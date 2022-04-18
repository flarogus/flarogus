package flarogus.command

import kotlin.math.*

/**
 * Decodes a string of arguments and assignes them to the providen callback
 */
open class ArgumentDecoder(
	val arguments: Arguments,
	val callback: Callback<out Any?>,
	message: String,
	val offset: Int = 0
) {
	val argcb = callback.createArguments()
	val message = message + " "

	protected val arg = StringBuilder(50)
	protected var errors: StringBuilder? = null
	protected var quoteBegin = -1
	protected var quoteMode = false
	protected var hasQuote = false

	/** 
	 * Constructs an ArgumentCallback for the callback and automatically assigns it
	 * @throws IllegalArgumentException containing all errors if the arguments are invalid
	 */
	open fun decode() {
		var lastChar: Char = 0.toChar()

		// appending a space as a workaround in order to make sure the last argument would get processed too
		for (index in min(offset, message.length - 1)..message.length - 1) {
			val char = message[index]

			when {
				char == '"' && lastChar != '\\' -> {
					if (!quoteMode.also { quoteMode = !quoteMode }) {
						if (!arg.isEmpty()) {
							err("Illegal quotation mark. Put a backslash (\\) before it if you don't want to make a quoted string.", index)
						}
						hasQuote = true
						arg.clear()
					}
				}
				char == ' ' && lastChar != '\\' && !quoteMode -> {
					if (!arg.isEmpty()) {
						processArg(arg.toString(), index)
						arg.clear()
					}
					hasQuote = false
				}
				char == '\\' && lastChar != '\\' -> {} //do nothing — it's an escape character
				else -> {
					if (hasQuote && !quoteMode) {
						err("Trailing text after a quotation mark. Add a space after the quote.", index)
					}
					arg.append(char)
				}
			}

			lastChar = char
		}

		if (quoteMode) {
			err("Unterminated quoted string", quoteBegin)
		}

		if (errors != null && !errors!!.isEmpty()) {
			throw IllegalArgumentException("Error(s) have occured:\n`errors.toString())`")
		}
	}

	protected fun processArg(arg: String, position: Int) {
		if (arg.isEmpty()) return

		if (arg.startsWith("-")) {
			if (arguments.flags.any { it.applicable(arg) }) {
				val trimmed = if (arg.startsWith("--")) arg.substring(2) else arg.substring(1)
				argcb.flags.add(trimmed)
			} else {
				err("Unresolved flag (add a '\\' before it if it's not a flag)", position - arg.length, arg.length)
			}
		} else {
			val index = argcb.positional.size
			val argument = arguments.positional.getOrNull(index)

			if (argument != null) {
				argcb.positional[argument.name] = argument.constructFrom(arg)
			} else {
				err("Unexpected argument. Use quotation marks if it's a part of the previous argument.", position - arg.length, arg.length)
			}
		}
	}

	protected fun err(cause: String, index: Int, hightlightLength: Int = 1, hintChars: Int = 5) {
		if (errors == null) errors = StringBuilder(250)

		errors!!.apply {
			appendLine(cause)
			
			if (!message.isEmpty() && hintChars > 0) {
				val hintBegin = max(0, index - hintChars)
				val hintEnd = min(message.length - 1, index + hintChars)
		
				appendLine().append('`')
				(hintBegin..hintEnd).forEach { append(message[it]) }
				appendLine()

				repeat(index - hintBegin) { append(' ') }
				repeat(min(hintChars, hightlightLength)) { append('^') }
				appendLine()
			}
		}
	}
}
