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
		MultiverseCommandHandler.handle(Vars.client, it[0], this)
		/*
		val commandName = it.getOrNull(1)?.lowercase()
		
		if (commandName == null) {
			throw CommandException("multiverse", "you must specify a multiversal command")
		}
		
		val command = subcommands.getOrDefault(commandName, null)
		
		if (command == null) {
			throw CommandException("multiverse", "unknown subcommand: $commandName")
		}
		
		if (command.condition(message.author!!)) {
			val handler = command.handler
			//turn ["subcommand arg1 arg2", "subcommand", "arg1", "arg2"] to ["arg1 arg2", "arg1", "arg2"]
			val args = it.toMutableList()
			args.removeAt(0)
			args[0] = it[0].substring(commandName.length + 1)
			
			this.handler(args)
			
			Log.info { "${message.author?.tag} has successfully executed a multiversal subcommand: `${it[0]}`" }
		} else {
			throw CommandException("multiverse", "you're not allowed to execute command $commandName!")
		}
		*/
	},
	
	header = "subcommand",
	
	description = "Execute a multiversal subcommand. Use flarogus multiverse help to see all available commands"
)

private val MultiverseCommandHandler = object : FlarogusCommandHandler() {
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
		.setCondition(CustomCommand.adminOnly)
		.setDescription("List all guilds multiverse works in")
		
		register("ban") {
			Lists.blacklist(Snowflake(it.getOrNull(1)?.toULong() ?: throw CommandException("ban", "no uid specified")))
		}
		.setCondition(CustomCommand.adminOnly)
		.setHeader("id: Snowflake")
		.setDescription("Ban a user or a guild")
	
		register("warn") {
			val user = Snowflake(it.getOrNull(1) ?: throw CommandException("warn", "you must specify the user id!"))
			val category = it.getOrNull(3)?.let { RuleCategory.valueOf(it.uppercase()) } ?: RuleCategory.GENERAL
			val rule = category[it.getOrNull(2)?.toInt() ?: throw CommandException("warn", "you must specify the rule number")]
			Lists.warn(user, rule)
			
			Multiverse.brodcastSystem { content = "user ${Vars.supplier.getUserOrNull(user)?.tag} was warned for rule '$rule'" }
		}
		.setCondition(CustomCommand.adminOnly)
		.setHeader("id: Snowflake, rule: Int, rule category: [general]?")
		.setDescription("Warn a user (or a guild if you're mad enough).")
		
		register("unwarn") {
			val warns = Lists.warns.getOrDefault(Snowflake(it.getOrNull(1) ?: throw CommandException("unwarn", "no uid specified")), null)
			warns?.clear()?.also { replyWith(message, "cleared succefully") } ?: replyWith(message, "this user has no warnings")
		}
		.setCondition(CustomCommand.adminOnly)
		.setHeader("id: Snowflake")
		.setDescription("unwarn a user")
		
		register("mywarnings") {
			message.channel.createEmbed {
				val warnings = Lists.warns.getOrDefault(message.author!!.id, null)?.fold(0) { a, v -> a + v.points }?.toString() ?: "no"
				description = "User ${message.author?.tag} has $warnings warning points"
			}
		}
		.setDescription("list warnings of the caller")
		
		register("whitelist") {
			Lists.whitelist(Snowflake(it.getOrNull(1)?.toULong() ?: throw CommandException("ban", "no uid specified")))
		}
		.setCondition(CustomCommand.adminOnly)
		.setHeader("id: Snowflake")
		.setDescription("Whitelist a guild.")
		
		register("banlist") {
			replyWith(message, Lists.blacklist.joinToString(", "))
		}
		.setCondition(CustomCommand.adminOnly)
		.setDescription("List banned users / guilds. Note that this shows IDs, not names.")
		
		register("lastusers") {
			replyWith(message, Multiverse.ratelimited.keys.map {
				try {
					return@map "[${Vars.supplier.getUser(it).tag}]: $it"
				} catch (e: Exception) {
					return@map "[error]: $it"
				}
			}.joinToString(",\n"))
		}
		.setCondition(CustomCommand.adminOnly)
		.setDescription("List all users that have recently sent a message in multiverse")
		
		register("help") {
			message.channel.sendHelp(message.author!!, selfRef.commands)
		}
		.setDescription("Show the list of commands")
	
	
		register("echo") {
			if (it[0].isEmpty()) throw CommandException("echo", "can't send an empty message")
			
			Multiverse.brodcastSystem {
				content = it[0].take(1999)
			}
		}
		.setCondition(CustomCommand.adminOnly)
		.setHeader("text: String")
		.setDescription("Send a system message in multiverse")
		
		register("reload") {
			Lists.blacklist.clear()
			Lists.whitelist.clear()
			Lists.usertags.clear()
			Multiverse.updateState()
		}
		.setCondition(CustomCommand.adminOnly)
		.setDescription("Clear all lists and reload them from the respective channels. This command is to be called after unbanning removing an entry from white / black / other list.")
		
		register("rules") {
			message.channel.createMessage {
				messageReference = message.id
				RuleCategory.values().forEach {
					embed { description = it.toString() }
				}
			}
		}
		.setDescription("Show the list of multiversal rules")
		
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
		.setCondition(CustomCommand.adminOnly)
		.setHeader("level: [lifecycle, debug, info, error]")
		.setDescription("Set the log level")
	}
}
