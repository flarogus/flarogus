package flarogus.command.parser

import flarogus.command.*
import flarogus.command.parser.AbstractArgumentParser.*

/**
 * Parses argument of a TreeCommand.
 *
 * If the string contains flags, they're added to callback's arguments.
 * In order to read the subcommand, read the [resultSubcommand] property of the parser after invoking [parse()].
 */
open class TreeCommandArgumentParser(
	callback: Callback<Any?>,
	command: TreeCommand
) : AbstractArgumentParser<TreeCommand>(callback, command) {
	lateinit var resultSubcommand: String
	var hasResult = false

	lateinit var argcb: Callback<Any?>.ArgumentCallback

	override protected suspend fun parseImpl() {
		argcb = callback.createArguments() as Callback<Any?>.ArgumentCallback

		while (!hasResult && index < content.length) {
			skipWhile { it == ' ' || it == '\n' }

			val begin = index

			try {
				readUnit()
			} catch (e: Exception) {
				error(e.message ?: "unknown exception", Type.OTHER, begin, index - begin)
			}
		}

		if (!hasResult) error("No subcommand name specified!", Type.MISSING_ARGUMENT, content.length - 1)
	}

	fun readUnit() {
		val arg = readArgument()

		when {
			arg.isEmpty() -> return; // whatsoever

			arg.startsWith("--") -> {
				command.arguments?.flags?.find { it.applicable(arg) }?.let { flag ->
					argcb.flags.add(flag.name)
				} ?: error(arg, Type.UNRESOLVED_FLAG, index - arg.length, arg.length)
			}

			arg.startsWith("-") -> {
				arg.substring(1).forEachIndexed { num, flagChar ->
					command.arguments?.flags?.find { it.applicable(flagChar) }?.let { flag ->
						argcb.flags.add(flag.name)
					} ?: error("-$flagChar", Type.UNRESOLVED_FLAG, index - arg.length + 1 + num, 1)
				}
			}

			arg.startsWith('"') || arg.startsWith('\'') || arg.startsWith('`') -> {
				error("'${arg.first()}'. Subcommand names do not support quoted strings.", Type.ILLEGAL_CHARACTER, index - arg.length)
			}

			arg.startsWith("<<") -> {
				error("'<<'. Subcommand names do not support raw strings.", Type.ILLEGAL_CHARACTER, index - arg.length)
			}

			else -> {
				resultSubcommand = arg
				hasResult = true
			}
		}
	}

	open fun readArgument() = current() + readWhile { it != ' ' && it != '\n' }
}
