package flarogus.multiverse.entity

import kotlin.time.*
import kotlinx.serialization.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import dev.kord.rest.builder.message.create.*
import dev.kord.common.entity.*
import dev.kord.core.entity.*
import dev.kord.core.entity.channel.*
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import flarogus.*
import flarogus.util.*
import flarogus.multiverse.*
import flarogus.multiverse.state.*

/** 
 * Represents a guild that has a multiversal channel
 * Currently unused.
 */
@Serializable
@OptIn(ExperimentalTime::class)
open class MultiversalGuild(
	val discordId: Snowflake
) : MultiversalEntity() {
	@Transient
	var guild: Guild? = null
	@Transient
	val channels = HashSet<TextChannel>()
	@Transient
	val webhooks = HashSet<Webhook>()

	var nameOverride: String? = null
	val name: String get() = nameOverride ?: guild?.name ?: "unknown guild"
	
	@Transient
	var lastUpdate = 0L
	var totalSent = 0
	var totalUserMessages = 0

	/** Whether this guild is allowed to participate in the multiverse */
	var isWhitelisted = false

	/** 
	 * Sends a message into every channels of this guild, optionally invoking a function on every message sent
	 * @param filter filters channels. should return [true] if the message is to be retranslated into the channel
	 * @param handler invoked whenever a message is being sent.
	 * @param builder builds a message. called whenever a message is being created. Receives the id of the channel a message is being sent to.
	 */
	suspend inline fun send(
		username: String?,
		avatar: String?,
		crossinline filter: (TextChannel) -> Boolean = { true },
		crossinline handler: (Message, Webhook) -> Unit = { _, _ -> },
		crossinline builder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
	) {
		update()
		if (!isWhitelisted || !isValid) return

		webhooks.forEach { webhook ->
			try {
				val channel = channels.find { it.id == webhook.channelId } 
				if (channel == null || !filter(channel)) return@forEach

				webhook.execute(webhook.token!!) {
					builder(webhook.channelId)
					content = content?.take(1999)?.stripEveryone()
					allowedMentions() //forbid all mentions
					this.username = username
					avatarUrl = avatar
				}.also { handler(it, webhook) }
			} catch (e: Exception) {
				webhooks.remove(webhook)
				channels.removeAll { it.id == webhook.channelId }
				lastUpdate = 0L // force next uldate
				Log.error { "An exception has occurred while transmitting a message to '$name': '$e'. Webhook and channel invalidated." }
			}
		}
		totalSent++
	}
	
	/** 
	 * Retranslates a message sent by the specified MultiversalUser into every multiversal guild. This method delegates to the Multiverse object.
	 * @see Multiverse#brodcast
	 * @see MultiversalGuild#send
	 */
	suspend inline fun retranslateUserMessage(
		user: MultiversalUser,
		crossinline filter: (TextChannel) -> Boolean = { true },
		crossinline builder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
	): Multimessage {
		if (!isWhitelisted) throw IllegalAccessException("this guild is not whitelisted")
		totalUserMessages++

		return Multiverse.brodcast("${user.name} — $name", user.avatar, filter) {
			builder(it)
			content = content?.stripEveryone()?.revealHypertext()
		}
	}

	override open suspend fun updateImpl() {
		if (guild == null || lastUpdate + updateInterval < System.currentTimeMillis()) {
			guild = Vars.restSupplier.getGuildOrNull(discordId)

			guild?.channels?.collect {
				if (it !is TopGuildMessageChannel || !isValidChannel(it)) return@collect

				channels.add(it)
				
				try {
					val webhook = it.webhooks.firstOrNull { it.name == webhookName && it.token != null } ?: it.createWebhook(webhookName)
					webhooks.add(webhook)
				} catch (e: Exception) {
					Log.error { "couldn't acquire a webhook for ${it.name} (${it.id}}: $e" }
					channels.remove(it)

					try { 
						it.createMessage("Failed to acquire a webhook for this channel. Next attempt in 10 minutes.") 
					} catch (e: Exception) {}
				}
			}
		}

		isValid = guild != null // well...

		// remove all invalid webhooks
		webhooks.removeAll { webhook ->
			!channels.any { it.id == webhook.channelId }
		}
	}

	
	companion object {
		val updateInterval = 1000L * 60 * 10
		val webhookName = "MultiverseWebhook"

		/** Checks if this channel is a valid multiversal channel */
		fun isValidChannel(channel: TopGuildMessageChannel): Boolean {
			val perms = it.getEffectivePermissions(Vars.botId)

			return channel.name.contains(Channels.multiverseChannelName, true)
				&& Permission.ViewChannel in perms
				&& Permission.SendMessages in perms
				&& Permission.ManageWebhooks in perms
		}
	}
}
