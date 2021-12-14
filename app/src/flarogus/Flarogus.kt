package flarogus

import java.io.*;
import java.net.*;
import javax.imageio.*;
import kotlinx.coroutines.*;
import kotlinx.coroutines.flow.*;
import dev.kord.core.*
import dev.kord.core.event.*
import dev.kord.core.event.message.*
import dev.kord.core.supplier.*;
import dev.kord.core.entity.*;
import dev.kord.common.entity.*
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import flarogus.util.*;
import flarogus.commands.*;

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
	
	CommandHandler.register("help") {
		launch {
			message.channel.createEmbed {
				title = "Flarogus help"
				for ((commandName, command) in CommandHandler.commands) {
					field {
						name = commandName
						value = command.description ?: "no description"
						inline = true
						
						val author = message.author
						if (author == null || !command.condition(author)) {
							value += " ***(you are not allowed to execute this command)***"
						}
					}
				}
			}
		}
	}.setDescription("Show the help message")
	
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
	}.setDescription("Print the current connection latency")
	
	CommandHandler.register("flaroficate") {
		val userid = it.getOrNull(1)
		launch {
			val pfp = userOrAuthor(userid, this@register)?.avatar?.url
			if (pfp == null) {
				replyWith(message, "failed to process: null avatar url")
				return@launch;
			}
			
			try {
				//le pfp can be a .webp, which is RGB-encoded
				val avatar = ImageIO.read(URL(pfp))
				val sussyImage = ImageUtil.multiply(avatar, flarsusBase)
				
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
	}.setDescription("Return a flaroficated avatar of the user with the providen user id. If there's no uid specified, uses the avatar of the caller")
	
	CommandHandler.register("impostor") {
		val name = userOrAuthor(it.getOrNull(1), this@register)?.username
		if (name == null) {
			replyWith(message, "you have no name :pensive:")
			return@register
		}
		val consonant = name.lastConsonantIndex()
		if (consonant == -1) {
			replyWith(message, "${name}gus")
		} else {
			replyWith(message, "${name.substring(0, consonant + 1)}us")
		}
	}.setDescription("Returns an amogusificated name of the user with the providen id. If there's no id providen, amogusificates the name of the caller")
	
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

suspend fun userOrAuthor(uid: String?, event: MessageCreateEvent): User? {
	if (uid == null || uid.isEmpty()) {
		return event.message.author
	}
	try {
		return event.supplier.getUser(Snowflake(uid.toULong()))
	} catch (e: Exception) {
		return event.message.author
	}
}

private val vowels = listOf('a', 'e', 'i', 'o', 'u', 'y')
fun String.lastConsonantIndex(): Int {
	for (i in length - 1 downTo 0) {
		if (!vowels.contains(get(i))) return i
	}
	return -1
}