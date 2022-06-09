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
		
		description = "View a help message. Specify an argument to view info of a subcommand."

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

				val cmdname = args.opt<String>("subcommand")
				val subcommand = if (cmdname != null) parent.subcommands.find { it.name.equals(cmdname, true) } else null

				if (cmdname != null && subcommand !is TreeCommand) {
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

					field {
						name = "Usage"
						value = "${subcommand.getFullName()} ${subcommand.summaryArguments()}"
					}

					if (subcommand.arguments != null && !subcommand.arguments!!.isEmpty()) {
						if (!subcommand.arguments!!.flags.isEmpty()) {
							field {
								name = "Flags"
								value = subcommand.arguments!!.flags.mapIndexed { i, it: NonPositionalArgument ->
									val aliases = it.getAllAliases().joinToString(" ")
									"${i + 1}. $aliases (${it.description})"
								}.joinToString("\n")
							}
						}
						if (!subcommand.arguments!!.positional.isEmpty()) {
							field {
								name = "Positional arguments"
								value = subcommand.arguments!!.positional.mapIndexed { i, it ->
									val isOptional = if (it.mandatory) "" else "[optional] "

									"${i + 1}. ${it.name}: ${it.type} $isOptional— ${it.description}"
								}.joinToString("\n")
							}
						}
					} else {
						field { value = "this command has no arguments nor flags." }
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
								subcommand.summaryArguments().let {
									name += " ${subcommand.summaryArguments()}"
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
