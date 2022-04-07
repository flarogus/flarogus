package flarogus.commands.impl

import kotlinx.coroutines.*;
import kotlinx.coroutines.flow.*;
import dev.kord.rest.builder.message.create.*
import dev.kord.core.event.*
import dev.kord.core.event.message.*
import dev.kord.core.supplier.*;
import dev.kord.core.entity.*;
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import dev.kord.common.entity.*
import flarogus.*
import flarogus.util.*;
import flarogus.commands.*
import flarogus.commands.impl.multiverse.*
import flarogus.multiverse.*
import flarogus.multiverse.state.*

val MultiverseCommand = Supercommand(
	name = "multiverse",

	description = "Execute a multiversal subcommand",
).also { it.commands.apply {
	register(AdminCommand)
	
	register("mywarnings") {
		message.channel.createEmbed {
			val warnings = Lists.warns.getOrDefault(message.author!!.id, null)
			val points = warnings?.fold(0) { a, v -> a + v.points }?.toString() ?: "no"

			description = "User ${message.author?.tag} has $warnings warning points"
			if (warnings != null && warnings.size > 0) description += buildString {
				appendLine()
				warnings.forEach {
					append(it.category).append('.').append(it.index).append(' ')
				}
			}
		}
	}
	.description("Show warnings of the caller")
	
	register("lastusers") {
		message.replyWith(Multiverse.ratelimited.keys.map {
			try {
				return@map "[${Vars.supplier.getUser(it).tag}]: $it"
			} catch (e: Exception) {
				return@map "[error]: $it"
			}
		}.joinToString(",\n"))
	}
	.description("List all users that have recently sent a message in multiverse")
	
	register("rules") {
		message.channel.createMessage {
			messageReference = message.id
			RuleCategory.values().forEach {
				embed { description = it.toString() }
			}
		}
	}
	.description("Show the list of multiversal rules")
	
	register("deletereply") {
		val reply = message.referencedMessage
		expect(reply != null) { "you must reply to a multiversal message" }
		
		val deleteOrigin = it.getOrNull(1)?.toBoolean() ?: true
		
		val origin = Multiverse.history.find { reply.id in it } ?: throw CommandException("this message wasn't found in the history. perhaps, it was sent too long time ago?")
		
		origin.let {
			if (message.data.author.id.value !in Vars.superusers && it.origin?.asMessage()?.data?.author?.id != message.data.author.id) {
				throw IllegalAccessException("you are not allowed to delete others' messages")
			}
			
			var deleted = 0
			it.retranslated.forEach { 
				try { 
					it.delete();
					deleted++
				} catch (ignored: Exception) {}
			}
			
			Log.info { "${message.author?.tag} deleted a multiversal message with id ${it.origin?.id}" }
			Multiverse.history.remove(it)

			if (deleteOrigin) {
				try {
					it.origin?.delete()?.also { deleted++ }
				} catch (e: Exception) {
					it.origin?.replyWith("""
						This message was deleted from other multiversal channels but this (original) message could not be deleted.
						Check whether the bot has the necessary permissions.
					""".trimIndent())
				}
			}

			message.replyWith("successfully deleted $deleted messages.")
		}
	}
	.header("deleteOriginal: Boolean?")
	.description("Reply to a multiversal message sent recently to delete it. You can set [deleteOriginal] to false to avoid deleting the original message.")

	register("replyInfo") {
		val reply = message.referencedMessage
		expect(reply != null) { "you must reply to a multiversal message" }

		val msg = Multiverse.history.find { reply in it }
		expect(msg != null) { "this message wasn't found in the history. perhaps, it was sent too long time ago?" }
		val originMsg = msg.origin?.asMessage() ?: throw CommandException("this message doesn't have an origin.")
		val author = User(originMsg.data.author, Vars.client)

		message.reply {
			content = """
				Multiversal message #${msg.origin.id}
				Author: ${author.tag}, uid: ${author.id}
				Channel id: ${originMsg.channelId}
				Guild id: ${originMsg.data.guildId.value}
			""".trimIndent()
		}
	}
	.description("Reply to a multiversal message sent recently to get info about it.")
} }
