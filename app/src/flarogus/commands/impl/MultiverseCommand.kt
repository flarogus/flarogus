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

val MultiverseCommand = flarogus.commands.Command(
	handler = {
		val command = it.getOrNull(1)
		
		when (command?.toLowerCase()) {
			//lists the guilds multiverse channels exist in
			"listguilds" -> {
				val msg = Multiverse.multiverse.map {
					message.supplier.getGuild(it.data.guildId.value ?: return@map null)
				}.filter { it != null}.toSet().map { "${it?.id?.value} - ${it?.name?.stripEveryone()}" }.joinToString(",\n")
				replyWith(message, msg)
			}
			
			//bans a user or a guild
			"ban" -> Multiverse.blacklist(Snowflake(it.getOrNull(2)?.toULong() ?: throw CommandException("ban", "no uid specified")))
			
			//lists banned users and guild
			"banlist" -> replyWith(message, Multiverse.blacklist.joinToString(", "))
			
			//lists users that have sent a message in the multiverse
			"lastusers" -> replyWith(message, Multiverse.ratelimited.keys.map {
				try {
					return@map "[${Vars.client.defaultSupplier.getUser(it).tag}]: $it"
				} catch (e: Exception) {
					return@map "[error]: $it"
				}
			}.joinToString(",\n"))
			
			null -> replyWith(message, "please specify a multiversal subcommand")
			
			else -> replyWith(message, "unknown mutliversal subcommand")
		}
	},
	
	header = "subcommand: [listGuilds, ban (id), banlist, lastusers]",
	
	description = "Execute a multiversal subcommand",
	
	condition = { it.id.value in Vars.runWhitelist }
)
