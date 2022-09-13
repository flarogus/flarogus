package flarogus.command.impl

import dev.kord.common.entity.*
import kotlin.math.*
import dev.kord.core.entity.*
import dev.kord.core.entity.channel.TopGuildMessageChannel
import flarogus.*
import flarogus.command.*
import flarogus.command.builder.*
import flarogus.multiverse.*
import flarogus.multiverse.entity.*
import flarogus.util.*

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

		subcommand<Unit>("nickname", "Set the nickname for this guild. Requires the user to be an admin of this guild.") {
			arguments {
				default<String>("name", "The new name you want to assign to this guild. Up to 25 characters long.") { "" }
			}
			action {
				requireAdmin()

				val name = args.arg<String>("name")
				require(name.length <= 30) { "The nickname cannot be longer than 30 characters! ${name.length} > 30" }

				args.arg<MultiversalGuild>("guild").nameOverride = name
				Log.info { "The name of guild ${args.arg<MultiversalGuild>("guild").discordId} was set to $name" }
			}
		}

		subaction<Unit>("reset-nickname", "Reset the name override.") {
			requireAdmin()
			args.arg<MultiversalGuild>("guild").nameOverride = null
		}
	}

	presetSubtree("user") {
		description = "Manage a multiversal user."

		presetArguments {
			default<MultiversalUser>("user", "The user you want to manage. Defaults to the caller.") {
				val id =
					originalMessage?.asMessage()?.author?.id
						?: fail("anonymous caller must specify a user")
				Multiverse.userOf(id) ?: fail("This user is not valid.")
			}
		}

		subcommand<Unit>(
			"nickname",
			"Set the nickname of a user. Users without moderator (or higher) permissions can only set their own nick name."
		) {
			arguments {
				default<String>("name", "The name to set this user's nickname to.") { "" }
			}

			action {
				requireModOrSelf()
				val name = args.arg<String>("name")

				require(name.length <= 30) { "The nickname must not be longer than 30 symbols." }

				args.arg<MultiversalUser>("user").nameOverride = name
				Log.info { "The name of user ${args.arg<MultiversalUser>("user").discordId} was set to $name" }
			}
		}

		subaction<Unit>("reset-nickname", "Resst the nickname of a user.") {
			requireModOrSelf()
			args.arg<MultiversalUser>("user").nameOverride = null
		}

		subcommand<Unit>("command-blacklist", "Manage the command blacklist.") {
			adminOnly()

			arguments {
				required<String>("command")
			}

			subaction<Unit>("add", "Blacklist a user from a command") {
				expect(args.arg<String>("command").startsWith("!flarogus")) { "command name must start with the prefix." }
				args.arg<MultiversalUser>("user").commandBlacklist.add(args.arg<String>("command"))
			}

			subaction("remove", "Unblacklist a user from a command") {
				args.arg<MultiversalUser>("user").commandBlacklist.remove(args.arg<String>("command")).let(::result)
			}
		}
	}
}

private suspend fun Callback<*>.requireModOrSelf() {
	expect(originalMessage != null) { "anonymous caller cannot use this command" }
	expect(originalMessage().author!!.let {
		it.isModerator() || it.id == args.arg<MultiversalUser>("user").discordId
	}) {
		"You must either manage yourself or be a multiversal moderator."
	}
}

private suspend fun Callback<*>.requireAdmin() {
	expect(originalMessage != null) { "anonymous caller cannot use this command" }
	val author = originalMessage().author!!
	expect(author.isModerator() || args.arg<MultiversalGuild>("guild").isAdmin(author.id)) {
		"You must be an admin of either this guild or the whole multiverse in order to execute this command."
	}
}

private suspend fun MultiversalGuild.isAdmin(userId: Snowflake) = channels.any {
	(it as? TopGuildMessageChannel)?.getEffectivePermissions(userId)?.contains(Permission.Administrator) ?: false
}
