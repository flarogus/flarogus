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

/** Contains lists related to multiverse and manages their updating */
object Lists {
	
	/** Guilds that are allowed to send messages in multiverse */
	val whitelist = ArrayList<Snowflake>(50)
	/** Guilds / users that are blacklisted from multiverse */
	val blacklist = ArrayList<Snowflake>(50)
	/** Custom user tags */
	val usertags = HashMap<Snowflake, String>(50)
	/** Multiversal ruleset */
	val rules = ArrayList<String>(10)
	
	val whitelistChannel = Snowflake(932632370354475028UL)
	val blacklistChannel = Snowflake(932524242707308564UL)
	val usertagsChannel = Snowflake(932690515667849246UL)
	val rulesChannel = Snowflake(940551409307377684UL)
	
	val rulesPrefix = "@rules:\n"
	
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
		
		//read ruleset
		rules.clear()
		fetchMessages(rulesChannel) {
			if (it.content.startsWith(rulesPrefix)) {
				rules += it.content.substring(rulesPrefix.length)
			}
		}
	};
	
	/** Adds an id to the blacklist and sends a message in the blacklist channel (to save the entry) */
	fun blacklist(id: Snowflake) = Vars.client.launch {
		 try {
		 	blacklist.add(id)
		 	
		 	Vars.client.unsafe.messageChannel(blacklistChannel).createMessage {
		 		content = "g${id.value}"
		 	}
		 } catch (ignored: Exception) {}
	};
	
	/** Whitelists a guild and adds an entry in whitelistChannel */
	fun whitelist(id: Snowflake) = Vars.client.launch {
		 try {
		 	whitelist.add(id)
		 	
		 	Vars.client.unsafe.messageChannel(whitelistChannel).createMessage {
		 		content = "${id.value}"
		 	}
		 } catch (ignored: Exception) {}
	};
	
	/** Returns whether it's allowed for this pair of guild + user to send messages in multiverse */
	fun canTransmit(guild: Guild?, user: User?) = guild == null || (guild.id !in blacklist && guild.id in whitelist && user?.id !in blacklist)
	
	/** Returns whether this channel is allowed to receive messages from multiverse */
	fun canReceive(channel: Channel?) = channel != null && channel.data.guildId?.value !in blacklist
}
