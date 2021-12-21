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

private val vowels = listOf('a', 'A', 'e', 'E', 'i', 'I', 'o', 'O', 'u', 'U', 'y', 'Y')
val flarsusBase = ImageIO.read({}::class.java.getResource("/flarsus.png") ?: throw RuntimeException("aaaaa le flar has escaped"))

val ownerId = 502871063223336990.toULong()
val prefix = "flarogus"

suspend fun main(vararg args: String) = runBlocking {
	val token = args.getOrNull(0)
	if (token == null) {
		println("[ERROR] no token specified")
		return@runBlocking
	}
	val client = Kord(token)
	val ubid = Random.nextInt(0, 1000000000).toString()
	val startedAt = System.currentTimeMillis()
	
	client.events
		.filterIsInstance<MessageCreateEvent>()
		.filter { it.message.author?.isBot == false }
		.filter { it.message.content.startsWith(prefix) }
		.onEach { CommandHandler.handle(this, it.message.content.substring(prefix.length), it) }
		.launchIn(client)
	
	CommandHandler.register("mines", flarogus.commands.impl.MinesweeperCommand);
	
	CommandHandler.register("userinfo", flarogus.commands.impl.UserinfoCommand);
	
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
				
				if (hidden > 0) {
					footer { text = "there's [$hidden] commands you are not allowed to run" }
				}
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
				userOrAuthor(it.getOrNull(1), this@register)?.getAvatarUrl()
			} else { //idk it thinks .jpg is not an image format
				message.attachments.find { (it.isImage || it.filename.endsWith(".jpg")) && it.width!! < 2000 && it.height!! < 2000 }?.url
			}
			if (image == null) throw CommandException("flaroficate", "failed to process: unable to retrieve image url. this can be caused by non-image files attached to the message.")
			
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
				throw CommandException("flaroficate", e.stackTraceToString())
			}
		}
	}
	.setHeader("userID: String? / attachment: Image")
	.setDescription("If the argument is a user id, return a flaroficated avatar of the user with the providen id. If there's no argument specified, uses the attached image or, if there's none, the avatar of the caller")
	
	CommandHandler.register("impostor") {
		val arg = it.getOrNull(1)
		val name = if (arg == null || arg.isEmpty() || arg[0].isDigit()) userOrAuthor(arg, this@register)?.username; else arg;
		if (name == null) throw CommandException("impostor", "the amogus has escaped, I couldn't do anything :pensive:")
		
		replyWith(message, buildString {
			var usAdded = false
			for (i in name.length - 1 downTo 0) {
				val char = name[i]
				if (!usAdded && char.isLetter() && char !in vowels) {
					insert(0, "us")
					insert(0, char)
					usAdded = true
				} else if (usAdded || char !in vowels) {
					insert(0, char)
				}
			}
		})
	}
	.setHeader("string/userID: String?")
	.setDescription("If the providen argument is a string, amogusificates it. If it's a user id, amogusifactes the name of the user with this id. Otherwise amogusificates the author's name.")
	
	CommandHandler.register("shutdown") {
		val target = it.getOrNull(1)
		if (target == null) throw CommandException("shutdown", "no unique bot id specified")
		
		if (target == ubid || target == "all") {
			File("done").printWriter().use { it.print(1) }
			client.shutdown()
			throw Error("shutting down...") //Error won't be caught, will crash the application and make the workflow stop
		}
	}
	.setCondition { it.id.value == ownerId }
	.setHeader("ubid: Int")
	.setDescription("shut down an instance by ubid. May not work from the first attempt.")
	
	launch {
		delay(1000 * 60 * 60 * 5L);
		client.shutdown();
	}
	
	println("initialized");
	client.login()
}

