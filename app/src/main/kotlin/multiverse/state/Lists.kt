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
	
	val whitelistChannel by lazy { Vars.client.unsafe.messageChannel(Snowflake(932632370354475028UL)) }
	val blacklistChannel by lazy { Vars.client.unsafe.messageChannel(Snowflake(932524242707308564UL)) }
	val usertagsChannel by lazy { Vars.client.unsafe.messageChannel(Snowflake(932690515667849246UL)) }
	
	/** Amount of warns required for the user to be auto-banned */
	val criticalWarns = 5
	
	fun updateLists() = Vars.client.launch {
		//add blacklisted users and guilds
		fetchMessages(blacklistChannel) {
			if (!it.content.startsWith("g") && !it.content.startsWith("u")) return@fetchMessages
			
			val id = "[ug](\\d+)".toRegex().find(it.content)!!.groupValues[1].toULong()
			
			val snow = Snowflake(id)
			if (snow !in blacklist) blacklist.add(snow)
		}
		
		//add whitelisted guilds
		fetchMessages(whitelistChannel) {
			val id = it.content.trim().toULong()
				
			val snow = Snowflake(id)
			if (snow !in whitelist) whitelist.add(snow)
		}
		
		//find user tags
		fetchMessages(usertagsChannel) {
			val groups = "(\\d+):(.+)".toRegex().find(it.content)!!.groupValues //null pointer is acceptable
			
			val id = Snowflake(groups[1].toULong())
			val tag = groups[2].trim()
			usertags[id] = tag
		}
	};
	
	/** Adds an id to the blacklist and sends a message in the blacklist channel (to save the entry) */
	fun blacklist(id: Snowflake) = Vars.client.launch {
		 try {
		 	blacklist.add(id)
		 	
		 	blacklistChannel.createMessage {
		 		content = "g${id.value}"
		 	}
		 } catch (ignored: Exception) {}
	};
	
	/** Whitelists a guild and adds an entry in whitelistChannel */
	fun whitelist(id: Snowflake) = Vars.client.launch {
		 try {
		 	whitelist.add(id)
		 	
		 	whitelistChannel.createMessage {
		 		content = "${id.value}"
		 	}
		 } catch (ignored: Exception) {}
	};
	
	/** Warn a user */
	fun warn(user: Snowflake, rule: Rule) {
		if (rule.points < 0) return;
		
		val entry = warns.getOrDefault(user, null) ?: ArrayList<Rule>(3).also { warns[user] = it }
		entry.add(rule)
		
		val points = entry.fold(0) { a, r -> a + r.points }
		if (points >= criticalWarns) {
			blacklist(user)
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
