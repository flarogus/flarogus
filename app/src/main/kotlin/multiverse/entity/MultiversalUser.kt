
package flarogus.multiverse.entity

import kotlinx.serialization.*
import dev.kord.common.entity.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.entity.*
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
	var forceBanned = false

	/** NOT the name of the user! */
	var usertag: String? = null

	/** The name of the user */
	val name get() = if (usertag != null) "[$usertag] ${user?.tag}" else user?.tag ?: "null"

	@Transient
	var lastUpdate = 0L

	inline suspend fun send(guild: MultiversalGuild, crossinline builder: suspend MessageCreateBuilder.() -> Unit) {
		if (user == null) update()
		guild.send(name, user!!.getAvatarUrl()) { builder() }
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
		return !forceBanned && warningPoints < criticalWarns
	}

	companion object {
		val criticalWarns = 5
		var updateInterval = 1000L * 180L
	}
}
