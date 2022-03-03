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
	/** Warned users */
	val warns = HashMap<Snowflake, MutableList<Rule>>(50)
	/** Custom user tags */
	val usertags = HashMap<Snowflake, String>(50)
	
	val whitelistChannel = Snowflake(932632370354475028UL)
	val blacklistChannel = Snowflake(932524242707308564UL)
	val usertagsChannel = Snowflake(932690515667849246UL)
	val rulesChannel = Snowflake(940551409307377684UL)
	
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
	fun canReceive(channel: Channel?) = channel != null && channel.data.guildId.value !in blacklist
	
}


enum class RuleCategory(val index: Int, val description: String, val rules: List<Rule>) {
	GENERAL(1, "list of multiversal rules", listOf(
		Rule(1, "Do not insult, harrass, discriminate other people. This should be obvious."),
		Rule(1, "Do not spam / flood the multiverse. Meme dumps are allowed as long as they don't disturb other users."),
		Rule(4, "Posting scam links is strictly prohibited and can result in an immediate ban, unless the link was successfully blocked by the filter."),
		Rule(2, "Avoid posting nsfw-content. Posting explicit images / videos / gifs is prohibited. Videos with a questionable preview are counted too."),
		Rule(2, "Do not advertise discord servers without consent."),
		Rule(1, "Avoid speaking foreign languages that other users can't understand (if they can understand it, it's fine) and do not encode text messages."),
		Rule(10, "Spam raids are forbidden, any raider is to be banned immediately.")
	)),
	
	ADDITIONAL(2, "notes", listOf(
		Rule(-1, "Multiversal admins are Mnemotechnician#9967, SMOLKEYS#4156, real sushi#0001."),
		Rule(-1, "Your multiversal channels are your responsibility, it doesn't matter whether you connect a general channel of a popular server or an admin-only channel, rules still apply."),
		Rule(-1, "The fact that 2 of 3 admins are furries __does not__ mean you can post yiff in multiverse!"),
		Rule(-1, "Personal animosity is not a valid reason for any form of punishment, if an admin does that, report it to owner")
	)),
	
	PUNISHMENT(3, "the following punishments can be applied by the admins", listOf(
		Rule(-1, "A verbal warning."),
		Rule(-1, "A physical warning (the amount of warning points depends on the rule)."),
		Rule(-1, "A temporary ban (applied automatically when the user has 5 warn points)."),
		Rule(-1, "A permanent ban.")
	));
	
	init {
		rules.forEachIndexed { ruleIndex: Int, it ->
			it.category = index
			it.index = ruleIndex
		}
	}

	override fun toString() = buildString {
		append(super.toString()).append(": ").append(description).append("\n\n")
		rules.forEachIndexed { i: Int, it -> append(i + 1).append(". ").append(it).append('\n') }
	};
	
	operator fun get(number: Int): Rule = (rules.getOrNull(number - 1) ?: throw IllegalArgumentException("rule '${super.toString()}.$index' doesn't exist!"))
	
	companion object {
		fun of(category: Int, rule: Int) = RuleCategory.values().find { it.index == category }?.get(rule + 1)
	}
}

class Rule(val points: Int = -1, val description: String) {
	//these two are inited right after creation
	var category = -1
	var index = -1
	
	override fun toString() = buildString {
		append(description)
		if (points > 0) append(" [").append(points).append(" warning points]")
	}
}
