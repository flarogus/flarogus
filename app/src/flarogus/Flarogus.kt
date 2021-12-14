package flarogus

import java.io.*;
import java.net.*;
import javax.imageio.*;
import kotlinx.coroutines.*;
import kotlinx.coroutines.flow.*;
import dev.kord.core.*
import dev.kord.core.event.*
import dev.kord.core.event.message.*
import dev.kord.core.entity.*;
import dev.kord.common.entity.*
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import flarogus.*;
import flarogus.util.*;

suspend fun main(vararg args: String) {
	val token = args.getOrNull(0)
	if (token == null) {
		println("[ERROR] no token specified")
		return
	}
	val client = Kord(token)
	val prefix = "flarogus"
	
	val flarsusBase = ImageIO.read({}::class.java.getResource("/flarsus.png") ?: throw RuntimeException("aaaaa le flar has escaped"))
	
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
	
	CommandHandler.register("flaroficate") {
		val userid = it.getOrNull(1)
		var pfp: String = ""
		if (userid == null) {
			pfp = message.author?.avatar?.url ?: "";
		} else {
			replyWith(message, "custom user selection is not yet implemented")
			return@register;
		}
		launch {
			try {
				val image = ImageIO.read(URL(pfp))
				val sussyImage = ImageUtil.multiply(image, flarsusBase)
				
				ByteArrayOutputStream().use {
					ImageIO.write(sussyImage, "png", it);
					ByteArrayInputStream(it.toByteArray()).use {
						message.channel.createMessage {
							content = "sus"
							messageReference = message.id
							addFile("SUSSUSUSUSUSUSUSUSU.png", it)
						}
					}
				}
			} catch (e: Exception) {
				replyWith(message, "Exception has occurred: ${e.stackTraceToString()}")
			}
		}
	}
	
	println("initialized")
	client.login()
}

fun CoroutineScope.replyWith(origin: Message, message: String, selfdestructIn: Long? = null) = launch {
	val msg = origin.channel.createMessage {
		content = message
		messageReference = origin.id
	}
	if (selfdestructIn != null) {
		delay(selfdestructIn)
		msg.delete()
	}
}