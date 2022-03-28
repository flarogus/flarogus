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
			val warnings = Lists.warns.getOrDefault(message.author!!.id, null)?.fold(0) { a, v -> a + v.points }?.toString() ?: "no"
			description = "User ${message.author?.tag} has $warnings warning points"
		}
	}
	.description("Show warnings of the caller")
	
	register("lastusers") {
		replyWith(message, Multiverse.ratelimited.keys.map {
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
		
		val origin = Multiverse.history.find { reply!!.id in it } ?: throw CommandException("this message wasn't found in the history. perhaps, it was sent in the previous instance?")
		
		origin.let {
			if (message.data.author.id.value !in Vars.superusers && it.origin.asMessage().data.author.id != message.data.author.id) {
				throw IllegalAccessException("you are not allowed to delete others' messages")
			}
			
			it.retranslated.forEach { 
				try { it.delete() } catch (ignored: Exception) {}
			}
			
			Log.info { "${message.author?.tag} deleted a multiversal message ${it.origin.id}" }
			
			if (deleteOrigin) {
				try {
					it.origin.delete()
				} catch (e: Exception) {
					replyWith(it.origin, """
						This message was deleted from other multiversal channels but this (original) message could not be deleted.
						Check whether the bot has the necessary permissions.
					""".trimIndent())
				}
			}
		}
	}
	.header("deleteOriginal: Boolean?")
	.description("Reply to a multiversal message sent in this instance to delete it. You can set [deleteOriginal] to false to avoid deleting the original message.")
} }
