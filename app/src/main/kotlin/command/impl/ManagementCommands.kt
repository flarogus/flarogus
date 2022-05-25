package flarogus.command.impl

import dev.kord.common.entity.Permission
import kotlin.math.*
import dev.kord.core.entity.*
import dev.kord.core.entity.channel.TopGuildMessageChannel
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

		subtree("name", "Manage the name override for this guild. The caller must be an admin of this guild.") {
			discordOnly()
			check { m, args ->
				val channel = m?.channel as? TopGuildMessageChannel
				val permissions = channel?.getEffectivePermissions(m.author!!.id)

				if (permissions != null && Permission.Administrator in permissions) null else "you must have an 'admin' permission in order to use this."
			}

			subcommand<Unit>("set", "Set the name override") {
				arguments {
					required<String>("name", "The new name you want to assign to this guild. Up to 25 characters long.")
				}
				action {
					val name = args.arg<String>("name")
					require(name.length <= 25) { "The name cannot be longer than 25 characters! ${name.length} > 25" }

					args.arg<MultiversalGuild>("guild").nameOverride = name
				}
			}

			subaction<Unit>("reset", "Reset the name override.") {
				args.arg<MultiversalGuild>("guild").nameOverride = null
			}
		}
	}

	presetSubtree("user") {
		description = "Manage a multiversal user."

		presetArguments {
			default<MultiversalGuild>("guild", "The guild you want to manage. Defaults to current guild.") {
				val id = originalMessage?.asMessage()?.data?.guildId?.value ?: fail("Anonymous caller must specify a guild")
				Multiverse.guildOf(id) ?: fail("This guild is not valid.")
			}
		}
	}
}
