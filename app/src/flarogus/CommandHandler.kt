package flarogus

import kotlinx.coroutines.*;
import dev.kord.core.event.message.*;
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*

object CommandHandler {
	
	private val commands = HashMap<String, MessageCreateEvent.(List<String>) -> Unit>(50)
	
	suspend fun handle(message: String, event: MessageCreateEvent) = coroutineScope {
		val args = message.split(" ").filter { !it.isEmpty() }
		
		val command = args.getOrNull(0)
		if (command == null) return@coroutineScope;
		
		val handler = commands.get(command)
		launch {
			if (handler == null) {
				val err = event.message.channel.createMessage {
					content = "unknown command: $command\n(${event.message.author?.username}, you're so sussy)"
					messageReference = event.message.id
				}
				delay(5000L)
				err.edit {
					content = "unknown command: $command"
				}
			} else {
				event.handler(args)
			}
		}
	}
	
	fun register(command: String, handler: MessageCreateEvent.(List<String>) -> Unit) {
		commands.put(command, handler)
	}
	
	fun remove(command: String) {
		commands.remove(command)
	}
	
}