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
	val discordId: Snowflake
) : MultiversalEntity() {
	@Transient
	var guild: Guild? = null
	var nameOverride: String? = null

	val name: String get() = nameOverride ?: guild!!.name

	var lastUpdate = 0L

	suspend inline fun send(username: String, avatar: String, crossinline builder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit) {
		Multiverse.brodcast("$username — $name", avatar, { it.guildId != discordId }, builder)
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
