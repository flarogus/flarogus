package flarogus.commands

import kotlinx.coroutines.*;
import dev.kord.core.event.message.*;
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import flarogus.util.*
import flarogus.commands.Command as FlarogusCommand

object CommandHandler {
	
	val commands = HashMap<String, FlarogusCommand>(50)
	
	/** Invokes the command handler associated with the first word (command name) in the message.
	 *  Provides an array or arguments to the handler, 0th element is the source message without the command name */
	suspend fun handle(scope: CoroutineScope, message: String, event: MessageCreateEvent) = scope.launch {
		val args = message.split(" ").filter { !it.isEmpty() }.toMutableList()
		
		val commandName = args.getOrNull(0)
		if (commandName == null || commandName == "" || commandName.length >= message.length) return@launch;
		args[0] = message.substring(commandName.length + 1)
		
		println("[INFO] ${event.message.author?.username}: ${event.message.content}")
		
		val command = commands.get(commandName)
		if (command == null) {
			val err = event.message.channel.createMessage {
				content = "unknown command: ${commandName.take(500)}\n(${event.message.author?.username}, you're so sussy)"
				messageReference = event.message.id
			}
			delay(5000L)
			err.edit { content = "unknown command: ${commandName.take(500)}" }
		} else {
			val author = event.message.author
			if (author != null && command.condition(author)) {
				val handler = command.handler;
				try {
					event.handler(args)
				} catch (e: Exception) { //no exceptions on my watch
					replyWith(event.message, e.toString())
					
					if (e is CommandException) {
						e.cause?.printStackTrace() //usually there's no cause, thus I can't do anything
					} else {
						e.printStackTrace()
					}
				}
			} else {
				replyWith(event.message, "You are not allowed to run '$commandName'.")
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