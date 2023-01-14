package flarogus.command

import dev.kord.core.Kord
import dev.kord.core.event.message.MessageCreateEvent
import flarogus.Vars
import flarogus.multiverse.Multiverse
import flarogus.multiverse.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

/**
 * Handles bot commands.
 */
 @OptIn(ObsoleteCoroutinesApi::class)
open class CommandHandler(
	val kord: Kord,
	val root: FlarogusCommand<Any?>
) {
	protected var job: Job? = null
	
	/** Executes commands the received events contain. */
	val commandExecutor = kord.actor<MessageCreateEvent>(capacity = Channel.UNLIMITED) {
		for (event in channel) {
			val cropped = event.message.content.removePrefix(root.name).trim()
			
			launch {
				try {
					root(event.message, cropped)
					Log.debug { "User ${event.message.data.author.username} has executed a command: '$cropped'" }
				} catch (e: Exception) {
					Log.error { "an uncaught exception has occurred while processing a message sent by ${event.message.author?.tag}: $e" }
				}
			}
		}
	}
	/** Passes received events to the multiverse. */
	val multiverseExecutor = kord.actor<MessageCreateEvent>(capacity = 10) {
		for (message in channel) runCatching {
			Vars.multiverse.onMessageReceived(message)
		}
	}

	/**
	 * Launches this handler.
	 * Throws an exception if it has been launched before and is still running.
	 */
	open fun launch() {
		if (job?.isActive == true) error("this command handler is already active.")

		job = kord.events
			.filterIsInstance<MessageCreateEvent>()
			.filter { it.message.data.author.id != Vars.botId && it.message.data.webhookId.value == null }
			.onEach {
				val isCommand = it.message.content.startsWith(Vars.rootCommand.name)
				if (isCommand) {
					commandExecutor.send(it)
				} else {
					multiverseExecutor.send(it)
				}
			}.launchIn(kord)
	}

	/**
	 * Stops this command handler.
	 */
	open fun shutdown() {
		job?.cancel()
		job = null
	}
}
