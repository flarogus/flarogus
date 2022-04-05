
package flarogus.multiverse.entity

import java.net.*
import kotlinx.serialization.*
import dev.kord.common.entity.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.entity.*
import dev.kord.core.event.message.*
import flarogus.*
import flarogus.util.*
import flarogus.multiverse.*

/** 
 * Represents a user that has ever interacted with the Multiverse 
 * Currently unused.
 */
@Serializable
open class MultiversalUser(
	val discordId: Snowflake,
	val uuid: Long
) {
	@Transient
	open var user: User? = null

	val warns = ArrayList<WarnEntry>()
	val warningPoints get() = warns.fold(0) { total: Int, warn -> total + warn.rule.points }
	/** If true, this user was forcedly banned by an admin */
	var isForceBanned = false

	/** NOT the name of the user! */
	var usertag: String? = null

	/** The name of the user */
	val name get() = if (usertag != null) "[$usertag] ${user?.tag}" else user?.tag ?: "null"

	@Transient
	var lastUpdate = 0L
	var lastSent = 0L

	/** Should be called when this user sends a multiversal message */
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
				event.message.replyWith("This message was not retranslated because was rate limited. Please, wait $delay ms.")
				return
			} else if (ScamDetector.hasScam(event.message.content)) {
				event.message.replyWith("[!] your message contains a potential scam. if you're not a bot, remove any links and try again")
				Log.info { "a potential scam message sent by ${event.message.author?.tag} was blocked: ```${event.message.content.take(200)}```" }
			}

			val guild = event.guildId?.let { Multiverse.guildOf(it) }

			if (guild == null) {
				Log.info { "A user with a non-existent guild has attempted to send a message in the multiverse: `${event.message.content}`" }
			} else {
				send(guild) { channelId ->
					content = buildString {
						append(event.message.content.stripEveryone())
						event.message.data.attachments.forEach { attachment ->
							if (attachment.size >= Multiverse.maxFileSize) {
								append('\n').append(attachment.url)
							}
						}
					}

					quoteMessage(event.message.referencedMessage, channelId)
					
					event.message.data.attachments.forEach { attachment ->
						if (attachment.size < Multiverse.maxFileSize) {
							addFile(attachment.filename, URL(attachment.url).openStream())
						}
					}

					if (content!!.isEmpty() && event.message.data.attachments.isEmpty()) content = "<no content>"
				}
			}
		}
	}

	inline suspend fun send(guild: MultiversalGuild, crossinline builder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit) {
		update()
		guild.send(name, user!!.getAvatarUrl()) { builder(it) }
	}
	
	/** Updates this user */
	open suspend fun update() {
		warns.removeAll { !it.isValid() }

		if (user == null || lastUpdate + updateInterval < System.currentTimeMillis()) {
			user = Vars.restSupplier.getUserOrNull(discordId)
		}
	}
	
	/** Whether this user can send multiversal messages */
	open fun canSend(): Boolean {
		return !isForceBanned && warningPoints < criticalWarns
	}

	companion object {
		val criticalWarns = 5
		var updateInterval = 1000L * 180
		val messageRateLimit = 3000L
	}
}
