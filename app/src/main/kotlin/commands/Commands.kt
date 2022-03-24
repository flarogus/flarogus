package flarogus.commands

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import javax.imageio.*;
import kotlin.random.*;
import kotlin.time.*
import kotlinx.coroutines.*;
import kotlinx.coroutines.flow.*;
import dev.kord.rest.builder.message.create.*
import dev.kord.core.event.*
import dev.kord.core.event.message.*
import dev.kord.core.supplier.*;
import dev.kord.core.entity.*;
import dev.kord.core.entity.channel.*
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import dev.kord.common.entity.*
import flarogus.*
import flarogus.util.*;
import flarogus.commands.*;
import flarogus.commands.Command as CustomCommand
import flarogus.commands.impl.*;
import flarogus.multiverse.*

fun initCommands() {
	CommandHandler.register("mines", MinesweeperCommand);
	
	CommandHandler.register("userinfo", UserinfoCommand);
	
	CommandHandler.register("run", RunCommand)
	
	CommandHandler.register("multiverse", MultiverseCommand)
	
	CommandHandler.register("help") {
		message.channel.sendHelp(message.author!!, CommandHandler.commands)
	}
	.description("Show the help message")
	
	@OptIn(ExperimentalTime::class)
	CommandHandler.register("sus") {
		sendEdited(message, "sussificating", 50L) {
			val ping = message.id.timeMark.elapsedNow().toLong(DurationUnit.MILLISECONDS)
			"""
				${Vars.ubid} — running for ${formatTime(System.currentTimeMillis() - Vars.startedAt)}. sussification time: ${ping}ms.
				Time since flarogus epoch: ${formatTime(System.currentTimeMillis() - Vars.flarogusEpoch)}
			""".trimIndent()
		}
		
		try {
			message.delete()
		} catch (ignored: Exception) {} //lack of "manage messages" permission
	}
	.description("Show the bot status")
	
	val flarsusBase = ImageIO.read({}::class.java.getResource("/flarsus.png") ?: throw RuntimeException("aaaaa le flar has escaped"))
	CommandHandler.register("flaroficate") {
		launch {
			val image = if (message.attachments.size == 0) {
				userOrAuthor(it.getOrNull(1), this@register)?.getAvatarUrl()
			} else {
				message.attachments.find { it.isImage && it.width!! < 2000 && it.height!! < 2000 }?.url
			}
			if (image == null) throw CommandException("flaroficate", "failed to process: unable to retrieve image url. this can be caused by non-image files attached to the message.")
			
			try {
				val origin = ImageIO.read(URL(image))
				val sussyImage = ImageUtil.multiply(origin, flarsusBase)
				
				sendImage(message, image = sussyImage)
			} catch (e: Exception) {
				throw CommandException("flaroficate", e.stackTraceToString())
			}
		}
	}
	.header("user: User? / attachment: Image")
	.description("Flaroficate the providen image, avatar of the providen user or, if neither are present, avatar of the caller")
	
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
	.header("user: User? / single_word: String?")
	.description("Amogusificate the providen word, name of the providen user or, if neither are present, name of the caller.")
	
	CommandHandler.register("shutdown") {
		val target = it.getOrNull(1)
		if (target == null) throw CommandException("shutdown", "no unique bot id specified")
		
		if (target == Vars.ubid || target == "all") {
			Multiverse.brodcastSystem { 
				embed { description = "A Multiverse instance is shutting down..." }
			}
			
			delay(5000L)
			Vars.client.shutdown()
			//Vars.saveState()
			throw Error("shutting down...") //Error won't be caught, will crash the application and make the workflow stop
		}
	}
	.condition(CustomCommand.adminOnly)
	.header("ubid: Int")
	.description("shut down an instance by ubid.")

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
	.header("bash script: String")
	.condition(CustomCommand.ownerOnly)
	
	CommandHandler.register("merge") {
		val first = userOrNull(it.getOrNull(1), this)
		val second = userOrAuthor(it.getOrNull(2), this)
			
		if (first == null || second == null) throw CommandException("merge", "you must specify at least one valid user! (null-equals: first - ${first == null}, second - ${second == null})")
		if (first == second) throw CommandException("merge", "you must specify different users!")
		
		try {
			val image1 = ImageIO.read(URL(first.getAvatarUrl()))
			val image2 = ImageIO.read(URL(second.getAvatarUrl()))
			
			val result = ImageUtil.merge(image1, image2)
			
			val name1 = first.username
			val name2 = second.username
			
			sendImage(message, name1.substring(0, name1.length / 2) + name2.substring(name2.length / 2), result)
		} catch (e: IOException) {
			throw CommandException("merge", e.stackTraceToString())
		}
	}
	.header("first: User, second: User?")
	.description("Merge pfps of two users. If only one user is specified, uses the caller as the second.")

	val reportsChannel = Snowflake(944718226649124874UL)
	
	CommandHandler.register("report") {
		if (it[0].isEmpty()) throw CommandException("report", "you must specify a message")
		
		try {
			Vars.client.unsafe.messageChannel(reportsChannel).createMessage {
				content = """
				${message.author?.tag} (channel ${message.channelId}, guild ${message.data.guildId.value}) reports:
				```
				${it[0].stripEveryone().take(1800)}
				```
				""".trimIndent()
			}
			
			replyWith(message, "Sent succefully")
		} catch (e: Exception) {
			throw CommandException("report", "Could not send a report: $e")
		}
	}
	.header("message: String")
	.description("Send a message that will be visible to admins")
	
	CommandHandler.register("server") {
		try {
			message.author?.getDmChannel()?.createMessage("invite to the core guild: https://discord.gg/kgGaUPx2D2")
		} catch (e: Exception) {
			replyWith(message, "couldn't send a DM. make sure you have DMs open ($e)")
		}
	}
	.description("Get an invite to the official server")
}

/** Sends a help message in the specified channel, lists all commands available to the user */
suspend fun MessageChannelBehavior.sendHelp(user: User, origin: Map<out Any, flarogus.commands.Command>) {
	createEmbed {
		title = "List of commands"
		
		var hidden = 0
		for ((commandName, command) in origin) {
			if (!command.condition(user)) {
				hidden++
				continue;
			}
			field {
				name = commandName.toString()
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
