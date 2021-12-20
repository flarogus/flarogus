package flarogus.commands

import kotlinx.coroutines.*;
import dev.kord.core.event.message.*;
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import flarogus.util.*
import flarogus.commands.Command as FlarogusCommand

object CommandHandler {
	
	val commands = HashMap<String, FlarogusCommand>(50)
	
	suspend fun handle(scope: CoroutineScope, message: String, event: MessageCreateEvent) = scope.launch {
		val args = message.split(" ").filter { !it.isEmpty() }
		
		val commandName = args.getOrNull(0)
		if (commandName == null || commandName == "") return@launch;
		
		val command = commands.get(commandName)
		if (command == null) {
			val err = event.message.channel.createMessage {
				content = "unknown command: $command\n(${event.message.author?.username}, you're so sussy)"
				messageReference = event.message.id
			}
			delay(5000L)
			err.edit { content = "unknown command: $commandName" }
		} else {
			val author = event.message.author
			if (author != null && command.condition(author)) {
				val handler = command.handler;
				try {
					event.handler(args)
				} catch (e: Throwable) { //no exceptions on my watch
					replyWith(event.message, e.toString())
				}
			} else {
				replyWith(event.message, "You are not allowed to run $commandName.")
			}
		}
	}
	
	fun register(name: String, command: FlarogusCommand): FlarogusCommand {
		commands.put(name, command)
		return command
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