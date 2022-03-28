package flarogus.commands

import dev.kord.core.event.message.*
import dev.kord.core.entity.*
import flarogus.util.*

/** 
 * A command that hosts some amount of subcommands, allowing to build a complex command tree
 *
 * @param ignoreBots Whether the internal command handler should ignore bot users
 * @param generateHelp Whether the internal commNd handler should generate a help command
 */
class Supercommand(
	name: String,
	condition: (User) -> Boolean = { true },
	description: String? = null,
	ignoreBots: Boolean = false,
	generateHelp: Boolean = true
) : Command(
	name = name,
	handler = {},
	header = "subcommand: String, arguments: String...",
	condition = condition, 
	description = description
) {
	override val fancyName get() = "[+] ${super.fancyName}"

	val commands = FlarogusCommandHandler(ignoreBots, "", generateHelp)

	init { 
		handler = {
			//create a fake event
			val newMessage = fakeMessage(message, it[0].lowercase())
			val event = MessageCreateEvent(newMessage, guildId, member, shard, supplier, coroutineScope)
			   
			commands.handle(event)
		}
	}
}
