package flarogus.command

import dev.kord.common.*
import flarogus.util.*

/**
 * Provides help messages for tree commands.
 */
class HelpCommand : FlarogusCommand<Unit>("help") {
	var embedColor = Color(0x7711aa)
	val splitter = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

	init {
		discordOnly()
		
		description = "View the list of commands. Specify an argument to view info of a subcommand."

		arguments {
			optional<String>("subcommand", "Subcommand whose help you want to see.")
		}

		action {
			val parent = this@HelpCommand.parent

			if (parent == null) {
				throw IllegalStateException("HelpCommand must be a child of a TreeCommand, but it is not")
			}

			replyEmbed {
				color = embedColor

				if ("subcommand" in args) {
					val subcommand = parent.subcommands.find { it.name.equals(args.arg<String>("subcommand"), true) }

					expect(subcommand != null) {
						"Subcommand '$subcommand' was not found in '${parent.getFullName()}'!".let {
							val matches = parent.findSimmilar(args.arg<String>("subcommand"), 3)
							val matchesStr = matches.map { "`$it`" }.joinToString(", ")

							if (!matches.isEmpty()) {
								"$it\nDid you mean:\n$matchesStr?"
							} else {
								it
							}
						}
					}

					title = subcommand.getFullName()

					description = subcommand.description + "\n" + splitter

					if (subcommand.arguments != null) {
						if (!subcommand.arguments!!.flags.isEmpty()) {
							field {
								name = "flags"
								value = subcommand.arguments!!.flags.map {
									"$it (${it.description})"
								}.joinToString("\n")
							}
						}
						if (!subcommand.arguments!!.positional.isEmpty()) {
							field {
								name = "positional arguments"
								value = subcommand.arguments!!.positional.map {
									"$it: ${it.type} (${it.description})"
								}.joinToString("\n")
							}
						}
					}
				} else {
					title = parent.getFullName()

					description = """
						__Underlined commands have subcommands.__
						To invoke a subcommand, type the full name of the parent command and the name of the subcommand, delimited by a space.
						$splitter
						Only summary info is providen here.
						Type `${parent.getFullName()} subcommand_name_here` to full view help of induvidual subcommands.
						$splitter
					""".trimIndent()

					parent.subcommands.forEach { subcommand ->
						field {
							name = subcommand.name

							if (subcommand is TreeCommand) {
								name = "__${name}__"
							} else {
								subcommand.summaryArguments()?.let { 
									name += "[${subcommand.summaryArguments()}]"
								}
							}

							value = subcommand.description.substringBefore('.')
						}
					}
				}
			}
		}
	}
}
