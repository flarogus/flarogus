package flarogus.multiverse

import kotlinx.coroutines.*
import dev.kord.rest.builder.message.create.*
import dev.kord.common.entity.*
import dev.kord.core.entity.*
import dev.kord.core.entity.channel.*
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import flarogus.*
import flarogus.util.*
import flarogus.multiverse.*

/** Contains lists related to multiverse and manages their updating. */
object Lists {
	/** Guilds that are allowed to send messages in multiverse */
	val whitelist = ArrayList<Snowflake>(50)
	/** Guilds / users that are blacklisted from multiverse */
	val blacklist = ArrayList<Snowflake>(50)
	/** Warned users */
	val warns = HashMap<Snowflake, MutableList<Rule>>(50)
	/** Custom user tags */
	val usertags = HashMap<Snowflake, String>(50)
	
	/** Amount of warns required for the user to be auto-banned */
	val criticalWarns = 5
	
	/** Warn a user */
	fun warn(user: Snowflake, rule: Rule) {
		if (rule.points < 0) return;
		
		val entry = warns.getOrDefault(user, null) ?: ArrayList<Rule>(3).also { warns[user] = it }
		entry.add(rule)
		
		val points = entry.fold(0) { a, r -> a + r.points }
		if (points >= criticalWarns) {
			blacklist.add(user)
			Vars.client.launch { Multiverse.brodcastSystem {
				embed { description = "User ${Vars.supplier.getUserOrNull(user)?.tag} was auto-banned for having too many warn points ($points > $criticalWarns)" }
			} }
		}
	}
	
	/** Returns whether it's allowed for this pair of guild + user to send messages in multiverse */
	fun canTransmit(guild: Guild?, user: User?) = guild == null || (guild.id !in blacklist && guild.id in whitelist && user?.id !in blacklist);
	
	/** Returns whether this channel is allowed to receive messages from multiverse */
	fun canReceive(channel: Channel?) = channel != null && channel.data.guildId.value.let { it !in blacklist && it in whitelist }
}
