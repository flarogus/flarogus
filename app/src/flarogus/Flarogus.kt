package flarogus

import kotlinx.coroutines.*;
import kotlinx.coroutines.flow.*;
import dev.kord.core.*
import dev.kord.core.event.*
import dev.kord.core.event.message.*
import dev.kord.common.entity.*
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import dev.kord.core.behavior.channel.threads.edit

suspend fun main(vararg args: String) {
	val token = args.getOrNull(0)
	if (token == null) {
		println("[ERROR] no token specified")
		return
	}
	val client = Kord(token)
	
	val prefix = "flarogus"
	
	client.events
		.filterIsInstance<MessageCreateEvent>()
		.filter { it.message.author?.isBot == false }
		.filter { it.message.content.startsWith(prefix) }
		.onEach { CommandHandler.handle(it.message.content.substring(prefix.length), it) }
		.launchIn(client)
	
	CommandHandler.register("sus") {
		val start = System.currentTimeMillis();
		
		launch {
			val reply = message.channel.createMessage {
				content = "sussificating..."
			}
			reply.edit { content = "sussificated in ${System.currentTimeMillis() - start}ms" }
			delay(50L)
			message.delete()
		}
	}
	
	println("initialized")
	client.login()
}