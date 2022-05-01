package flarogus.command.impl

import flarogus.command.*
import flarogus.command.builder.*
import flarogus.multiverse.*
import flarogus.multiverse.entity.*

fun TreeCommand.addAdminSubtree() = adminSubtree("admin") {
	description = "Admin-only commands that allow to manage the multiverse"

	presetAdminSubtree("listguilds") {
		description = "List multiversal guilds."
		
		fun Callback<String>.list(predicate: (MultiversalGuild) -> Boolean) {
			result(Multiverse.guilds.filter(predicate).sortedByDescending { it.discordId.value }.map {
				"${it.discordId} — ${it.name}"
			}.joinToString("\n"))
		}

		subaction<String>("all") { list { true } }

		subaction<String>("unwhitelisted") { list { !it.isWhitelisted } }

		subaction<String>("whitelisted") { list { it.isWhitelisted } }

		subaction<String>("blacklisted") { list { it.isForceBanned } }

		subaction<String>("invalid") { list { !it.isValid } }
	}

	presetAdminSubtree("blacklist") {
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

	presetAdminSubtree("whitelist") {
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

	presetAdminSubtree("tag") {
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
}
