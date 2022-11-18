package flarogus.command.parser

import flarogus.Vars
import flarogus.command.*
import kotlin.math.*

//TODO: write unit tests

/** Parses qrguments of normal commands. */
open class CommandArgumentParser(
	callback: Callback<out Any?>,
	command: FlarogusCommand<out Any?>
) : AbstractArgumentParser<FlarogusCommand<out Any?>>(callback, command) {
	protected lateinit var argcb: Callback<out Any?>.ArgumentCallback

	protected var positionalArgIndex = 0
	/** Whether to perform command substitutions */
	var performSubstitutions = true

	override suspend fun parseImpl() {
		argcb = callback.createArguments()

		if (command.arguments == null) {
			skipWhitespace()
			if (index < content.length) {
				error("this command accepts no arguments.", Type.TRAILING_ARGUMENT, index, content.length - index)
			}
		} else {
			while (index < content.length) {
				skipWhitespace()

				val begin = index

				try {
					readUnit()
				} catch (e: ParseException) {
					// merge them in case of a failed command substitution
					exception.errors.addAll(e.errors)
				} catch (e: Exception) {
					error(e.message ?: "unknown exception", Type.OTHER, begin, index - begin)
				}
			}

			if (command.requiredArguments > positionalArgIndex) {
				error("", Type.MISSING_ARGUMENT, content.length - 1, 1)
			}
		}
	}

	/** Reads from the current position to the next space and processes it */
	protected open suspend fun readUnit() {
		skipWhitespace()

		var arg = when (currentOrNone()) {
			// for absolutely no reason, the string has ended
			AbstractArgumentParser.NONE_CHAR -> return

			// if it's a quotation mark, it's most likely a quoted string. if it's in middle, it can be ignored.
			'"', '\'', '`' -> readQuoted(current())

			// raw unquoted string or single-word argument
			'<' -> {
				if (lookaheadOrNone() == '<') {
					skip()
					readWhole().trim()
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

		if (performSubstitutions) {
			// transform the argument by performing substitutions
			arg = buildString top@ {
				var pos = -1
				while (++pos < arg.length) {
					// until a "$(" is met, read literallyj
					if (arg[pos] == '\\') {
						// append the next char literally
						if (++pos < arg.length) append(arg[pos])
					} else if (arg[pos] != '$' || arg.getOrNull(pos + 1) != '(') {
						append(arg[pos])
					} else {
						val begin = pos
						pos++ // skip the $. the next ++pos will skip the (.
						// read the substitution, respecting the inner substitutions
						val command = buildString {
							var depth = 0
							while (++pos < arg.length) {
								if (arg[pos] == '\\') {
									if (++pos < arg.length) append(arg[pos])
								} else if (arg[pos] == ')') {
									if (depth <= 0) return@buildString // the substitution has ended

									depth = max(depth - 1, 0)
								} else if (arg[pos] == '$' && arg.getOrNull(pos + 1) == '(') {
									depth++
								}
								append(arg[pos])
							}

							error(")'. If it's not a substitution, put a backslash before '$(", Type.UNTERMINATED_CONSTRUCT, begin, length)
							return@top
						}.trimStart().removePrefix("!flarogus").trim()

						val result = callback.invokeCommand(command, false).result
						append(result?.toString()?.trim().orEmpty())
					}
				}
			}
		}

		val argIndex = positionalArgIndex++
		val argument = command.arguments!!.positional.getOrNull(argIndex)

		if (argument != null) {
			argcb.positional[argument.name] = argument.constructFrom(arg)
		} else {
			error("use quotation marks if it's a part of the previous argument", Type.TRAILING_ARGUMENT, index - arg.length, arg.length)
		}
	}

	open fun readArgument(): String {
		var depth = 0

		return current() + readWhile {
			if (lookbehindOrNone() == '$' && it == '(') depth++
			if (it == ')') depth--

			(it != ' ' && it != '\n') || depth > 0
		}
	}
}
