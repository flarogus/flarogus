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
			//turn ["subcommand arg1 arg2", "subcommand", "arg1", "arg2"] into ["arg1 arg2", "arg1", "arg2"]
			val args = it.toMutableList()
			args.removeAt(0)
			args[0] = it[0].substring(commandName.length + 1)
			
			this.handler(args)
		} else {
			throw CommandException("multiverse", "you're not allowed to execute command $commandName!")
		}
	},
	
	header = "subcommand",
	
	description = "Execute a multiversal subcommand. Use flarogus multiverse help to see all available commands",
	
	condition = { it.id.value in Vars.runWhitelist }
)

private val subcommands: Map<String, CustomCommand> = mapOf(
	"listguilds" to CustomCommand(
		handler = {
			val msg = Multiverse.multiverse.map {
				message.supplier.getGuild(it.data.guildId.value ?: return@map null)
			}.filter { it != null}.toSet().map { "${it?.id?.value} - ${it?.name?.stripEveryone()}" }.joinToString(",\n")
			
			replyWith(message, msg)
		}, 
		condition = CustomCommand.adminOnly, 
		description = "List all guilds multiverse works in"
	),
	
	"ban" to CustomCommand(
		handler = {
			Multiverse.blacklist(Snowflake(it.getOrNull(1)?.toULong() ?: throw CommandException("ban", "no uid specified")))
		},
		condition = CustomCommand.adminOnly,
		header = "id: Snowflake",
		description = "Ban a user or a guild"
	),
	
	"banlist" to CustomCommand(
		handler = {
			replyWith(message, Multiverse.blacklist.joinToString(", "))
		},
		condition = CustomCommand.adminOnly,
		description = "List banned users / guilds. Note that this shows IDs, not names."
	),
	
	"lastusers" to CustomCommand(
		handler = {
			replyWith(message, Multiverse.ratelimited.keys.map {
				try {
					return@map "[${Vars.client.defaultSupplier.getUser(it).tag}]: $it"
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
		}
	)
)

/** Java doesn't allow to reference a variable from itself. UnU. */
fun getSubcommands() = subcommands;