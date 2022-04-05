package flarogus.multiverse.entity

import kotlinx.serialization.*
import dev.kord.rest.builder.message.create.*
import dev.kord.common.entity.*
import dev.kord.core.entity.*
import flarogus.*
import flarogus.multiverse.*

/** 
 * Represents a guild that has a multiversal channel
 * Currently unused.
 */
@Serializable
open class MultiversalGuild(
	val discordId: Snowflake,
	val uuid: Long
) {
	@Transient
	var guild: Guild? = null

	var lastUpdate = 0L

	open suspend fun send(username: String, avatar: String, builder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit) {

	}

	open suspend fun update() {
		if (guild == null || lastUpdate + updateInterval < System.currentTimeMillis()) {
			guild = Vars.restSupplier.getGuildOrNull(discordId)
		}
	}

	companion object {
		val updateInterval = 1000L * 60 * 5
	}
}
