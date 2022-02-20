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
	val universes = ArrayList<TextChannel>(50)
	/** All webhooks multiverse retranslates to */
	val universeWebhooks = ArrayList<UniverseEntry>(50)
	
	/** Rate limitation map */
	val ratelimited = HashMap<Snowflake, Long>(150)
	val ratelimit = 2000L
	
	val webhookName = "MultiverseWebhook"
	val settingsPrefix = "@set"
	val settingsChannel = Snowflake(937781472394358784UL)
	
	val systemName = "Multiverse"
	val systemAvatar = "https://lh6.googleusercontent.com/-m_Vgb1-Jm1e2nPTWHKLAh7s8pGmwMnzfWXw0QvygrDmmHE3rJRWzGd8QNFw65U3OfF19O7gPBtRYuqZP4mw=w1400-h2712"
	
	/** Sets up the multiverse */
	suspend fun start() {
		loadState(true)
		saveState()
		
		findChannels()
		
		Vars.client.launch {
			delay(10000L)
			brodcastSystem {
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
				
				if (event.message.webhookId != null && universeWebhooks.any { it.webhook != null && it.webhook!!.id == event.message.webhookId }) return@onEach
				
				if (!Lists.canTransmit(guild, event.message.author)) {
					replyWith(event.message, """
						[!] You're not allowed to send messages in multiverse. Please contact one of admins to find out why.
						You can do that using `flarogus report`.
					""".trimIndent())
					Log.info { "${event.message.author?.tag}'s multiversal message was not retranslated: `${event.message.content.take(200)}`" }
					return@onEach
				}
				
				try {
					//instaban spammers
					if (countPings(event.message.content) > 7) {
						Lists.blacklist += event.message.author!!.id //we don't need to ban permanently
						replyWith(event.message, "[!] You've been auto-banned from this multiverse instance. please wait 'till the next restart.")
						Log.info { "${event.message.author?.tag} was auto-tempbanned for attempting to ping too many people at once" }
						return@onEach
					}
					
					//block potential spam messages
					if (ScamDetector.hasScam(event.message.content)) {
						replyWith(event.message, "[!] your message contains a potential scam. if you're not a bot, remove any links and try again")
						Log.info { "a potential scam message sent by ${event.message.author?.tag} was blocked: ```${event.message.content.take(200)}```" }
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
						
					val username = buildString {
						if (customTag != null) {
							append('[')
							append(customTag)
							append(']')
						} else if (userid?.value in Vars.runWhitelist) {
							append("[Admin]")
						}
						append("[")
						append(guild?.name ?: "<DISCORD>")
						append("] ")
						append(author)
					}
					
					val finalMessage = buildString {
						val reply = event.message.referencedMessage
						
						if (reply != null) {
							val replyOrigin = "(> .+\n)?((?s).+)".toRegex().find(reply.content)?.groupValues?.getOrNull(2)?.replace('\n', ' ') ?: "unknown"
							
							append("> ")
							append(replyOrigin.take(100).replace("/", "\\/"))
							
							if (replyOrigin.length > 100) append("...")
							
							append("\n")
						}
						append(original)
					}.take(1999)
					
					val beginTime = System.currentTimeMillis()
					
					brodcast(event.message.channel.id.value, username, event.message.author?.getAvatarUrl()) {
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
					
					val time = System.currentTimeMillis() - beginTime
					Log.lifecycle { "$author's multiversal message was retranslated in $time ms." }
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
		
		//find webhooks of these channels
		universes.forEach { universe ->
			val entry = universeWebhooks.find { it.channel.id == universe.id } ?: UniverseEntry(null, universe).also { universeWebhooks.add(it) }
			
			if (entry.webhook != null) return@forEach
			
			try {
				var webhook: Webhook? = null	
				universe.webhooks.collect {
					if (it.name == webhookName) webhook = it
				}
				if (webhook == null) {
					webhook = universe.createWebhook(webhookName)
				}
				entry.webhook = webhook
			} catch (e: Exception) {
				if (!entry.hasReported) Log.error { "Could not acquire webhook for ${universe.name} (${universe.id}): $e" }
				
				if (!entry.hasReported) {
					val reason = if (e.toString().contains("Missing Permission")) {
						"missing 'MANAGE_WEBHOOKS' permission!\nYou should allow the bot to manage webhooks or ask the guild's admin(s) to do that!"
					} else {
						e.toString()
					}
					
					try {
						entry.channel.createEmbed { description = """
							[ERROR] Could not acquire a webhook: $reason
							----------
							***Using fallback strategy for this channel: messages will be sent in the classic way!***
						""".trimIndent() }
						
						entry.hasReported = true
					} catch (e: Exception) {}
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
	
	/** Same as normal brodcast but uses system pfp & name */
	inline suspend fun brodcastSystem(crossinline message: suspend MessageCreateBuilder.() -> Unit) = brodcast(0UL, systemName, systemAvatar, message)
	
	/** Sends a message into every multiverse channel expect blacklisted and the one with id == exclude  */
	inline suspend fun brodcast(exclude: ULong = 0UL, user: String? = null, avatar: String? = null, crossinline message: suspend MessageCreateBuilder.() -> Unit) {
		universeWebhooks.forEach {
			if (exclude != it.channel.id.value && Lists.canReceive(it.channel)) {
				try {
					if (it.webhook == null) {
						it.channel.createMessage {
							message()
							content = "$user: $content".take(1999).stripEveryone()
							allowedMentions() //forbid all mentions
						}
					} else {
						it.webhook!!.execute(it.webhook!!.token!!) {
							message()
							content = content?.take(1999)?.stripEveryone()
							allowedMentions() //forbid all mentions
							username = user ?: "unknown user"
							avatarUrl = avatar
						}
					}
				} catch (e: Exception) {
					Log.error { "failed to retranslate a message into ${it.channel.id}: $e" }
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
						brodcastSystem {
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

data class UniverseEntry(var webhook: Webhook?, val channel: TextChannel, var hasReported: Boolean = false)
