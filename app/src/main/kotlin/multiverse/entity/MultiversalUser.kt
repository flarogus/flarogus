package flarogus.multiverse.entity

import dev.kord.common.entity.*
import dev.kord.core.Kord
import dev.kord.core.entity.User
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.Image
import dev.kord.rest.builder.message.create.*
import flarogus.Vars
import flarogus.multiverse.*
import flarogus.multiverse.ScamDetector
import flarogus.multiverse.state.Multimessage
import flarogus.util.*
import kotlinx.serialization.*
import java.net.URL
import java.time.Instant
import kotlin.time.ExperimentalTime

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

	val isSuperuser: Boolean get() = discordId in Vars.superusers
	val isModerator: Boolean get() = discordId in Vars.moderators || isSuperuser

	val warns = ArrayList<WarnEntry>()
	val warningPoints get() = warns.fold(0) { total: Int, warn -> total + warn.rule.points }
	/** Commands this user is not allowed to execute. */
	val commandBlacklist = ArrayList<String>()

	/** NOT the name of the user! */
	var usertag: String? = null
	var nameOverride: String? = null
		get() = if (field == null || field!!.isEmpty()) null else field

	/** The name of the user */
	val name get() = (if (usertag != null) "[$usertag] " else "") + (nameOverride ?: user?.username ?: "null") + "#" + user?.discriminator
	/** The avatar of the user */
	val avatar get() = user?.getAvatarUrl()

	var lastSent = 0L
	var totalSent = 0

	var lastReward = 0L

	/**
	 * Should be called when this user sends a multiversal message.
	 * Automatically saves the message to history.
	 *
	 * @return whether the message was retranslated.
	 */
	suspend fun onMultiversalMessage(event: MessageCreateEvent): Boolean {
		update()
		if (!canSend()) {
			event.message.replyWith(when {
				isForceBanned -> "You are banned from the Multiverse. Contact one of the admins for more info."
				warningPoints >= criticalWarns -> "You have too many warning points. You cannot send messages in the Multiverse until some of your warns expire."
				!isValid -> "Your user entry is invalid. This should be fixed automatically."
				else -> "For an unknown reason, you're not allowed to send mssages in the Multiverse. Contact the admins for more info."
			})
			return false
		} else {
			// rate limiting
			val sentAt = event.message.id.timestamp.toEpochMilliseconds()
			val delay = lastSent + messageRateLimit - sentAt
			lastSent = sentAt

			if (delay > 0) {
				val reply = event.message.replyWith("This message was not retranslated because you were rate limited. Please, wait $messageRateLimit ms.")
				scheduleMessageRemoval(10000L, reply, event.message)
				return false
			} else if (ScamDetector.hasScam(event.message.content)) {
				event.message.replyWith("[!] your message contains a potential scam. if you're not a bot, remove any links and try again")
				Log.info { "a potential scam message sent by $name was blocked: ```${event.message.content.take(200)}```" }
				return false
			}

			val guild = event.guildId?.let { Vars.multiverse.guildOf(it) }

			if (guild == null) {
				Log.info { "A user with a non-existent guild has attempted to send a message in the multiverse: `${event.message.content}`" }
			} else if (!guild.isWhitelisted) {
				event.message.replyWith("""
					This guild is not whitelisted.
					Contact the admins (e.g. by executing `!flarogus report 'pls whitelist my server thx'`) to get whitelisted.
				""".trimIndent())
			} else {
				// notifying the global message filters
				Vars.multiverse.messageFilters.find { !it.filter(this, event.message) }?.let {
					if (it.reason != null) {
						event.message.replyWith("This message was not retranslated. The reason was: ${it.reason}")
					}
					if (it.log) {
						Log.info { "Message sent by $name was filtered out (${it.reason}): ```${event.message.content}```" }
					}
					return false
				}

				// retranslating the message
				val message = send(
					guild = guild,
					filter = { it.id != event.message.channelId }
				) { channelId ->
					content = event.message.toRetranslatableContent()
					quoteMessage(event.message.referencedMessage, channelId)
					
					event.message.attachments.forEach { attachment ->
						if (attachment.isImage) {
							embed { image = attachment.url }
						} else if (attachment.size < maxFileSize) {
							addFile(attachment.filename, URL(attachment.url).openStream())
						}
					}

					if (content!!.isEmpty() && event.message.data.attachments.isEmpty()) content = "<no content>"
				}

				message.origin = event.message

				return true
			}
			return false
		}
	}
	
	/**
	 * Sends a message as if it was sent from the specified guild by this user
	 * @param guild Guild to send from
	 * @param filter Should return true if the message is to be retranslated into the providen channel
	 */
	suspend inline fun send(
		guild: MultiversalGuild,
		noinline filter: (TextChannel) -> Boolean = { true },
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
			Vars.multiverse.broadcastSystemAsync {
				content = "User $name was warned for rule ${rule.category}.${rule.index + 1}: «$rule»"
			}
		}
	}
	
	/** Updates this user */
	override suspend fun updateImpl() {
		warns.removeAll { !it.isValid() }

		if (user == null || lastUpdate + updateInterval < System.currentTimeMillis()) {
			val newuser = Vars.restSupplier.getUserOrNull(discordId)
			if (newuser != null) user = newuser

			lastUpdate = System.currentTimeMillis()
		}

		isValid = user != null
	}
	
	/** Whether this user can send multiversal messages */
	open fun canSend(): Boolean {
		return !isForceBanned && warningPoints < criticalWarns && isValid
	}

	override fun toString() = name

	companion object {
		val criticalWarns = 5
		var updateInterval = 1000L * 60 * 30
		val messageRateLimit = 3500L

		val linkRegex = """https?://([a-zA-Z0-9_\-]+?\.?)+/[a-zA-Z0-9_\-%\./]+""".toRegex()
		/**
		 * Files with size exceeding this limit will be sent in the form of links.
		 * Since i'm now hosting it on a device with horrible connection speed, it's set to 0. 
		 */
		const val maxFileSize = 1024 * 1024 * 0
	}

	/** Represents the fact that a user has violated a rule */
	@Serializable
	data class WarnEntry(val rule: Rule, val received: Long = System.currentTimeMillis()) {
		val instant: Instant get() = Instant.ofEpochMilli(received)

		val expires: Instant get() = Instant.ofEpochMilli(received + expiration)
		
		fun isValid() = received + expiration > System.currentTimeMillis()

		companion object {
			/** Time in ms required for a warn to expire. 60 days. */
			const val expiration = 1000L * 60 * 60 * 24 * 60
		}
	}

	/** Represents a dynamic message filter. */
	data class MessageFilter(
		/** When the message is declined, this (if not null) is sent as a reason for it was declined. */
		val reason: String? = null,
		/** Whether to send a log message when a message gets declined by this filter. */
		val log: Boolean = false,
		/** When this function returns false, the message is declined. */
		val filter: MultiversalUser.(Message) -> Boolean
	)
}

/** Merges the content of this message with links to its attachments. */
fun Message.toRetranslatableContent() = buildString {
	append(content.stripEveryone())
	attachments.forEach { attachment ->
		if (!attachment.isImage && attachment.size >= MultiversalUser.maxFileSize) {
			append('\n').append("[file: ${attachment.filename}](${attachment.url})")
		}
	}
}
/** Merges the content of this message with links to its attachments. */
fun DiscordPartialMessage.toRetranslatableContent() = buildString {
	append(content.value?.stripEveryone().orEmpty())
	attachments.value?.forEach { attachment ->
		if (!Image.Format.isSupported(attachment.filename) && attachment.size >= MultiversalUser.maxFileSize) {
			append('\n').append("[file: ${attachment.filename}](${attachment.url})")
		}
	}
}

