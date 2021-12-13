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
	
	client.on<MessageCreateEvent> {
		println("intercepted")
		val args = message.content.split(" ").filter { !it.isEmpty() }
		
		when (args[1]) {
			"sus" -> {
				val start = System.currentTimeMillis();
				println("sent reply")
				
				val reply = message.channel.createMessage { content = "sussificating..." }
				reply.edit { content = "sussificated in ${System.currentTimeMillis() - start}ms" }
				delay(50L)
				message.delete()
			}
			else -> message.channel.createMessage("no")
		}
	}
	
	println("initialized")
	client.login()
}