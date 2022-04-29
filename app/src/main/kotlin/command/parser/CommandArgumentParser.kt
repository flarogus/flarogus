package flarogus.command.parser

import flarogus.command.*
import flarogus.command.parser.AbstractArgumentParser.*

open class CommandArgumentParser(
	callback: Callback<out Any?>,
	command: FlarogusCommand<out Any?>
) : AbstractArgumentParser<FlarogusCommand<out Any?>>(callback, command) {
	lateinit var argcb: Callback<out Any?>.ArgumentCallback

	var positionalArgIndex = 0

	override protected suspend fun parseImpl() {
		if (command.arguments == null) {
			if (!content.substring(index).trim().isEmpty()) {
				error("this command accepts no arguments.", Type.TRAILING_ARGUMENT, index, content.length - index)
			}
		} else {
			argcb = callback.createArguments()

			while (index < content.length) {
				skipWhile { it == ' ' || it == '\n' }

				val begin = index

				try {
					readUnit()
				} catch (e: Exception) {
					error(e.message ?: "unknown exception", Type.OTHER, begin, index - begin)
				}
			}

			if (command.requiredArguments > argcb.positional.size) {
				error("", Type.MISSING_ARGUMENT, content.length - 1, 1)
			}
		}	
	}

	/** Reads from the current position to the next space and processes it */
	protected open suspend fun readUnit() {
		skipWhile { it == ' ' || it == '\n' }

		val arg = when (currentOrNone()) {
			// for absokutely no reason, the string has ended
			AbstractArgumentParser.NONE_CHAR -> return

			// if it's a quotation mark, it's most likely a quoted string. if it's in middle, it can be ignored.
			'"', '\'', '`' -> readQuoted(current())

			// raw unquoted string or single-word argument
			'<' -> {
				if (lookaheadOrNone() == '<') {
					skip()
					readWhole()
				} else {
					readArgument()
				}
			}

			// flag. this one is processed on-spot
			'-' -> {
				val flagStr = readArgument()

				if (flagStr.startsWith("--")) {
					// long flag, the simple one
					command.arguments!!.flags.find { it.applicable(flagStr) }?.let { flag ->
						argcb.flags.add(flag.name)
					} ?: error(flagStr, Type.UNRESOLVED_FLAG, index - flagStr.length, flagStr.length)
				} else {
					// short flag
					flagStr.substring(1).forEachIndexed { num, flagChar ->
						command.arguments!!.flags.find { it.applicable(flagChar) }?.let { flag ->
							argcb.flags.add(flag.name)
						} ?: error("-$flagChar", Type.UNRESOLVED_FLAG, index - flagStr.length + 1 + num, 1)
					}
				}

				return
			}

			// anything else
			else -> readArgument()
		}

		val argIndex = positionalArgIndex++
		val argument = command.arguments!!.positional.getOrNull(argIndex)

		if (argument != null) {
			argcb.positional[argument.name] = argument.constructFrom(arg)
		} else {
			error("use quotation marks if it's a part of the previous argument", Type.TRAILING_ARGUMENT, index - arg.length, arg.length)
		}
	}

	open fun readArgument() = current() + readWhile { it != ' ' && it != '\n' }
}
