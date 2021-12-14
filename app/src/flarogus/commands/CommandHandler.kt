package flarogus.commands

import kotlinx.coroutines.*;
import dev.kord.core.event.message.*;
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import flarogus.replyWith
import flarogus.commands.Command as FlarogusCommand

object CommandHandler {
	
	val commands = HashMap<String, FlarogusCommand>(50)
	
	suspend fun handle(message: String, event: MessageCreateEvent) = coroutineScope {
		val args = message.split(" ").filter { !it.isEmpty() }
		
		val commandName = args.getOrNull(0)
		if (commandName == null || commandName == "") return@coroutineScope;
		
		val command = commands.get(commandName)
		if (command == null) {
			launch {
				val err = event.message.channel.createMessage {
					content = "unknown command: $command\n(${event.message.author?.username}, you're so sussy)"
					messageReference = event.message.id
				}
				delay(5000L)
				err.edit { content = "unknown command: $command" }
			}
		} else {
			val author = event.message.author
			if (command.condition != null || (author != null && command.condition(author))) {
				val handler = command.handler;
				event.handler(args)
			} else {
				replyWith(event.message, "You are not allowed to run $commandName.")
			}
		}
	}
	
	fun register(name: String, handler: suspend MessageCreateEvent.(List<String>) -> Unit): FlarogusCommand {
		val command = FlarogusCommand(handler)
		commands.put(name, command)
		return command
	}
	
	fun remove(name: String) {
		commands.remove(name)
	}
	
}