package flarogus

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import javax.imageio.*;
import kotlin.random.*;
import kotlin.time.*
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
	loadState()
	Vars.client = Kord(token)
	Vars.ubid = Random.nextInt(0, 1000000000).toString()
	
	Vars.client.events
		.filterIsInstance<MessageCreateEvent>()
		.filter { it.message.author?.isBot == false }
		.filter { it.message.content.startsWith(Vars.prefix) }
		.onEach { CommandHandler.handle(this, it.message.content.substring(Vars.prefix.length), it) }
		.launchIn(Vars.client)
	
	CommandHandler.register("mines", flarogus.commands.impl.MinesweeperCommand);
	
	CommandHandler.register("userinfo", flarogus.commands.impl.UserinfoCommand);
	
	CommandHandler.register("run", flarogus.commands.impl.RunCommand)
	
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
	
	@OptIn(kotlin.time.ExperimentalTime::class)
	CommandHandler.register("sus") {
		sendEdited(message, "sussificating", 50L) {
			val ping = message.id.timeMark.elapsedNow().toLong(DurationUnit.MILLISECONDS)
			"${Vars.ubid} — running for ${formatTime(System.currentTimeMillis() - Vars.startedAt)}. sussification time: ${ping}ms."
		}
		message.delete()
	}
	.setDescription("Show the bot status")
	
	val flarsusBase = ImageIO.read({}::class.java.getResource("/flarsus.png") ?: throw RuntimeException("aaaaa le flar has escaped"))
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
	.setHeader("user: User? / attachment: Image")
	.setDescription("Flaroficate the providen image, avatar of the providen user or, if neither are present, avatar of the caller")
	
	val vowels = listOf('a', 'A', 'e', 'E', 'i', 'I', 'o', 'O', 'u', 'U', 'y', 'Y')
	CommandHandler.register("impostor") {
		val arg = it.getOrNull(1)
		val name = if (arg == null || arg.isEmpty() || arg[0].isDigit() || arg.startsWith("<@")) { //todo: find a better way?
			userOrAuthor(arg, this@register)?.username;
		} else {
			arg;
		}
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
	.setHeader("user: User? / single_word: String?")
	.setDescription("Amogusificate the providen word, name of the providen user or, if neither are present, name of the caller.")
	
	CommandHandler.register("shutdown") {
		val target = it.getOrNull(1)
		if (target == null) throw CommandException("shutdown", "no unique bot id specified")
		
		if (target == Vars.ubid || target == "all") {
			File("done").printWriter().use { it.print(1) }
			Vars.client.shutdown()
			saveState()
			throw Error("shutting down...") //Error won't be caught, will crash the application and make the workflow stop
		}
	}
	.setCondition { it.id.value == Vars.ownerId }
	.setHeader("ubid: Int")
	.setDescription("shut down an instance by ubid.")

	flarogus.commands.CommandHandler.register("command") {
		if (it.getOrNull(1) == null) return@register
		
		var proc: Process? = null
		
		val thread = Vars.threadPool.submit {
			try {
				File("/tmp/command").writeText(it.get(0))
				
				ProcessBuilder("sudo", "chmod", "+x", "/tmp/command").start().waitFor(1000, TimeUnit.MILLISECONDS)
				proc = ProcessBuilder("bash", "/tmp/command")
					.directory(File("/usr/bin"))
					.redirectOutput(ProcessBuilder.Redirect.PIPE)
					.redirectError(ProcessBuilder.Redirect.PIPE)
					.start()
				proc!!.waitFor(10000, TimeUnit.MILLISECONDS)
				proc!!.errorStream.bufferedReader().use {
					val error = it.readText()
					proc!!.inputStream.bufferedReader().use {
						replyWith(message, "output${if (error != "") " and errors" else ""}:\n```\n$error\n\n${it.readText()} \n```")
					}
				}
			} catch(e: IOException) {
				ktsinterface.launch { replyWith(message, e.toString()) }
			}
		}
		delay(60 * 1000L) //60 seconds must be enough
		thread.cancel(true)
		if (proc != null) proc!!.destroy()
	}
	.setHeader("bash script: String")
	.setCondition { it.id.value == flarogus.Vars.ownerId }
	
	launch {
		delay(1000 * 60 * 60 * 5L);
		Vars.client.shutdown();
		saveState()
	}
	
	println("initialized");
	Vars.client.login()
}

fun loadState() {
	try {
		val file = File("save.bin")
		
		if (file.exists()) {
			DataInputStream(file.inputStream()).use {
				Vars.startedAt = it.readLong()
				return
			}
		}
	} catch (e: IOException) {
		e.printStackTrace()
	}
	//fallback — should only occur if the file doesn't exist/cannot be read
	Vars.startedAt = System.currentTimeMillis()
}

fun saveState() {
	try {
		DataOutputStream(File("save.bin").outputStream()).use {
			it.writeLong(Vars.startedAt)
		}
	} catch (e: IOException) {
		e.printStackTrace()
	}
}