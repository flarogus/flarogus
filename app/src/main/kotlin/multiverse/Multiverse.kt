package flarogus.multiverse

import java.io.*
import java.net.*
import kotlin.math.*
import kotlin.random.*
import kotlin.concurrent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import dev.kord.common.entity.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.*
import dev.kord.core.event.message.*
import dev.kord.core.supplier.*
import dev.kord.core.entity.*
import dev.kord.core.entity.channel.*
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import flarogus.*
import flarogus.util.*
import flarogus.multiverse.state.*
import flarogus.multiverse.npc.impl.*
import flarogus.multiverse.entity.*

/**
 * Retranslates messages sent in any channel of guild network, aka Multiverse, into other multiverse channels
 */
object Multiverse {

	/** All channels the multiverse works in */
	val universes = ArrayList<TextChannel>(50)
	/** All webhooks multiverse retranslates to */
	val universeWebhooks = ArrayList<UniverseEntry>(50)
	
	/** Rate limitation map */
	val ratelimited = HashMap<Snowflake, Long>(150)
	val ratelimit = 2000L
	
	/** Array containing all messages sent in this instance */
	val history = ArrayList<Multimessage>(1000)
	
	/** Files with size exceeding this limit will be sent in the form of links */
	val maxFileSize = 1024 * 1024 * 3 - 1024
	
	val webhookName = "MultiverseWebhook"
	val systemName = "Multiverse"
	val systemAvatar = "https://drive.google.com/uc?export=download&id=197qxkXH2_b0nZyO6XzMC8VeYTuYwcai9"
	
	/** If false, new messages will be ignored */
	var isRunning = false
	val internalShutdownChannel = Snowflake(949196179386814515UL)
	var shutdownWebhook: Webhook? = null
	
	val npcs = mutableListOf(AmogusNPC())

	/** Currently unused. */
	val users = ArrayList<MultiversalUser>(90)
	/** Currently unused. */
	val guilds = ArrayList<MultiversalGuild>(30)
	
	/** Sets up the multiverse */
	suspend fun start() {
		Settings.updateState()
		
		//send a shutdown message to forcefully stop other instances
		try {
			val sc = Vars.supplier.getChannelOrNull(internalShutdownChannel) as? TextChannel
			sc?.webhooks?.collect {
				if (it.name == webhookName) shutdownWebhook = it
			}
			if (shutdownWebhook == null) shutdownWebhook = sc?.createWebhook(webhookName)
			
			shutdownWebhook?.execute(shutdownWebhook!!.token!!) {
				content = Vars.startedAt.toString()
			}
		} catch (e: Exception) {
			Log.error { "FAILED TO SEND SHUTDOWN MESSAGE: $e" }
		}
		
		//listen for shutdown messages
		Vars.client.events
			.filterIsInstance<MessageCreateEvent>()
			.filter { it.message.channelId == internalShutdownChannel }
			.filter { it.message.data.webhookId.value != null && it.message.data.webhookId.value == shutdownWebhook?.id }
			.onEach {
				try {
					if (it.message.content.toLong() > Vars.startedAt) {
						shutdown()
						Log.info { "multiverse instance ${Vars.ubid} is shutting down (a newer instance has sent a shutdown message)" }
						Vars.client.launch { delay(5000L); Vars.client.shutdown() }
					}
				} catch (e: NumberFormatException) {
					Log.error { "a shutdown message was received but it's content could not be readen: $e" }
				}
			}.launchIn(Vars.client)

		setupEvents()
		
		findChannels()
		
		Vars.client.launch {
			delay(40000L)
			brodcastSystem { _ ->
				val channels = universeWebhooks.fold(0) { v, it -> if (it.webhook != null && Lists.canReceive(it.channel)) v + 1 else v }
				embed { description = """
					***This channel is a part of the Multiverse. There's ${channels - 1} other channels.***
					Call `flarogus multiverse rules` to see the rules
					Use `flarogus report` to report an issue
					Refer to `flarogus multiverse help` for useful commands
				""".trimIndent() }
			}
		}
		
		isRunning = true
		
		fixedRateTimer("update state", true, initialDelay = 5 * 1000L, period = 180 * 1000L) { updateState() }
		fixedRateTimer("update settings", true, initialDelay = 5 * 1000L, period = 20 * 1000L) {
			//random delay is to ensure that there will never be situations when two instances can't detect each other
			Vars.client.launch {
				delay(Random.nextLong(0L, 5000L))
				Settings.updateState()
			}
		}
	}
	
	/** Shuts the multiverse down */
	fun shutdown() {
		isRunning = false
	}
	
	suspend fun messageReceived(event: MessageCreateEvent) {
		if (!isRunning || isOwnMessage(event.message)) return
		if (!universes.any { event.message.channel.id == it.id }) return
		
		val userid = event.message.author?.id
		val guild = event.getGuild()
		
		if (!Lists.canTransmit(guild, event.message.author)) {
			event.message.replyWith("""
				[!] You're not allowed to send messages in multiverse. Please contact one of admins to find out why.
				You can do that using `flarogus report`.
			""".trimIndent())
			Log.info { "${event.message.author?.tag}'s multiversal message was not retranslated: `${event.message.content.take(200)}`" }
			return
		}
		
		try {
			//instaban spammers
			if (countPings(event.message.content) > 7) {
				Lists.blacklist += event.message.author!!.id //we don't need to ban permanently
				event.message.replyWith("[!] You've been auto-banned from this multiverse instance. please wait 'till the next restart.")
				Log.info { "${event.message.author?.tag} was auto-tempbanned for attempting to ping too many people at once" }
				return
			}
			
			//block potential spam messages
			if (ScamDetector.hasScam(event.message.content)) {
				event.message.replyWith("[!] your message contains a potential scam. if you're not a bot, remove any links and try again")
				Log.info { "a potential scam message sent by ${event.message.author?.tag} was blocked: ```${event.message.content.take(200)}```" }
				return
			}
			
			//rate limitation
			if (userid != null) {
				val current = System.currentTimeMillis()
				val lastMessage = ratelimited.getOrDefault(userid, 0L)
				
				if (current - lastMessage < ratelimit) {
					Vars.client.launch {
						event.message.replyWith("[!] You are being rate limited. Please wait ${ratelimit + lastMessage - current} milliseconds.")
					}
					return
				} else {
					ratelimited[userid] = current
				}
			}
			
			//actual retranslation region
			Vars.client.launch {
				val original = event.message.content
				val isWebhook = event.message.data.webhookId.value == event.message.data.author.id
				val author = User(event.message.data.author, Vars.client)
				val customTag = Lists.usertags.getOrDefault(userid, null)
				
				val username = buildString {
					if (customTag != null) {
						append('[')
						append(customTag)
						append(']')
						append(' ')
					}
					if (isWebhook) append("webhook<")
					append(author.tag)
					append(" — ")
					append(guild?.name ?: "<DISCORD>")
					if (isWebhook) append(">")
				}
				
				var finalMessage = buildString {
					append(original)
					
					event.message.data.attachments.forEach { attachment ->
						if (attachment.size >= maxFileSize) {
							append('\n').append(attachment.url)
						}
					}
				}.take(1999)
				
				if (finalMessage.isEmpty() && event.message.data.attachments.isEmpty()) {
					finalMessage = "<no content>"
				}
				
				val beginTime = System.currentTimeMillis()
				
				//actually brodcast
				val messages = brodcast(username, author.getAvatarUrl(), { it.id != event.message.channel.id }) { channelId ->
					var finalContent = finalMessage
					
					quoteMessage(event.message.referencedMessage, channelId)
					
					event.message.data.attachments.forEach { attachment ->
						if (attachment.size < maxFileSize) {
							addFile(attachment.filename, URL(attachment.url).openStream())
						}
					}
					
					content = finalContent
				}
				
				//save to history
				val multimessage = Multimessage(MessageBehavior(event.message.channelId, event.message.id, event.message.kord), messages)
				history.add(multimessage)
				
				//notify npcs
				npcs.forEach { it.multiversalMessageReceived(event.message) }
				
				val time = System.currentTimeMillis() - beginTime
				Log.lifecycle { "$author's multiversal message was retranslated in $time ms." }
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
	};

	private fun setupEvents() {
		Vars.client.events
			.filterIsInstance<MessageDeleteEvent>()
			.filter { isRunning }
			.filter { event -> !isRetranslatedMessage(event.messageId) }
			.filter { event -> event.guildId in Lists.whitelist }
			.filter { event -> universes.any { it.id == event.channelId } }
			.onEach { event ->
				var multimessage: Multimessage? = null
				delayWhile(20000L, 500L) { history.find { it.origin.id == event.messageId }.also { multimessage = it } != null }

				try {
					if (multimessage != null) {
						multimessage!!.delete(false) //well...
						Log.info { "Message ${event.messageId} was deleted by deleting the original message" }
						history.remove(multimessage!!)
					}
				} catch (e: Exception) {
					Log.error { "An exception has occurred while trying to delete a multiversal message ${event.messageId}: $e" }
				}
			}
			.launchIn(Vars.client)
		
		//can't check the guild here
		Vars.client.events
			.filterIsInstance<MessageUpdateEvent>()
			.filter { isRunning }
			.filter { event -> !isRetranslatedMessage(event.messageId) }
			.filter { event -> universes.any { it.id == event.channelId } }
			.onEach { event ->
				var multimessage: Multimessage? = null
				delayWhile(20000L, 500L) { history.find { it.origin.id == event.messageId }.also { multimessage = it } != null }

				try {
					if (multimessage != null) {
						multimessage!!.edit(false) {
							content = buildString {
								appendLine(event.new.content.value ?: "")
								event.new.attachments.value?.forEach { attachment ->
									if (attachment.size >= maxFileSize) {
										appendLine(attachment.url)
									}
								}
							}
						}
					}
				} catch (e: Exception) {
					Log.error { "An exception has occurred while trying to edit a multiversal message ${event.messageId}: $e" }
				}
			}
			.launchIn(Vars.client)
	}
	
	/** Searches for channels with "multiverse" in their names in all guilds this bot is in */
	suspend fun findChannels() {
		Vars.client.rest.user.getCurrentUserGuilds().forEach {
			//the following methods are way too costly to invoke them for every guild
			if (universes.any { ch -> ch.data.guildId.value == it.id }) return@forEach

			val guild = Vars.restSupplier.getGuildOrNull(it.id) //gCUG() returns a flow of partial discord guilds.
			
			if (guild != null && it.id !in Lists.blacklist && guild.id !in Lists.blacklist) guild.channels.collect {
				var c = it as? TextChannel

				if (c != null && c.data.name.toString().contains("multiverse") && c.id !in Lists.blacklist) {
					if (!universes.any { it.id.value == c.id.value }) {
						universes += c
					}
				}
			}
		}
		
		//acquire webhooks for these channels
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
					val reason = if (e.toString().contains("Missing Permission")) "missing 'MANAGE_WEBHOOKS' permission!" else e.toString()
					entry.hasReported = true
					
					try {
						entry.channel.createEmbed { description = """
							[ERROR] Could not acquire a webhook: $reason
							----------
							Webhookless communication is deprecated and __IS NO LONGER SUPPORTED__.
							Contact the server's staff or allow the bot to manage webhooks yourself.
						""".trimIndent() }
					} catch (e: Exception) {}
				}
			}	
		}
	};
	
	/** Updates everything */
	fun updateState() = Vars.client.launch {
		findChannels()
	}
	
	/** Same as normal brodcast but uses system pfp & name */
	inline suspend fun brodcastSystem(
		crossinline message: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
	) = brodcast(systemName, systemAvatar, { true }, message)
	
	/**
	 * Sends a message into every multiverse channel except the ones that are blacklisted and the one sith id == exclude
	 * Accepts username and pfp url parameters
	 *
	 * @return array containing ids of all created messages
	 **/
	inline suspend fun brodcast(
		user: String? = null,
		avatar: String? = null,
		filter: (TextChannel) -> Boolean = { true },
		crossinline messageBuilder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
	): List<WebhookMessageBehavior> {
		val messages = ArrayList<WebhookMessageBehavior>(universeWebhooks.size)
		val deferreds = arrayOfNulls<Deferred<WebhookMessageBehavior?>>(universeWebhooks.size) //todo: can i avoid this array allocation?
		
		universeWebhooks.forEachIndexed { index, it ->
			if (filter(it.channel) && Lists.canReceive(it.channel)) {
				deferreds[index] = Vars.client.async {
					try {
						if (it.webhook != null) {
							val message = it.webhook!!.execute(it.webhook!!.token!!) {
								messageBuilder(it.channel.id)
								content = content?.take(1999)?.stripEveryone()
								allowedMentions() //forbid all mentions
								username = user ?: "unknown user"
								avatarUrl = avatar
							}
							
							return@async WebhookMessageBehavior(it.webhook!!, message)
						}
					} catch (e: Exception) {
						Log.error { "failed to retranslate a message into ${it.channel.id}: $e" }
						
						if (e.toString().contains("404")) it.webhook = null; //invalid webhook
					}
					
					null
				}
			}
		}
		
		deferreds.forEach { def ->
			def?.await()?.let { messages.add(it) }
		}
		
		return messages
	}
	
	/** Returns a MultiversalUser with the given id, or null if it does not exist */
	fun userOf(id: Snowflake) = users.find { it.discordId == id }

	/** Returns a MultiversalGuild with the given id, or null if it does not exist */
	fun guildOf(id: Snowflake) = guilds.find { it.discordId == id }

	/** Returns whether this message was sent by flarogus */
	fun isOwnMessage(message: Message): Boolean {
		return message.author?.id?.value == Vars.botId || (message.webhookId != null && universeWebhooks.any { it.webhook != null && it.webhook!!.id == message.webhookId })
	}

	/** Returns whether a message with this id is a retranslated message */
	fun isRetranslatedMessage(id: Snowflake) = history.any { it.retranslated.any { it.id == id } }
	
}
