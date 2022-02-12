package flarogus.multiverse

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
	val universes = ArrayList<MessageChannel>(50)
	
	/** Rate limitation map */
	val ratelimited = HashMap<Snowflake, Long>(150)
	val ratelimit = 2000L
	
	val settingsPrefix = "@set"
	val settingsChannel = Snowflake(937781472394358784UL)
	
	/** Sets up the multiverse */
	suspend fun start() {
		loadState(true)
		saveState()
		
		findChannels()
		
		Vars.client.launch {
			delay(10000L)
			brodcast {
				embed { description = """
					***This channel is now a part of the Multiverse! There's ${universes.size - 1} channels!***
					Call `flarogus multiverse rules` to see the rules
				""".trimIndent() }
			}
		}
		
		fixedRateTimer("update state", true, initialDelay = 5000L, period = 45 * 1000L) { updateState() }
		
		//retranslate any messages in multiverse channels
		Vars.client.events
			.filterIsInstance<MessageCreateEvent>()
			.filter { it.message.author?.id?.value != Vars.botId }
			.filter { event -> universes.any { event.message.channel.id == it.id } }
			.onEach { event ->
				val userid = event.message.author?.id
				val guild = event.getGuild()
				
				if (!Lists.canTransmit(guild, event.message.author)) {
					replyWith(event.message, "[!] you're not allowed to send messages in multiverse. please contact one of admins to find out why.")
					return@onEach
				}
				
				try {
					//instaban spammers
					if (countPings(event.message.content) > 7) {
						Lists.blacklist += event.message.author!!.id //we don't need to ban permanently
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
					val original = event.message.content
					val author = event.message.author?.tag?.replace("*", "\\*") ?: "<webhook>"
					val customTag = Lists.usertags.getOrDefault(userid, null)
						
					val finalMessage = buildString {
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
						append(guild?.name ?: "<DISCORD>")
						append("]:")
						append("** ")
						append(original.take(1600))
					}
					
					brodcast(event.message.channel.id.value) {
						content = finalMessage
						
						/* TODO: this doesn't work and never did
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
						*/
						
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
		//todo: this code is unoptimized AS FUCK and I don't know why I've choosen these exact solutions.
		//this method is to be rewritten
		Vars.client.rest.user.getCurrentUserGuilds().forEach { 
			if (it.name.contains("@everyone") || it.name.contains("@here")) {
				//instant blacklist, motherfucker
				Lists.blacklist.add(it.id)
				return@forEach
			}
			
			Vars.client.unsafe.guild(it.id).asGuild().channelBehaviors.forEach {
				var c = it.asChannel()
				
				if (c.data.type == ChannelType.GuildText && c.data.name.toString().contains("multiverse")) {
					if (!universes.any { it.id.value == c.id.value }) {
						universes += TextChannel(c.data, Vars.client)
					}
				}
			}
		}
	};
	
	/** Updates everything */
	fun updateState() = Vars.client.launch {
		findChannels()
		Lists.updateLists()
		
		loadState(false)
		saveState()
	}
	
	/** Sends a message into every multiverse channel expect blacklisted and the one with id == exclude  */
	inline suspend fun brodcast(exclude: ULong = 0UL, crossinline message: suspend MessageCreateBuilder.() -> Unit) {
		universes.forEach {
			if (exclude != it.id.value && Lists.canReceive(it)) {
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
						
						Vars.client.shutdown()
					} else {
						//this instance is a newer one. get all saved data from the save message
						map.getOrDefault("runWhitelist", null)?.asOrNull<Array<Any>>()?.forEach { 
							if (it is ULong) Vars.runWhitelist.add(it)
						}
						
						Vars.flarogusEpoch = map.getOrDefault("epoch", null)?.asOrNull<Long>() ?: System.currentTimeMillis()
						
						Log.level = Log.LogLevel.of(map.getOrDefault("log", null)?.asOrNull<Int>() ?: -1)
					}
				}
				
				throw Error() //exit from fetchMessages
			}
		}
		
		if (!found) {
			try {
				Vars.client.unsafe.messageChannel(settingsChannel).createMessage(settingsPrefix)
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
			"epoch" to Vars.flarogusEpoch,
			"log" to Log.level.level
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
	
	inline fun <T> T.catchAny(block: (T) -> Unit): Unit {
		try {
			block(this)
		} catch (ignored: Exception) {}
	}
	
}
