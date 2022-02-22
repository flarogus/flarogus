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
	},
	
	header = "subcommand",
	
	description = "Execute a multiversal subcommand. Use flarogus multiverse help to see all available commands"
)

private val subcommands: Map<String, CustomCommand> = mapOf(
	"listguilds" to CustomCommand(
		handler = {
			val msg = Multiverse.universes.map {
				try {
					message.supplier.getGuild(it.data.guildId.value ?: return@map null)
				} catch (ignored: Exception) {
					null
				}
			}.filter { it != null }.toSet().map { "${it?.id?.value} - ${it?.name}" }.joinToString(",\n")
			
			replyWith(message, msg)
		}, 
		condition = CustomCommand.adminOnly, 
		description = "List all guilds multiverse works in"
	),
	
	"ban" to CustomCommand(
		handler = {
			Lists.blacklist(Snowflake(it.getOrNull(1)?.toULong() ?: throw CommandException("ban", "no uid specified")))
		},
		condition = CustomCommand.adminOnly,
		header = "id: Snowflake",
		description = "Ban a user or a guild"
	),
	
	"warn" to CustomCommand(
		handler = {
			val user = Snowflake(it.getOrNull(1) ?: throw CommandException("warn", "you must specify the user id!"))
			val category = it.getOrNull(3)?.let { RuleCategory.valueOf(it.uppercase()) } ?: RuleCategory.GENERAL
			val rule = category[it.getOrNull(2)?.toInt() ?: throw CommandException("warn", "you must specify the rule number")]
			Lists.warn(user, rule)
			
			Multiverse.brodcastSystem { content = "user ${Vars.supplier.getUserOrNull(user)?.tag} was warned for rule '$rule'" }
		},
		condition = CustomCommand.adminOnly,
		header = "id: Snowflake, rule: Int, rule category: [general]?",
		description = "Warn a user (or a guild if you're mad enough)."
	),
	
	"unwarn" to CustomCommand(
		handler = {
			val warns = Lists.warns.getOrDefault(Snowflake(it.getOrNull(1) ?: throw CommandException("unwarn", "no uid specified")), null)
			warns?.clear()?.also { replyWith(message, "cleared succefully") } ?: replyWith(message, "this user has no warnings")
		},
		condition = CustomCommand.adminOnly,
		header = "id: Snowflake",
		description = "unwarn a user"
	),
	
	"mywarnings" to CustomCommand(
		handler = {
			message.channel.createEmbed {
				val warnings = Lists.warns.getOrDefault(message.author!!.id, null)?.fold(0) { a, v -> a + v.points }?.toString() ?: "no"
				description = "User ${message?.author?.tag} has $warnings warning points"
			}
		},
		description = "list warnings of the caller"
	),
	
	"whitelist" to CustomCommand(
		handler = {
			Lists.whitelist(Snowflake(it.getOrNull(1)?.toULong() ?: throw CommandException("ban", "no uid specified")))
		},
		condition = CustomCommand.adminOnly,
		header = "id: Snowflake",
		description = "Whitelist a guild."
	),
	
	"banlist" to CustomCommand(
		handler = {
			replyWith(message, Lists.blacklist.joinToString(", "))
		},
		condition = CustomCommand.adminOnly,
		description = "List banned users / guilds. Note that this shows IDs, not names."
	),
	
	"lastusers" to CustomCommand(
		handler = {
			replyWith(message, Multiverse.ratelimited.keys.map {
				try {
					return@map "[${Vars.supplier.getUser(it).tag}]: $it"
				} catch (e: Exception) {
					return@map "[error]: $it"
				}
			}.joinToString(",\n"))
		},
		condition = CustomCommand.adminOnly,
		description = "List all users that have recently sent a message in multiverse"
	),
	
	"help" to CustomCommand(
		handler = {
			message.channel.sendHelp(message.author!!, getSubcommands())
		},
		description = "Show the list of commands"
	),
	
	"echo" to CustomCommand(
		handler = {
			if (it[0].isEmpty()) throw CommandException("echo", "can't send an empty message")
			
			Multiverse.brodcastSystem {
				content = it[0].take(1999)
			}
		},
		condition = CustomCommand.adminOnly,
		header = "text: String",
		description = "Send a system message in multiverse"
	),
	
	"reload" to CustomCommand(
		handler = {
			Lists.blacklist.clear()
			Lists.whitelist.clear()
			Lists.usertags.clear()
			Multiverse.updateState()
		},
		condition = CustomCommand.adminOnly,
		description = "Clear all lists and reload them from the respective channels. This command is to be called after unbanning removing an entry from white / black / other list."
	),
	
	"rules" to CustomCommand(
		handler = {
			message.channel.createMessage {
				messageReference = message.id
				RuleCategory.values().forEach {
					embed { description = it.toString() }
				}
			}
		},
		description = "Show the list of multiversal rules"
	),
	
	"setloglevel" to CustomCommand(
		handler = {
			Log.level = when (it.getOrNull(1)?.lowercase()) {
				//todo: maybe valueOf would be better?
				"lifecycle" -> Log.LogLevel.LIFECYCLE
				"debug" -> Log.LogLevel.DEBUG
				"info" -> Log.LogLevel.INFO
				"error" -> Log.LogLevel.ERROR
				null -> throw CommandException("setLogLevel", "no log level specified")
				else -> throw CommandException("setLogLevel", "unknown log level")
			}
		},
		condition = CustomCommand.adminOnly,
		header = "level: [lifecycle, debug, info, error]",
		description = "Set the log level"
	)
)

/** Java doesn't allow to reference a variable from itself. UnU. */
fun getSubcommands() = subcommands;
