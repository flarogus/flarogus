package flarogus.command.impl

import kotlin.math.*
import flarogus.command.*
import flarogus.command.builder.*
import flarogus.multiverse.*
import flarogus.multiverse.entity.*

fun TreeCommandBuilder.addAdminSubtree() = subtree("admin") {
	modOnly()

	description = "Mod/admin-only commands that allow to manage the multiverse"

	presetSubtree("listguilds") {
		adminOnly()

		description = "List multiversal guilds."
		
		fun Callback<String>.list(predicate: (MultiversalGuild) -> Boolean) {
			result(Multiverse.guilds.filter(predicate).sortedByDescending { it.discordId.value }.map {
				"${it.discordId} — ${it.name}"
			}.joinToString("\n"))
		}

		subaction<String>("all") {
			list { true && it.isValid }
		}
		subaction<String>("unwhitelisted") { 
			list { !it.isWhitelisted && it.isValid }
		}
		subaction<String>("whitelisted") {
			list { it.isWhitelisted && it.isValid }
		}
		subaction<String>("blacklisted") {
			list { it.isForceBanned && it.isValid }
		}
		subaction<String>("invalid") {
			list { !it.isValid }
		}
	}

	presetSubtree("blacklist") {
		adminOnly()

		description = "Manage the blacklist"

		presetArguments {
			required<MultiversalGuild>("guild", "Multiversal guild you want to manage")
		}

		subaction<Unit>("add", "Blacklist a guild") {
			args.arg<MultiversalGuild>("guild").isForceBanned = true
		}

		subaction<Unit>("remove", "Unblacklist a guild") {
			args.arg<MultiversalGuild>("guild").isForceBanned = false
		}

		subaction<Boolean>("check", "Check if a guild is banned") {
			args.arg<MultiversalGuild>("guild").isForceBanned.let {
				result(it, false)
				reply("This guild is " + if (it) "banned" else "not banned")
			}
		}
	}

	presetSubtree("whitelist") {
		description = "Manage the whitelist"

		presetArguments {
			required<MultiversalGuild>("guild", "Multiversal guild you want to manage")
		}

		subaction<Unit>("add", "Whitelist a guild.") {
			args.arg<MultiversalGuild>("guild").isWhitelisted = true
		}

		subaction<Unit>("remove", "Unwhitelist a guild.") {
			args.arg<MultiversalGuild>("guild").isWhitelisted = false
		}

		subaction<Boolean>("check", "Check if a guild is whitelisted.") {
			args.arg<MultiversalGuild>("guild").isForceBanned.let {
				result(it, false)
				reply("This guild is " + if (it) "whitelisted" else "not whitelisted")
			}
		}
	}

	presetSubtree("tag") {
		description = "Manage the tag of a user."

		presetArguments {
			required<MultiversalUser>("user", "The user whose usertag you want to manage.")
		}

		subcommand<Unit>("set") {
			description = "set the usertag of the user"

			arguments {
				required<String>("tag")
			}

			action {
				args.arg<MultiversalUser>("user").usertag = args.arg<String>("tag")
			}
		}

		subaction<Unit>("clear", "Clear the usertag of the user.") {
			args.arg<MultiversalUser>("user").usertag = null
		}

		subaction<String>("check", "Fetch the usertag of the user.") {
			result(args.arg<MultiversalUser>("user").usertag)
		}
	}

	presetSubtree("warn") {
		description = "Manage warnings of users."

		presetArguments {
			required<MultiversalUser>("user", "The user whose warnings you want to manage.")
		}

		subcommand<Unit>("add", "Warn a user.") {
			arguments {
				required<Int>("number", "The number of the rule you want to warn the user for")
				default<Int>("category", "Rule category to which the rule belongs. 1 (general) by default.") {
					1
				}
			}

			action {
				val rule = RuleCategory.of(args.arg<Int>("category"), args.arg<Int>("number") - 1)
				require(rule != null) { "Rule ${args.arg<Int>("category")}.${args.arg<Int>("number")} does not exist." }

				args.arg<MultiversalUser>("user").warnFor(rule, true)
			}
		}

		val checkCommand by lazy { FlarogusCommand.find("!flarogus multiverse warnings") as FlarogusCommand<Int> }

		subaction<Int>("check", "Check warnings of the user. This command delegates to the 'multiverse warnings' command.") {
			val arg = args.arg<MultiversalUser>("user").discordId.toString()

			result(if (originalMessage != null) {
				checkCommand(originalMessage.asMessage(), arg).result
			} else {
				checkCommand(arg)
			})
		}

		subaction<Unit>("clear", "Clear warnings of the user") {
			val user = args.arg<MultiversalUser>("user")
			user.warns.clear()

			Multiverse.brodcastSystem { content = "User ${user.name} just had their warns cleared." }
		}
	}

	subcommand<Boolean>("echo", "Send a system message in the multiverse") {
		adminOnly()

		arguments {
			required<String>("message", "Content of the system message. Trimmed.")
		}

		action {
			Multiverse.brodcastSystem {
				content = args.arg<String>("message").trim()
			}
			result(true)
		}
	}

	subcommand<Boolean>("setloglevel", "Set the level of logging.") {
		adminOnly()

		arguments {
			required<String>("level", "New log level")
		}

		action {
			Log.level = Log.LogLevel.valueOf(args.arg<String>("level").uppercase())
			Log.force { "log level was set to ${Log.level}!" }
			result(true)
		}
	}

	subcommand<Int>("purge", "Deletes messages from the multiverse. Use with caution!") {
		arguments {
			required<Int>("count", "Number of messages to delete")
			flag("delete-origin").alias('o')
		}

		action {
			val deleteOrigin = args.flag("delete-origin")

			var errors = 0
			var deleted = 0
			Multiverse.history.takeLast(min(args.arg<Int>("count"), Multiverse.history.size)).forEach {
				it.retranslated.forEach { 
					try {
						it.delete()
						deleted++
					} catch (e: Exception) {
						errors++
					}
				}
				
				if (deleteOrigin) {
					try {
						it.origin?.delete()?.also { deleted++ }
					} catch (e: Exception) {
						errors++
					}
				}
			}
			
			result(deleted, false)
			reply("$deleted messages were deleted successfully, $errors messages could not be deleted.")
		}
	}
}
