package flarogus.multiverse

import java.io.*
import java.net.*
import kotlin.math.*
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
import flarogus.multiverse.npc.impl.*

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
	
	/** Files with size exceeding this limit will be sent in the form of links */
	val maxFileSize = 1024 * 1024 * 3 - 1024
	
	val webhookName = "MultiverseWebhook"
	val systemName = "Multiverse"
	val systemAvatar = "https://drive.google.com/uc?export=download&id=197qxkXH2_b0nZyO6XzMC8VeYTuYwcai9"
	
	val npcs = mutableListOf(AmogusNPC())
	
	/** Sets up the multiverse */
	suspend fun start() {
		Settings.updateState()
		
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
				
				if (isOwnMessage(event.message)) return@onEach
				
				if (!Lists.canTransmit(guild, event.message.author)) {
					replyWith(event.message, """
						[!] You're not allowed to send messages in multiverse. Please contact one of admins to find out why.
						You can do that using `flarogus report`.
					""".trimIndent())
					Log.info { "${event.message.author?.tag}'s multiversal message was not retranslated: `${event.message.content.take(200)}`" }
					return@onEach
				}
				
				/*
				//block messages with oversize files: most servers don't accept >=8mb files
				if (event.message.data.attachments.any { it.size > maxFilesize }) {
					replyWith(event.message, "Cannot retranslate this message: file size ≥ 8mb")
					return@onEach
				}
				*/
				
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
					val webhook = event.message.webhookId?.let { Vars.supplier.getWebhookOrNull(it) }
					val author = event.message.author?.tag?.replace("*", "\\*") ?: "webhook<${webhook?.name}>"
					val customTag = Lists.usertags.getOrDefault(userid, null)
						
					val username = buildString {
						if (customTag != null) {
							append('[')
							append(customTag)
							append(']')
						} else if (userid?.value in Vars.runWhitelist) {
							append("[Admin]")
						}
						append(author)
						append(" — ")
						append(guild?.name ?: "<DISCORD>")
					}
					
					var finalMessage = buildString {
						append(original)
						
						event.message.data.attachments.forEach { attachment ->
							if (attachment.size >= maxFileSize) {
								append('\n').append(attachment.url)
							}
						}
					}.take(1999)
					
					if (finalMessage.isEmpty() && event.message.data.attachments.isEmpty()/* && event.message.data.stickers.isEmpty*/) {
						finalMessage = "<no content>"
					}
					
					val beginTime = System.currentTimeMillis()
					
					brodcast(event.message.channel.id.value, username, event.message.author?.getAvatarUrl() ?: webhook?.data?.avatar) {
						var finalContent = finalMessage
						
						quoteMessage(event.message.referencedMessage)
						
						event.message.data.attachments.forEach { attachment ->
							if (attachment.size < maxFileSize) {
								addFile(attachment.filename, URL(attachment.url).openStream())
							}
						}
						
						content = finalContent
					}
					
					npcs.forEach { it.multiversalMessageReceived(event.message) }
					
					val time = System.currentTimeMillis() - beginTime
					Log.lifecycle { "$author's multiversal message was retranslated in $time ms." }
				} catch (e: Exception) {
					e.printStackTrace()
				}
			}.launchIn(Vars.client)
	}
	
	/** Searches for channels with "multiverse" in their names in all guilds this bot is in */
	suspend fun findChannels() {
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
						"missing 'MANAGE_WEBHOOKS' permission!"
					} else {
						e.toString()
					}
					
					try {
						entry.channel.createEmbed { description = """
							[ERROR] Could not acquire a webhook: $reason
							----------
							Webhookless communication is deprecated and __IS NO LONGER SUPPORTED__.
							Contact the server's staff or allow the bot to manage webhooks yourself.
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
		
		Settings.updateState()
	}
	
	/** Same as normal brodcast but uses system pfp & name */
	inline suspend fun brodcastSystem(crossinline message: suspend MessageCreateBuilder.() -> Unit) = brodcast(0UL, systemName, systemAvatar, message)
	
	/** Sends a message into every multiverse channel expect blacklisted and the one with id == exclude  */
	inline suspend fun brodcast(exclude: ULong = 0UL, user: String? = null, avatar: String? = null, crossinline message: suspend MessageCreateBuilder.() -> Unit) {
		val deferreds = arrayOfNulls<Deferred<Any>>(universeWebhooks.size) //todo: can i avoid array allocation?
		
		universeWebhooks.forEachIndexed { index, it ->
			if (exclude != it.channel.id.value && Lists.canReceive(it.channel)) {
				deferreds[index] = Vars.client.async {
					try {
						if (it.webhook != null) {
							it.webhook!!.executeIgnored(it.webhook!!.token!!) {
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
		}
		
		//wait for every deferred to finish
		deferreds.forEach { if (it != null) it.await() }
	};
	
	/** Returns whether this message was sent by flarogus */
	fun isOwnMessage(message: Message): Boolean {
		return message.author?.id?.value == Vars.botId || (message.webhookId != null && universeWebhooks.any { it.webhook != null && it.webhook!!.id == message.webhookId })
	}
	
}

data class UniverseEntry(var webhook: Webhook?, val channel: TextChannel, var hasReported: Boolean = false)
