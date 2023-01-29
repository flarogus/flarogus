package flarogus.multiverse.entity

import dev.kord.common.entity.*
import dev.kord.core.entity.User
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.Image
import dev.kord.rest.builder.message.create.*
import flarogus.Vars
import flarogus.multiverse.*
import flarogus.multiverse.ScamDetector
import flarogus.multiverse.entity.MultiversalUser.*
import flarogus.multiverse.state.Multimessage
import flarogus.multiverse.state.TransactionSerializer
import flarogus.util.*
import kotlinx.serialization.*
import java.net.URL
import kotlin.time.ExperimentalTime
import kotlinx.datetime.*

/** 
 * Represents a real user wno has ever interacted with the Multiverse.
 *
 * Serial names of this class' properties are made short to save memory space.
 */
@Serializable
@SerialName("real")
class RealMultiversalUser(
	@SerialName("id")
	override val discordId: Snowflake
) : MultiversalUser() {
	@Transient
	override var user: User? = null

	override val isSuperuser: Boolean get() = discordId in Vars.superusers
	override val isModerator: Boolean get() = discordId in Vars.moderators || isSuperuser

	override val warns = ArrayList<WarnEntry>()

	/** A prefix before the name. This is NOT the name of the user! */
	@SerialName("ut")
	var usertag: String? = null
	/** Overrides [user.username]. */
	@SerialName("no")
	var nameOverride: String? = null
		get() = field?.takeIf { it.isNotEmpty() }

	override val name get() =
		usertag?.let { "[$it] " }.orEmpty() +
		(nameOverride ?: user?.username ?: "<invalid user>") +
		"#" + user?.discriminator
	override val avatar get() = user?.getAvatarUrl()

	@SerialName("fb")
	override val flarcoinBank = BankAccount(discordId, 10)
	@SerialName("sn")
	override var showNotifications = true
	/** The last time this user received a daily reward. */
	@SerialName("lr")
	var lastReward = 0L

	/** Commands this user is not allowed to execute. */
	@SerialName("cb")
	val commandBlacklist = ArrayList<String>()
	
	override suspend fun updateImpl() {
		warns.removeAll { !it.isValid() }

		if (user == null || lastUpdate + updateInterval < System.currentTimeMillis()) {
			val newuser = Vars.restSupplier.getUserOrNull(discordId)
			if (newuser != null) user = newuser

			lastUpdate = System.currentTimeMillis()
		}

		isValid = user != null
	}
}

fun MultiversalUser.asReal(): RealMultiversalUser = run {
	require(this is RealMultiversalUser) { "$this is not a real multiversal user." }
	this
}

