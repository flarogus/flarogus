package flarogus.multiverse

import java.io.*
import java.net.*
import kotlin.math.*
import kotlin.time.*
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
import kotlin.time.Duration.Companion.seconds

// TODO switch to the new model:
// remove remains of the old model

/**
 * Retranslates messages sent in any channel of guild network, aka Multiverse, into other multiverse channels
 */
@OptIn(ExperimentalTime::class)
object Multiverse {
	/** Array containing all messages sent in this instance */
	val history = ArrayList<Multimessage>(1000)
	
	/** Files with size exceeding this limit will be sent in the form of links */
	val maxFileSize = 1024 * 1024 * 1
	
	val webhookName = "MultiverseWebhook"
	val systemName = "Multiverse"
	val systemAvatar = "https://drive.google.com/uc?export=download&id=1Iab4m-a6VsyEnRhniT7nI4EMk_Wzjc-1"
	
	/** If false, new messages will be ignored */
	var isRunning = false
	val internalShutdownChannel = Snowflake(949196179386814515UL)
	var shutdownWebhook: Webhook? = null
	
	val npcs = mutableListOf(AmogusNPC())

	/** Currently unused. */
	val users = ArrayList<MultiversalUser>(90)
	/** Currently unused. */
	val guilds = ArrayList<MultiversalGuild>(30)

	var lastJob: Job? = null
	var jobCount = 0
	
	/** Sets up the multiverse */
	suspend fun start() {
		Log.setup()
		Settings.updateState()
		
		setupEvents()
		findChannels()
		
		Vars.client.launch {
			delay(40000L)
			val channels = guilds.fold(0) { v, it -> if (!it.isForceBanned) v + it.channels.size else v }
			brodcastSystem {
				embed { description = """
					***This channel is a part of the Multiverse. There's ${channels - 1} other channels.***
					Call `!flarogus multiverse rules` to see the rules
					Use `!flarogus report` to report an issue
					Refer to `!flarogus multiverse help` for useful commands
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
		if (!guilds.any { it.channels.any { it.id == event.message.channel.id } }) return
		if (event.message.type.let {
			it !is MessageType.Unknown 
			&& it !is MessageType.Default 
			&& it !is MessageType.Reply
		}) {
			event.message.replyWith("This message type (${event.message.type}) is not supported by the multiverse.")
			return
		}
		
		val user = userOf(event.message.data.author.id)
		user?.onMultiversalMessage(event) ?: event.message.replyWith("No user associated with your user id was found!")

		npcs.forEach { it.multiversalMessageReceived(event.message) }
	};

	private fun setupEvents() {
		Vars.client.events
			.filterIsInstance<MessageDeleteEvent>()
			.filter { isRunning }
			.filter { event -> !isRetranslatedMessage(event.messageId) }
			.filter { event -> isMultiversalChannel(event.channelId) }
			.filter { event -> event.guildId != null && guildOf(event.guildId!!).let { it != null && !it.isForceBanned } }
			.onEach { event ->
				var multimessage: Multimessage? = null
				delayUntil(20000L, 500L) { history.find { it.origin?.id == event.messageId }.also { multimessage = it } != null }

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
			.filter { event -> isMultiversalChannel(event.message.channel.id) }
			.onEach { event ->
				var multimessage: Multimessage? = null
				delayUntil(20000L, 500L) { history.find { it.origin?.id == event.messageId }.also { multimessage = it } != null }

				try {
					if (multimessage != null) {
						val origin = multimessage!!.origin?.asMessage()
						val newContent = buildString {
							appendLine(event.new.content.value ?: multimessage!!.origin!!.asMessage().content)
							origin?.attachments?.forEach { attachment ->
								if (attachment.size >= maxFileSize) {
									appendLine(attachment.url)
								}
							}
						}

						multimessage!!.edit(false) {
							content = newContent
						}

						Log.info { "Message ${multimessage!!.origin?.id} was edited by it's author" }
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
			guildOf(it.id)?.update() //this will add an entry if it didn't exist
		}
	};
	
	/** Updates everything */
	fun updateState() = Vars.client.launch {
		findChannels()
	}

	/** {@link #brodcastAsync()} except that it awaits for the result. */
	inline suspend fun brodcast(
		user: String? = null,
		avatar: String? = null,
		crossinline filter: (TextChannel) -> Boolean = { true },
		crossinline messageBuilder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
	): Multimessage = brodcastAsync(user, avatar, filter, messageBuilder).await()
	
	/**
	 * Sends a message into every multiversal channel.
	 * Accepts username and pfp url parameters.
	 * Automatically adds the multimessage into the history.
	 *
	 * @return The multimessage containing all created messages but no origin.
	 */
	inline fun brodcastAsync(
		user: String? = null,
		avatar: String? = null,
		crossinline filter: (TextChannel) -> Boolean = { true },
		crossinline messageBuilder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
	): Deferred<Multimessage> {
		val prevJob = lastJob

		return Vars.client.async {
			val messages = ArrayList<WebhookMessageBehavior>(guilds.size * 2)
			val deferreds = ArrayList<Job>(guilds.size * 2)

			jobCount++
			prevJob?.join() //wait for the completeion of that coroutine
			
			try {
				withTimeout(45.seconds) {
					guilds.forEach {
						if (!it.isValid) return@forEach

						try {
							deferreds.add(Vars.client.launch {
								it.send(
									username = user,
									avatar = avatar,
									filter = filter,
									handler = { m, w -> messages.add(WebhookMessageBehavior(w, m)) },
									builder = messageBuilder
								)
							})
						} catch (e: Exception) {
							Log.error { "Exception while retranslating a message into ${it.name}: $e" }
						}

						yield()
					}
					
					deferreds.forEach { def ->
						def.join()
					}

					val multimessage = Multimessage(null, messages)
					Multiverse.history.add(multimessage)
					
					multimessage
				}
			} catch (e: TimeoutCancellationException) {
				println(e) // we don't need to log it
				throw e
			} finally {
				jobCount--
			}
		}.also {
			lastJob = it
		}
	}

	/** @see #brodcastSystemAsync() */
	inline suspend fun brodcastSystem(
		crossinline message: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
	) = brodcastSystemAsync(message).await()

	/** {@link #brodcastAsync()} but uses system pfp & name */
	inline fun brodcastSystemAsync(
		crossinline message: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
	) = brodcastAsync(systemName, systemAvatar, { true }, message)
	
	/** Returns a MultiversalUser with the given id, or null if it does not exist */
	suspend fun userOf(id: Snowflake): MultiversalUser? = users.find { it.discordId == id } ?: let {
		MultiversalUser(id).also { it.update() }.let {
			if (it.isValid) it.also { users.add(it) } else null
		}
	}

	/** Returns a MultiversalGuild with the given id, or null if it does not exist */
	suspend fun guildOf(id: Snowflake): MultiversalGuild? = guilds.find { it.discordId == id } ?: let {
		MultiversalGuild(id).also { it.update() }.let { 
			if (it.isValid) it.also { guilds.add(it) } else null
		}
	}

	/** Returns whether this message was sent by flarogus */
	fun isOwnMessage(message: Message): Boolean {
		return (message.author?.id == Vars.botId)
		|| (message.webhookId != null && isMultiversalWebhook(message.webhookId!!))
	}

	/** Returns whether this channel belongs to the multiverse. Does not guarantee that it is not banned. */
	fun isMultiversalChannel(channel: Snowflake) = guilds.any { it.channels.any { it.id == channel } }

	/** Returns whether this webhook belongs to the multiverse. */
	fun isMultiversalWebhook(webhook: Snowflake) = guilds.any { it.webhooks.any { it.id == webhook } }

	/** Returns whether a message with this id is a retranslated message */
	fun isRetranslatedMessage(id: Snowflake) = history.any { it.retranslated.any { it.id == id } }
}

