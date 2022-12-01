package flarogus.command

import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.response.*
import dev.kord.core.event.interaction.*
import dev.kord.core.entity.interaction.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

typealias InteractionMessage = Pair<GlobalChatInputCommandInteraction, DeferredMessageInteractionResponseBehavior>

/**
 * Maps flarogus commands to slash commands.
 *
 * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
 * TODO: this class is unfinished and doesn't work yet.
 */
 @OptIn(ObsoleteCoroutinesApi::class)
open class SlashCommandMapper(val kord: Kord) {
	val commandNameSeparator = "/"

	protected var job: Job? = null

	/** Executes commands the received events contain. */
	val executor = kord.actor<InteractionMessage>(capacity = Channel.UNLIMITED) {
		for ((interaction, response) in channel) {
			val commandName = interaction.invokedCommandName
				.split(commandNameSeparator)
				.joinToString(" ")
				.let { "!$it" }

			// firstly, we need to ensure the command exists and is a normal command.
			val command = FlarogusCommand.find(commandName) 
			if (command == null) {
				response.respond { content = "Error: unresolved command: $commandName" }
				continue
			} else if (command is HelpCommand || command is TreeCommand) {
				response.respond { content = "Error: this type of command (${command::class}) cannot be invoked as a slash-command." }
				continue
			}

			// transform the argument map to a list of positional arguments
			val slashCommand = interaction.command
			TODO()

			response.respond {
				val user = interaction.user.tag
				val commandText = TODO()
				content = TODO()
			}
		}
	}

	/** Launches this mapper. */
	open fun launch() {
		job = kord.events
			.filterIsInstance<GlobalChatInputCommandInteractionCreateEvent>()
			.onEach {
				return@onEach

				val interaction = it.interaction
				val response = interaction.deferPublicResponse()
				
				executor.send(interaction to response)
			}
			.launchIn(kord)
	}

	/** Stops the mapper. */
	open fun shutdown() {
		job?.cancel()
		job = null
	}
}
