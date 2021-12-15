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
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import dev.kord.common.entity.*
import flarogus.util.*;
import flarogus.commands.*;

suspend fun main(vararg args: String) = runBlocking {
	val token = args.getOrNull(0)
	if (token == null) {
		println("[ERROR] no token specified")
		return@runBlocking
	}
	val client = Kord(token)
	val prefix = "flarogus"
	
	val flarsusBase = ImageIO.read({}::class.java.getResource("/flarsus.png") ?: throw RuntimeException("aaaaa le flar has escaped"))
	
	client.events
		.filterIsInstance<MessageCreateEvent>()
		.filter { it.message.author?.isBot == false }
		.filter { it.message.content.startsWith(prefix) }
		.onEach { CommandHandler.handle(this, it.message.content.substring(prefix.length), it) }
		.launchIn(client)
	
	CommandHandler.register("help") {
		launch {
			message.channel.createEmbed {
				title = "Flarogus help"
				for ((commandName, command) in CommandHandler.commands) {
					field {
						name = commandName
						value = command.description ?: "no description"
						`inline` = true
						
						if (command.header != null) name += " [" + command.header + "]"
						
						val author = message.author
						if (author == null || !command.condition(author)) {
							value += " ***(you are not allowed to execute this command)***"
						}
					}
				}
			}
		}
	}
	.setDescription("Show the help message")
	
	CommandHandler.register("sus") {
		val start = System.currentTimeMillis();
		sendEdited(message, "sussificating", 50L) { "sussificated in ${System.currentTimeMillis() - start}ms" }
		message.delete()
	}
	.setDescription("Print the current connection latency")
	
	CommandHandler.register("flaroficate") {
		launch {
			val userid = it.getOrNull(1)
			val image = userOrAuthor(userid, this@register)?.avatar?.url
			if (image == null) {
				replyWith(message, "failed to process: null image url")
				return@launch;
			}
			
			try {
				//le pfp can be a .webp, which is RGB-encoded
				val origin = ImageIO.read(URL(image))
				val sussyImage = ImageUtil.multiply(origin, flarsusBase)
				
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
	.setHeader("userID: String? (or attached file)")
	.setDescription("Return a flaroficated avatar of the user with the providen user id. If there's no uid specified, uses the avatar of the caller")
	
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
	}
	.setHeader("userID: String?")
	.setDescription("Returns an amogusificated name of the user with the providen id. If there's no id providen, amogusificates the name of the caller")
	
	CommandHandler.register("mines", flarogus.commands.impl.MinesweeperCommand);
	
	println("initialized")
	client.login()
}

private val vowels = listOf('a', 'e', 'i', 'o', 'u', 'y')
fun String.lastConsonantIndex(): Int {
	for (i in length - 1 downTo 0) {
		if (!vowels.contains(get(i))) return i
	}
	return -1
}