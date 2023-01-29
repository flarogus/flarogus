package flarogus.multiverse.entity

import dev.kord.common.entity.*
import dev.kord.core.entity.User
import flarogus.multiverse.entity.MultiversalUser.*
import kotlinx.coroutines.*
import kotlinx.serialization.*

/**
 * Represents a multiversal user that doesn't exist outside the multiverse.
 */
@Serializable
@SerialName("virtual")
class VirtualMultiversalUser(
	override val discordId: Snowflake,

	override val name: String,
	override val avatar: String?,
	override val isSuperuser: Boolean = false
) : MultiversalUser() {
	/** Always null. */
	override val user get() = null
	override val isModerator: Boolean get() = isSuperuser

	override val warns = ArrayList<WarnEntry>()

	/** Does nothing; always null. */
	override var usertag: String?
		get() = null
		set(value) {}
	/** Does nothing; always null. */
	override var nameOverride: String?
		get() = null
		set(value) {}

	override val flarcoinBank = BankAccount(discordId, 10)
	override val showNotifications get() = false
	
	/** Does nothing. */
	override suspend fun updateImpl() {}
}
