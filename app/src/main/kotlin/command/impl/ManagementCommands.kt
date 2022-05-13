package flarogus.command.impl

import kotlin.math.*
import dev.kord.core.entity.*
import flarogus.*
import flarogus.command.*
import flarogus.command.builder.*
import flarogus.multiverse.*
import flarogus.multiverse.entity.*

fun TreeCommandBuilder.addManagementSubtree() = subtree("manage") {
	description = "Manage multiversal entities"

	presetSubtree("guild") {
		description = "Manage a multiversal guild."
		
		presetArguments {
			default<MultiversalGuild>("guild", "The guild you want to manage. Defaults to current guild.") {
				val id = originalMessage?.asMessage()?.data?.guildId?.value ?: fail("Anonymous caller must specify a guild")
				Multiverse.guildOf(id) ?: fail("This guild is not valid.")
			}
		}

		subaction<String>("info", "Fetch info of a multiversal guild") {
			val guild = args.arg<MultiversalGuild>("guild")
			guild.update()

			result("""
				Id: ${guild.discordId}
				Name: ${guild.name}
				Name overriden: ${guild.nameOverride != null}
				Is valid: ${guild.isValid}

				Is whitelisted: ${guild.isWhitelisted}
				Multiversal channels: ${guild.channels.map { it.id }}
				Multiversal webhooks: ${guild.webhooks.size}

				Messages received: ${guild.totalSent}
				Messages sent: ${guild.totalUserMessages}
			""".trimIndent())
		}
	}
}
