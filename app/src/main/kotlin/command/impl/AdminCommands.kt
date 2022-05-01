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

		subaction<String>("invalid") { list { !it.isValid } }
	}

	adminSubtree("blacklist") {
		
	}
}
