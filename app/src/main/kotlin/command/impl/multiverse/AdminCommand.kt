package flarogus.command.impl.multiverse

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
import flarogus.command.*
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
			
			message.replyWith("```\n" + msg + "\n```")
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
			val id = it.getOrNull(1)?.toSnowflakeOrNull() ?: throw CommandException("ban", "no uid specified")
			Lists.blacklist.add(id) sendResultTo message
			Settings.updateState()
		}
		.header("id: Snowflake")

		register("remove") {
			val id = it.getOrNull(1)?.toULongOrNull() ?: throw CommandException("ban", "no uid specified")
			Lists.blacklist.removeAll { it.value == id } sendResultTo message
			Settings.updateState()
		}
		.header("id: Snowflake")

		register("list") {
			message.replyWith(Lists.blacklist.sorted().joinToString(","))
		}
		.description("List all banned ids")
	}
	.condition(CustomCommand.adminOnly)
	.description("Manage the blacklist")

	tree("whitelist") {
		register("add") {
			val id = it.getOrNull(1)?.toSnowflakeOrNull() ?: throw CommandException("no uid specified")
			Lists.whitelist.add(id) sendResultTo message
			Settings.updateState()
		}
		.header("id: Snowflake")

		register("remove") {
			val id = it.getOrNull(1)?.toULongOrNull() ?: throw CommandException("no uid specified")
			Lists.whitelist.removeAll { it.value == id } sendResultTo message
			Settings.updateState()
		}
		.header("id: Snowflake")
	}
	.condition(CustomCommand.adminOnly)
	.description("Manage the whitelist")

	tree("tag") {
		register("set") {
			expect(it.size != 2) { "you must provide exactly 2 arguments" }
			val id = it[1].toSnowflakeOrNull() ?: throw CommandException("the providen id is not valid")

			Lists.usertags[id] = it[2]
			message.replyWith("success.")
		}
		.header("id: Snowflake, tag: String")
		.description("Set the usertag of a user. The tag should not contain spaces.")

		register("clear") {
			val id = it.getOrNull(1)?.toSnowflakeOrNull() ?: throw CommandException("no uid providen")
			Lists.usertags.remove(id).isNotNull() sendResultTo message
		}
		.header("id: Snowflake")
		.description("Remove the usertag of a user")

		register("list") {
			message.replyWith(buildString {
				Lists.usertags.forEach { (id, tag) ->
					val user = Vars.supplier.getUserOrNull(id)
					append(user?.tag).append(" — ").appendLine(tag)
				}
			})
		}
	}
	.condition(CustomCommand.adminOnly)
	.description("Manage the list of usertags")
	
	tree("warn") {
		register("add") {
			val user = Snowflake(it.getOrNull(1) ?: throw CommandException("you must specify the user id!"))
			val category = it.getOrNull(3)?.let { RuleCategory.valueOf(it.uppercase()) } ?: RuleCategory.GENERAL
			val rule = category[it.getOrNull(2)?.toInt() ?: throw CommandException("you must specify the rule number")]
			Lists.warn(user, rule)
			
			Multiverse.brodcastSystem { content = "user ${Vars.supplier.getUserOrNull(user)?.tag} was warned for rule '$rule'" }
			message.replyWith("success.")
		}
		.header("id: Snowflake, rule: Int, rule category: [general]?")

		register("clear") {
			val warns = Lists.warns.getOrDefault(Snowflake(it.getOrNull(1) ?: throw CommandException("no uid specified")), null)
			warns?.clear()?.also { message.replyWith("cleared succefully") } ?: message.replyWith("this user has no warnings")
		}
		.header("id: Snowflake")
		.description("Remove all warnings of a user")

		register("list") {
			message.replyWith(buildString {
				Lists.warns.forEach { (id, warns) ->
					val user = with(this@register) { Vars.supplier.getUserOrNull(id) }
					append(user?.tag).append(" — ")
					warns.forEach {
						append(it.category).append('.').append(it.index)
						append(" [").append(it.points).append("points]; ")
					}
					appendLine()
				}
			})
		}
	}
	.condition(CustomCommand.adminOnly)
	.description("Manage warnings.")
	
	register("echo") {
		Multiverse.brodcastSystem { _ ->
			content = (it.getOrNull(0) ?: throw CommandException("echo", "can't send an empty message")).take(1999)
		}
	}
	.condition(CustomCommand.adminOnly)
	.header("text: String")
	.description("Send a system message in multiverse")
	
	register("setloglevel") {
		Log.level = Log.LogLevel.valueOf(it[1].uppercase())
		message.replyWith("success")
		Log.force { "log level was set to ${Log.level}!" }
	}
	.condition(CustomCommand.adminOnly)
	.header("level: [lifecycle, debug, info, error]")
	.description("Set the log level")
	
	register("purge") {
		val purgeCount = it.getOrNull(1)?.toIntOrNull() ?: throw CommandException("You must specify the purge count")
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
					it.origin?.delete()?.also { deleted++ }
				} catch (e: Exception) {
					errors++
				}
			}
		}
		
		message.replyWith("$deleted messages were deleted successfully, $errors messages could not be deleted.")
	}
	.condition(CustomCommand.adminOnly)
	.header("purgeCount: Int, deleteOrigin: Boolean?")
	.description("Delete up to [purgeAmount] messages sent in this instance. By default doesn't delete original messages, [deleteOrigin] overrides this.")
} }
