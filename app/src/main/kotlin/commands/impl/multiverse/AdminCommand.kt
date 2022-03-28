package flarogus.commands.impl.multiverse

import kotlin.math.*
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
import flarogus.multiverse.state.*

typealias CustomCommand = flarogus.commands.Command

val AdminCommand = Supercommand(
	name = "admin",

	description = "Execute an admin-only multiversal subcommand",
	
	condition = CustomCommand.adminOnly
).also { it.commands.apply {
	tree("listguilds") {
		suspend fun MessageCreateEvent.common(unwhitelisted: Boolean) {
			val msg = Multiverse.universeWebhooks.mapNotNull {
				if (!unwhitelisted || it.channel.data.guildId.value?.let { it !in Lists.whitelist } ?: true) {
					it.channel.data.guildId.value?.let {
						try { Vars.supplier.getGuildOrNull(it) } catch (e: Exception) { null }
					}
				} else {
					null
				}
			}.distinct().sortedBy { it.id }.map { "${it.id.value} - ${it.name}" }.joinToString(",\n")
			
			replyWith(message, "```\n" + msg + "\n```")
		}

		register("all") {
			common(false)
		}
		.description("List all guids")

		register("unwhitelisted") {
			common(true)
		}
		.description("List unwhitelisted guids")
	}
	.condition(CustomCommand.adminOnly)
	.description("List the guilds multiverse works in.")
	
	tree("blacklist") {
		register("add") {
			val id = Snowflake(it.getOrNull(1)?.toULong() ?: throw CommandException("ban", "no uid specified"))
			Lists.blacklist(id)
		}
		.header("id: Snowflake")

		register("remove") {
			TODO()
		}
		.header("id: Snowflake")
		.description("not implemented yet")

		register("list") {
			replyWith(message, Lists.blacklist.sorted().joinToString(","))
		}
		.description("List all banned ids")
	}
	.condition(CustomCommand.adminOnly)
	.description("Manage the blacklist")

	tree("whitelist") {
		register("add") {
			Lists.whitelist(Snowflake(it.getOrNull(1)?.toULong() ?: throw CommandException("no uid specified")))
		}
		.header("id: Snowflake")

		register("remove") {
			TODO()
		}
		.header("id: Snowflake")
		.description("not implemented yet")
	}
	.condition(CustomCommand.adminOnly)
	.description("Manage the whitelist")

	tree("tag") {
		register("set") {
			if (it.size != 2) { throw CommandException("you must provide exactly 2 arguments") }
			val id = Snowflake(it[1].toULong())
			
			Lists.usertags[id]
		}
		.header("id: Snowflake, tag: String")
		.description("Set the usertag of a user. The tag should not contain spaces.")

		register("clear") {
			TODO()
		}
		.header("id: Snowflake")
		.description("Not implemented yet")
	}
	.condition(CustomCommand.adminOnly)
	.description("Nanage the list of usertags")
	
	tree("warn") {
		register("add") {
			val user = Snowflake(it.getOrNull(1) ?: throw CommandException("you must specify the user id!"))
			val category = it.getOrNull(3)?.let { RuleCategory.valueOf(it.uppercase()) } ?: RuleCategory.GENERAL
			val rule = category[it.getOrNull(2)?.toInt() ?: throw CommandException("you must specify the rule number")]
			Lists.warn(user, rule)
			
			Multiverse.brodcastSystem { content = "user ${Vars.supplier.getUserOrNull(user)?.tag} was warned for rule '$rule'" }
		}
		.header("id: Snowflake, rule: Int, rule category: [general]?")

		register("clear") {
			val warns = Lists.warns.getOrDefault(Snowflake(it.getOrNull(1) ?: throw CommandException("no uid specified")), null)
			warns?.clear()?.also { replyWith(message, "cleared succefully") } ?: replyWith(message, "this user has no warnings")
		}
		.header("id: Snowflake")
		.description("Remove all warnings of a user")
	}
	.condition(CustomCommand.adminOnly)
	.description("Manage warnings.")
	
	register("echo") {
		Multiverse.brodcastSystem {
			content = (it.getOrNull(0) ?: throw CommandException("echo", "can't send an empty message")).take(1999)
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
	
	register("setloglevel") {
		Log.level = Log.LogLevel.valueOf(it[1].uppercase())
	}
	.condition(CustomCommand.adminOnly)
	.header("level: [lifecycle, debug, info, error]")
	.description("Set the log level")
	
	register("purge") {
		val purgeCount = it.getOrNull(1)?.toIntOrNull() ?: throw CommandException("purge", "You must specify the purge count")
		val deleteOrigin = it.getOrNull(2)?.toBoolean() ?: false
		
		var errors = 0
		var deleted = 0
		Multiverse.history.takeLast(min(purgeCount, Multiverse.history.size)).forEach {
			it.retranslated.forEach { 
				try {
					it.delete()
					deleted++
				} catch (e: Exception) {
					errors++
				}
			}
			
			if (deleteOrigin) {
				try {
					it.origin.delete()
					deleted++
				} catch (e: Exception) {
					errors++
				}
			}
		}
		
		replyWith(message, "$deleted messages were deleted successfully, $errors messages could not be deleted.")
	}
	.condition(CustomCommand.adminOnly)
	.header("purgeCount: Int, deleteOrigin: Boolean?")
	.description("Delete up to [purgeAmount] messages sent in this instance. By default doesn't delete original messages, [deleteOrigin] overrides this.")
} }
