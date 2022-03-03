package flarogus.commands

import kotlinx.coroutines.*;
import dev.kord.core.event.message.*;
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import dev.kord.core.entity.*
import flarogus.*
import flarogus.util.*
import flarogus.commands.Command as FlarogusCommand
import flarogus.multiverse.*

/** Main command handler */
val CommandHandler = FlarogusCommandHandler(false, "flarogus")

/** Handles discord chat commands */
open class FlarogusCommandHandler(val ignoreBots: Boolean = true, val prefix: String) {
	val commands = HashMap<String, FlarogusCommand>(50)
	
	/** 
	 * Invokes the command handler associated with the first word (command name) in the message.
	 * Provides an array or arguments to the handler, 0th element is the source message without the command name 
	 *
	 * @return whether the message contained a command that was executed
	 **/
	open suspend fun handle(event: MessageCreateEvent): Boolean {
		if (ignoreBots && event.message.author?.isBot ?: true) return false
		if (!event.message.content.startsWith(prefix)) return false
		
		val message = event.message.content.substring(prefix.length)
		val args = message.split(" ").filter { !it.isEmpty() }.toMutableList()
		
		val commandName = args.getOrNull(0)
		if (commandName == null || commandName == "" || commandName.length >= message.length) return false
		args[0] = message.substring(commandName.length + 1)
		
		val command = commands.get(commandName)
		
		Vars.client.launch {
			if (command == null) {
				val err = event.message.channel.createMessage {
					content = "unknown command: ${commandName.take(500).stripEveryone()}\n(${event.message.author?.username?.stripEveryone()}, you're so sussy)"
					messageReference = event.message.id
				}
				delay(5000L)
				err.edit { content = "unknown command: ${commandName.take(500).stripEveryone()}" }
			} else {
				val author = event.message.author
				if (author != null && command.condition(author)) {
					val handler = command.handler;
					try {
						event.handler(args)
						
						Log.debug { "${event.message.author?.tag} has successfully executed `$commandName`" }
					} catch (e: Exception) { //no exceptions on my watch
						replyWith(event.message, e.toString())
						
						if (e is CommandException) {
							e.cause?.printStackTrace() //usually there's no cause, thus I can't do anything
							Log.debug { "a command exception has occurred while executing command `$commandName` ran by ${event.message.author?.tag}: `$e`" }
						} else {
							e.printStackTrace()
							Log.error { "a fatal exception has occurred while executing command `$commandName` ran by ${event.message.author?.tag}:: `$e`" }
						}
					}
				} else {
					replyWith(event.message, "You are not allowed to run '${commandName.stripEveryone()}'.")
					
					Log.info { "${event.message.author?.tag} has tried to execute a command they don't have access to: `${commandName}`" }
				}
			}
		}
		
		return true
	}
	
	open fun register(name: String, command: FlarogusCommand): FlarogusCommand {
		commands.put(name, command)
		return command
	}
	
	open fun register(name: String, handler: suspend MessageCreateEvent.(List<String>) -> Unit): FlarogusCommand {
		val command = FlarogusCommand(handler)
		commands.put(name, command)
		return command
	}
	
	open fun remove(name: String) {
		commands.remove(name)
	}
}
