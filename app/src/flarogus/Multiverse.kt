package flarogus

import java.io.*
import java.net.*
import kotlin.concurrent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import dev.kord.common.entity.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.event.message.*
import dev.kord.core.entity.*
import dev.kord.core.entity.channel.*
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import flarogus.*
import flarogus.util.*

/**
 * Retranslates messages sent in any channel of guild network, aka Multiverse, into other multiverse channels
 *
 * The bot is hosted on github actions and it's really hard to make it save the state.
 * Thus, multiverse settings are stored on a discord server.
 */
object Multiverse {

	/** All channels the multiverse works in */
	val multiverse = ArrayList<MessageChannel>(50)
	/** Guilds that are allowed to send messages in multiverse */
	val whitelist = ArrayList<Snowflake>(50)
	/** Guilds / users that are blacklisted from multiverse */
	val blacklist = ArrayList<Snowflake>(50)
	/** Custom user tags */
	val usertags = HashMap<Snowflake, String>(50)
	/** Multiversal ruleset */
	val rules = ArrayList<String>(10)
	
	/** Rate limitation map */
	val ratelimited = HashMap<Snowflake, Long>(150)
	val ratelimit = 2000L
	
	val whitelistChannel = 932632370354475028UL
	val blacklistChannel = 932524242707308564UL
	val usertagsChannel = 932690515667849246UL
	val settingsChannel = 937781472394358784UL
	val rulesChannel = 940551409307377684UL
	
	val settingsPrefix = "@set"
	val rulesPrefix = "@rules:\n"
	
	/** Sets up the multiverse */
	suspend fun start() {
		loadState(true)
		saveState()
		
		findChannels()
		
		Vars.client.launch {
			delay(10000L)
			brodcast {
				embed { description = """
					***This channel is now a part of the Multiverse! There's ${multiverse.size - 1} channels!***
					Call `flarogus multiverse rules` to see the rules
				""".trimIndent() }
			}
		}
		
		fixedRateTimer("channel search", true, initialDelay = 5000L, period = 45 * 1000L) { updateState() }
		
		//retranslate any messages in multiverse channels
		Vars.client.events
			.filterIsInstance<MessageCreateEvent>()
			.filter { it.message.author?.id?.value != Vars.botId }
			.filter { it.message.channel.asChannel() in multiverse }
			.onEach { event ->
				val userid = event.message.author?.id
				val guild = event.getGuild()
				
				if (guild?.id in blacklist || guild?.id !in whitelist || event.message.author?.id in blacklist) {
					replyWith(event.message, "[!] you're not allowed to send messages in multiverse. please contact one of admins to find out why.")
					return@onEach
				}
				
				try {
					//instaban spammers
					if (countPings(event.message.content) > 7) {
						blacklist += event.message.author!!.id //we don't need to ban permanently
						replyWith(event.message, "[!] you've been auto-banned from this multiverse instance. please wait 'till the next restart.")
						return@onEach
					}
					
					//block potential spam messages
					if (ScamDetector.hasScam(event.message.content)) {
						replyWith(event.message, "[!] your message contains a potential scam. if you're not a bot, remove any links and try again")
						return@onEach
					}
					
					//rate limitation
					if (userid != null) {
						val current = System.currentTimeMillis()
						val lastMessage = ratelimited.getOrDefault(userid, 0L)
						
						if (current - lastMessage < ratelimit) {
							Vars.client.launch {
								replyWith(event.message, "[!] You are being rate limited. Please wait ${ratelimit + lastMessage - current} milliseconds.")
							}
							return@onEach
						} else {
							ratelimited[userid] = current
						}
					}
					
					//actual retranslation
					brodcast(event.message.channel.id.value) {
						val original = event.message.content
						val author = event.message.author?.tag?.replace("*", "\\*") ?: "<webhook>"
						val customTag = usertags.getOrDefault(userid, null)
						
						content = buildString {
							val reply = event.message.referencedMessage
							
							if (reply != null) {
								val replyOrigin = "(> .+\n)?((?s).+)".toRegex().find(reply.content)?.groupValues?.getOrNull(2)?.replace('\n', ' ') ?: "unknown"
								
								append("> ")
								append(replyOrigin.take(100).replace("/", "\\/"))
								
								if (replyOrigin.length > 100) append("...")
								
								append("\n")
							}
							
							append("**")
							
							if (customTag != null) {
								append('[')
								append(customTag)
								append(']')
							} else if (userid?.value in Vars.runWhitelist) {
								append("[Admin]")
							}
							
							append("[")
							append(author)
							append(" — ")
							append(guild?.name)
							append("]:")
							append("** ")
							append(original.take(1600))
						}
						
						try {
							event.message.stickers.forEach {
								val extension = when (it.formatType.value) {
									1 -> "png"
									2 -> "apng"
									else -> throw Exception()
								}
								
								val url = "https://discord.com/api/v9/stickers/${it.id.value}.${extension}"
								addFile("sticker-${it.id.value}.${extension}", URL(url).openStream())
							}
						} catch (e: Exception) {} //ignored: stickers are not so uh i forgor
						
						event.message.data.attachments.forEach { attachment ->
							addFile(attachment.filename, URL(attachment.url).openStream())
						}
					}
				} catch (e: Exception) {
					e.printStackTrace()
				}
			}.launchIn(Vars.client)
	}
	
	/** Searches for channels with "multiverse" in their names in all guilds this bot is in */
	fun findChannels() = Vars.client.launch {
		Vars.client.rest.user.getCurrentUserGuilds().forEach { 
			if (it.name.contains("@everyone") || it.name.contains("@here")) {
				//instant blacklist, motherfucker
				blacklist.add(it.id)
				return@forEach
			}
			
			Vars.client.unsafe.guild(it.id).asGuild().channelBehaviors.forEach {
				var c = it.asChannel()
				
				if (c.data.type == ChannelType.GuildText && c.data.name.toString().contains("multiverse")) {
					if (!multiverse.any { it.id.value == c.id.value }) {
						multiverse += TextChannel(c.data, Vars.client)
					}
				}
			}
		}
	};
	
	/** Updates ban, white and tag list */
	fun updateState() = Vars.client.launch {
		findChannels()
		
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
		
		loadState(false)
		saveState()
	}
	
	/** Utility function: asynchronously calls the specified function for every message in the channel. Catches and ignores any exceptions, Errors can be used to stop execution */
	suspend inline fun fetchMessages(channel: ULong, crossinline handler: suspend (Message) -> Unit) {
		try {
			val channel = Vars.client.unsafe.messageChannel(Snowflake(channel))
			
			channel.messages
				.onEach {
					try {
						handler(it)
					} catch (e: Exception) { //any invalid messages are ignored. this includes number format exceptions, nullpointers and etc
						e.printStackTrace()
					}
				}.collect()
		} catch (e: Throwable) { //this one should catch Error subclasseses too
			e.printStackTrace()
		}
	};
	
	/** Adds an id to the blacklist and sends a message in the blacklist channel (to save the entry) */
	fun blacklist(id: Snowflake) = Vars.client.launch {
		 try {
		 	blacklist.add(id)
		 	
		 	Vars.client.unsafe.messageChannel(Snowflake(blacklistChannel)).createMessage {
		 		content = "g${id.value}"
		 	}
		 } catch (ignored: Exception) {}
	};
	
	/** Sends a message into every multiverse channel expect blacklisted and the one with id == exclude  */
	inline fun brodcast(exclude: ULong = 0UL, crossinline message: suspend MessageCreateBuilder.() -> Unit) = Vars.client.launch {
		multiverse.forEach {
			if (exclude != it.id.value && it.id !in blacklist) {
				try {
					it.createMessage {
						message()
						
						content = content?.take(1999)?.stripEveryone()
						
						allowedMentions() //forbid all mentions
					}
				} catch (e: Exception) {
					e.printStackTrace()
				}
			}
		}
	};
	
	//i / o region
	/** reads state from settings channel, shuts the bot down if there's a newer instance running */
	suspend fun loadState(firstRun: Boolean) {
		var found = false
		
		fetchMessages(settingsChannel) {
			if (it.author?.id?.value == Vars.botId && it.content.startsWith(settingsPrefix)) {
				val map = SimpleMapSerializer.deserialize(it.content.substring(settingsPrefix.length))
				
				found = true
				
				//throwing an exception is perfectly fine here
				if (map["ubid"] as String? != Vars.ubid) {
					//shutdown if there's a newer instance running
					if (map.getOrDefault("started", 0L) as Long > Vars.startedAt) {
						brodcast {
							embed { description = "another instance is running, shutting the current one down" }
						}
						
						delay(10000L)
						
						Vars.client.shutdown()
					} else {
						//this instance is a newer one. get all saved data from the save message
						map.getOrDefault("runWhitelist", null)?.asOrNull<Array<Any>>()?.forEach { 
							if (it is ULong) Vars.runWhitelist.add(it)
						}
						
						Vars.flarogusEpoch = map.getOrDefault("epoch", null)?.asOrNull<Long>() ?: System.currentTimeMillis()
					}
				}
				
				throw Error() //exit from fetchMessages
			}
		}
		
		if (!found) {
			try {
				Vars.client.unsafe.messageChannel(Snowflake(settingsChannel)).createMessage(settingsPrefix)
				saveState()
			} catch (ignored: Exception) {}
		}
	}
	
	/** Saves the state to the settings channel */
	suspend fun saveState() {
		val map = mapOf<String, Any>(
			"ubid" to Vars.ubid,
			"runWhitelist" to Vars.runWhitelist.toTypedArray(),
			"started" to Vars.startedAt,
			"epoch" to Vars.flarogusEpoch
		)
		
		fetchMessages(settingsChannel) {
			if (it.author?.id?.value == Vars.botId && it.content.startsWith(settingsPrefix)) {
				it.edit {
					content = settingsPrefix + SimpleMapSerializer.serialize(map)
				}
				
				throw Error() //exit from fetchMessages. this is dumb, yes. but I'm too lazy to find a better way
			}
		}
	}
	
	inline fun <reified T> Any.asOrNull(): T? = if (this is T) this else null;
	
}
