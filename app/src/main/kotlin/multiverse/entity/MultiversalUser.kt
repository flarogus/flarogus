package flarogus.multiverse.entity

import java.net.*
import java.time.*
import kotlin.time.*
import kotlinx.serialization.*
import kotlinx.coroutines.*
import dev.kord.common.entity.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.entity.*
import dev.kord.core.entity.channel.*
import dev.kord.core.event.message.*
import flarogus.*
import flarogus.util.*
import flarogus.multiverse.*
import flarogus.multiverse.state.*

/** 
 * Represents a user that has ever interacted with the Multiverse 
 * Currently unused.
 */
@Serializable
@OptIn(ExperimentalTime::class)
open class MultiversalUser(
	val discordId: Snowflake
) : MultiversalEntity() {
	@Transient
	open var user: User? = null

	val warns = ArrayList<WarnEntry>()
	val warningPoints get() = warns.fold(0) { total: Int, warn -> total + warn.rule.points }

	/** NOT the name of the user! */
	var usertag: String? = null

	/** The name of the user */
	val name get() = if (usertag != null) "[$usertag] ${user?.tag}" else user?.tag ?: "null"
	/** The avatar of the user */
	val avatar get() = user?.getAvatarUrl()

	@Transient
	var lastUpdate = 0L
	var lastSent = 0L
	var totalSent = 0

	var lastReward = 0L

	/** Should be called when this user sends a multiversal message. Automatically saves the message to history. */
	suspend fun onMultiversalMessage(event: MessageCreateEvent) {
		update()
		if (!canSend()) {
			event.message.replyWith(when {
				isForceBanned -> "You are banned from the Multiverse. Contact one of the admins for more info."
				warningPoints > criticalWarns -> "You have too many warnings. You cannot send messages in the Multiverse."
				else -> "For an unknown reason, you're not allowed to send mssages in the Multiverse. Contact the admins for more info."
			})
		} else {
			val delay = lastSent + messageRateLimit - System.currentTimeMillis()
			lastSent = System.currentTimeMillis()

			if (delay > 0) {
				event.message.replyWith("This message was not retranslated because was rate limited. Please, wait $messageRateLimit ms.")
				return
			} else if (ScamDetector.hasScam(event.message.content)) {
				event.message.replyWith("[!] your message contains a potential scam. if you're not a bot, remove any links and try again")
				Log.info { "a potential scam message sent by ${event.message.author?.tag} was blocked: ```${event.message.content.take(200)}```" }
			}

			val guild = event.guildId?.let { Multiverse.guildOf(it) }

			if (guild == null) {
				Log.info { "A user with a non-existent guild has attempted to send a message in the multiverse: `${event.message.content}`" }
			} else if (!guild.isWhitelisted) {
				event.message.replyWith("This guild is not whitelisted. Contact the admins for more info")
			} else {
				val message = send(
					guild = guild,
					filter = { it.id != event.message.channelId }
				) { channelId ->
					content = buildString {
						append(event.message.content.stripEveryone())
						event.message.attachments.forEach { attachment ->
							if (!attachment.isImage && attachment.size >= Multiverse.maxFileSize) {
								append('\n').append(attachment.url)
							}
						}
					}

					quoteMessage(event.message.referencedMessage, channelId)
					
					event.message.attachments.forEach { attachment ->
						if (attachment.isImage) {
							embed { image = attachment.url }
						} else if (attachment.size < Multiverse.maxFileSize) {
							addFile(attachment.filename, URL(attachment.url).openStream())
						}
					}

					if (content!!.isEmpty() && event.message.data.attachments.isEmpty()) content = "<no content>"
				}

				message.origin = event.message
			}
		}
	}
	
	/**
	 * Sends a message as if it was sent from the specified guild by this user
	 * @param guild Guild to send from
	 * @param filter Should return true if the message is to be retranslated into the providen channel
	 */
	inline suspend fun send(
		guild: MultiversalGuild,
		crossinline filter: (TextChannel) -> Boolean = { true },
		crossinline builder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
	): Multimessage {
		update()
		return guild.retranslateUserMessage(this, filter) {
			builder(it)
			content = content?.revealHypertext()?.explicitMentions()
		}.also { totalSent++ }
	}

	/** Warns this user for a rule. */
	open fun warnFor(rule: Rule, informMultiverse: Boolean) {
		if (rule.points <= 0) return;
		warns.add(WarnEntry(rule, System.currentTimeMillis()))

		if (informMultiverse) {
			Multiverse.brodcastSystemAsync {
				content = "User $name was warned for rule ${rule.category}.${rule.index}: «$rule»"
			}
		}
	}
	
	/** Updates this user */
	override open suspend fun updateImpl() {
		warns.removeAll { !it.isValid() }

		if (user == null || lastUpdate + updateInterval < System.currentTimeMillis()) {
			val newuser = Vars.restSupplier.getUserOrNull(discordId)
			if (newuser != null) user = newuser
		}

		isValid = user != null
	}
	
	/** Whether this user can send multiversal messages */
	open fun canSend(): Boolean {
		return !isForceBanned && warningPoints < criticalWarns
	}

	companion object {
		val criticalWarns = 5
		var updateInterval = 1000L * 60 * 8
		val messageRateLimit = 3000L
	}

	/** Represents the fact that a user has violated a rule */
	@Serializable
	data class WarnEntry(val rule: Rule, val received: Long = System.currentTimeMillis()) {
		val instant: Instant get() = Instant.ofEpochMilli(received)

		val expires: Instant get() = Instant.ofEpochMilli(received + expiration)
		
		fun isValid() = received + expiration < System.currentTimeMillis()

		companion object {
			/** Time in ms required for a warn to expire. 20 days. */
			val expiration = 1000L * 60 * 60 * 24 * 20
		}
	}
}
