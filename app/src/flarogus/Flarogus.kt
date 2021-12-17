package flarogus

import java.io.*;
import java.net.*;
import javax.imageio.*;
import kotlin.random.*;
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

val ownerId = 502871063223336990.toULong()
val prefix = "flarogus"
//these are late-initialized
var ubid = ""
var startedAt = -1L

suspend fun main(vararg args: String) = runBlocking {
	//check if the precious instance has been shut down
	try {
		if (File("done").bufferedReader().use { it.readText().toInt() } == 1) {
			System.exit(0)
		}
	} catch (e: Exception) {} //no such file, so the previous instance either ended with an error (it's ok) or didn't exist
	
	//try to load saved data, set everything up if there's none
	try {
		File("data").bufferedReader().use {
			val lines = it.readText().split("\n")
			ubid = lines[0]
			startedAt = lines[1].toLong()
		}
	} catch (e: Exception) {
		ubid = Random.nextInt(0, 1000000000).toString()
		startedAt = System.currentTimeMillis()
		File("data").printWriter().use { it.println(ubid); it.println(startedAt) }
	}
	
	val token = args.getOrNull(0)
	if (token == null) {
		println("[ERROR] no token specified")
		return@runBlocking
	}
	val client = Kord(token)
	val flarsusBase = ImageIO.read({}::class.java.getResource("/flarsus.png") ?: throw RuntimeException("aaaaa le flar has escaped"))
	
	client.events
		.filterIsInstance<MessageCreateEvent>()
		.filter { it.message.author?.isBot == false }
		.filter { it.message.content.startsWith(prefix) }
		.onEach { CommandHandler.handle(this, it.message.content.substring(prefix.length), it) }
		.launchIn(client)
	
	CommandHandler.register("mines", flarogus.commands.impl.MinesweeperCommand);
	
	CommandHandler.register("help") {
		launch {
			message.channel.createEmbed {
				title = "Flarogus help"
				
				var hidden = 0
				val author = message.author
				for ((commandName, command) in CommandHandler.commands) {
					if (author == null || !command.condition(author)) {
						hidden++
						continue;
					}
					field {
						name = commandName
						value = command.description ?: "no description"
						`inline` = true
						
						if (command.header != null) name += " [" + command.header + "]"
					}
				}
				
				footer { text = "there's [$hidden] commands you are not allowed to run" }
			}
		}
	}
	.setDescription("Show the help message")
	
	CommandHandler.register("sus") {
		val start = System.currentTimeMillis();
		sendEdited(message, "sussificating", 50L) { 
			"$ubid — running for ${formatTime(System.currentTimeMillis() - startedAt)}. sussification time: ${System.currentTimeMillis() - start}ms."
		}
		message.delete()
	}
	.setDescription("Print the bot status")
	
	CommandHandler.register("flaroficate") {
		launch {
			val image = if (message.attachments.size == 0) {
				userOrAuthor(it.getOrNull(1), this@register)?.avatar?.url
			} else { //idk it thinks .jpg is not an image format
				message.attachments.find { (it.isImage || it.filename.endsWith(".jpg")) && it.width!! < 2000 && it.height!! < 2000 }?.url
			}
			if (image == null) {
				replyWith(message, "failed to process: unable to retrieve image url. this can be caused by non-image files attached to the message.")
				return@launch;
			}
			
			try {
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
	.setHeader("userID: String? / attachment: Image")
	.setDescription("If the argument is a user id, return a flaroficated avatar of the user with the providen id. If there's no argument specified, uses the attached image or, if there's none, the avatar of the caller")
	
	CommandHandler.register("impostor") {
		val arg = it.getOrNull(1)
		val name = if (arg == null || arg.isEmpty() || arg[0].isDigit()) userOrAuthor(arg, this@register)?.username; else arg;
		if (name == null) {
			replyWith(message, "the amogus has escaped, I couldn't do anything :pensive:")
			return@register
		}
		val consonant = name.lastConsonantIndex()
		if (consonant == -1) {
			replyWith(message, "${name}gus")
		} else {
			replyWith(message, "${name.substring(0, consonant + 1)}us")
		}
	}
	.setHeader("string/userID: String?")
	.setDescription("If the providen argument is a string, amogusificates it. If it's a user id, amogusifactes the name of the user with this id. Otherwise amogusificates the author's name.")
	
	CommandHandler.register("shutdown") {
		val target = it.getOrNull(1)
		if (target == null) {
			replyWith(message, "no unique bot id specified")
			return@register
		}
		if (target == ubid || target == "all") {
			File("done").printWriter().use { it.print(1) }
			client.shutdown()
		}
	}
	.setCondition { it.id.value == ownerId }
	.setHeader("ubid: Int")
	.setDescription("shut down an instance by ubid.")
	
	println("initialized")
	launch {
		delay(1000 * 60 * 60 * 5L) //shut down after 5 hours
		client.shutdown()
	}
	client.login()
}

private val vowels = listOf('a', 'e', 'i', 'o', 'u', 'y')
fun String.lastConsonantIndex(): Int {
	for (i in length - 1 downTo 0) {
		if (!vowels.contains(get(i))) return i
	}
	return -1
}

fun formatTime(millis: Long): String {
	val time: Long = millis / 1000L;
	return "${(time % 86400) / 3600} hours, ${(time % 3600) / 60} minutes, ${time % 60} seconds";
}