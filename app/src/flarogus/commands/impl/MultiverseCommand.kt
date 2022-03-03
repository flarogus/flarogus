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
import flarogus.multiverse.*

typealias CustomCommand = flarogus.commands.Command

val MultiverseCommand = CustomCommand(
	handler = {
		//create a fake event
		val newMessage = fakeMessage(message, it[0].lowercase())
		val event = MessageCreateEvent(newMessage, guildId, member, shard, supplier, coroutineScope)
		   
		MultiverseCommandHandler.handle(event)
	},
	
	header = "subcommand",
	
	description = "Execute a multiversal subcommand. Use flarogus multiverse help to see all available commands"
)

private val MultiverseCommandHandler = object : FlarogusCommandHandler(true, "") {
	init {
		val selfRef = this //idk how to reference this anonymous class from register()
		
		register("listguilds") {
			val msg = Multiverse.universes.map {
				try {
					message.supplier.getGuild(it.data.guildId.value ?: return@map null)
				} catch (ignored: Exception) {
					null
				}
			}.filter { it != null }.toSet().map { "${it?.id?.value} - ${it?.name}" }.joinToString(",\n")
			
			replyWith(message, "```\n" + msg + "\n```")
		}
		.condition(CustomCommand.adminOnly)
		.description("List all guilds multiverse works in")
		
		register("ban") {
			Lists.blacklist(Snowflake(it.getOrNull(1)?.toULong() ?: throw CommandException("ban", "no uid specified")))
		}
		.condition(CustomCommand.adminOnly)
		.header("id: Snowflake")
		.description("Ban a user or a guild")
	
		register("warn") {
			val user = Snowflake(it.getOrNull(1) ?: throw CommandException("warn", "you must specify the user id!"))
			val category = it.getOrNull(3)?.let { RuleCategory.valueOf(it.uppercase()) } ?: RuleCategory.GENERAL
			val rule = category[it.getOrNull(2)?.toInt() ?: throw CommandException("warn", "you must specify the rule number")]
			Lists.warn(user, rule)
			
			Multiverse.brodcastSystem { content = "user ${Vars.supplier.getUserOrNull(user)?.tag} was warned for rule '$rule'" }
		}
		.condition(CustomCommand.adminOnly)
		.header("id: Snowflake, rule: Int, rule category: [general]?")
		.description("Warn a user (or a guild if you're mad enough).")
		
		register("unwarn") {
			val warns = Lists.warns.getOrDefault(Snowflake(it.getOrNull(1) ?: throw CommandException("unwarn", "no uid specified")), null)
			warns?.clear()?.also { replyWith(message, "cleared succefully") } ?: replyWith(message, "this user has no warnings")
		}
		.condition(CustomCommand.adminOnly)
		.header("id: Snowflake")
		.description("unwarn a user")
		
		register("mywarnings") {
			message.channel.createEmbed {
				val warnings = Lists.warns.getOrDefault(message.author!!.id, null)?.fold(0) { a, v -> a + v.points }?.toString() ?: "no"
				description = "User ${message.author?.tag} has $warnings warning points"
			}
		}
		.description("list warnings of the caller")
		
		register("whitelist") {
			Lists.whitelist(Snowflake(it.getOrNull(1)?.toULong() ?: throw CommandException("ban", "no uid specified")))
		}
		.condition(CustomCommand.adminOnly)
		.header("id: Snowflake")
		.description("Whitelist a guild.")
		
		register("banlist") {
			replyWith(message, Lists.blacklist.joinToString(", "))
		}
		.condition(CustomCommand.adminOnly)
		.description("List banned users / guilds. Note that this shows IDs, not names.")
		
		register("lastusers") {
			replyWith(message, Multiverse.ratelimited.keys.map {
				try {
					return@map "[${Vars.supplier.getUser(it).tag}]: $it"
				} catch (e: Exception) {
					return@map "[error]: $it"
				}
			}.joinToString(",\n"))
		}
		.condition(CustomCommand.adminOnly)
		.description("List all users that have recently sent a message in multiverse")
		
		register("help") {
			message.channel.sendHelp(message.author!!, selfRef.commands)
		}
		.description("Show the list of commands")
	
	
		register("echo") {
			if (it[0].isEmpty()) throw CommandException("echo", "can't send an empty message")
			
			Multiverse.brodcastSystem {
				content = it[0].take(1999)
			}
		}
		.condition(CustomCommand.adminOnly)
		.header("text: String")
		.description("Send a system message in multiverse")
		
		register("reload") {
			Lists.blacklist.clear()
			Lists.whitelist.clear()
			Lists.usertags.clear()
			Multiverse.updateState()
		}
		.condition(CustomCommand.adminOnly)
		.description("Clear all lists and reload them from the respective channels. This command is to be called after unbanning removing an entry from white / black / other list.")
		
		register("rules") {
			message.channel.createMessage {
				messageReference = message.id
				RuleCategory.values().forEach {
					embed { description = it.toString() }
				}
			}
		}
		.description("Show the list of multiversal rules")
		
		register("setloglevel") {
			Log.level = when (it.getOrNull(1)?.lowercase()) {
				//todo: maybe valueOf would be better?
				"lifecycle" -> Log.LogLevel.LIFECYCLE
				"debug" -> Log.LogLevel.DEBUG
				"info" -> Log.LogLevel.INFO
				"error" -> Log.LogLevel.ERROR
				null -> throw CommandException("setLogLevel", "no log level specified")
				else -> throw CommandException("setLogLevel", "unknown log level")
			}
		}
		.condition(CustomCommand.adminOnly)
		.header("level: [lifecycle, debug, info, error]")
		.description("Set the log level")
		
		register("deletereply") {
			val reply = message.referencedMessage
			if (reply == null) throw CommandException("deleteReply", "you must reply to a multiversal message")
			
			val origin = Multiverse.history.find { reply.id in it } ?: throw CommandException("deleteReply", "this message wasn't found in the history. perhaps, it was sent in the previous instance?")
			origin.let {
				it.retranslated.forEach { 
					try { it.delete() } catch (ignored: Exception) {}
				}
				
				try {
					it.origin.delete()
				} catch (e: Exception) {
					replyWith(it.origin, """
						This message was deleted from other multiversal channel but this (original) message could not be deleted.
						Check whether the bot has the necessary permissions.
					""".trimIndent())
				}
			}
		}
		.condition(CustomCommand.adminOnly)
		.description("reply to a multiversal message to delete it from __all__ channels")
	}
}
