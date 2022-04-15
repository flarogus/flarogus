package flarogus.command

import kotlin.math.*

open class ArgumentDecoder(val callback: Callback, message: String, val offset: Int = 0) {
	val argcb = callback.ArgumentCallback().also { callback._arguments = it }
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
						processArg(arg.toString())
						arg.clear()
					}
					hasQuote = false
				}
				char == '\\' && lastChar != '\\' -> {} //do nothing — it's an escape character
				else -> {
					if (hasQuote && !quoteMode) {
						err("Illegal quotation mark. Put a backslash (\\) before it if you don't want to use a quoted string.", index)
					}
					arg.append(char)
				}
			}

			lastChar = char
		}

		if (quoteMode) {
			err("Unterminated quoted string", quoteBegin)
		}

		if (errors != null && !errors.isEmpty()) {
			throw IllegalArgumentException(errors.toString())
		}

		TODO("implement this")
	}

	protected fun processArg(arg: String) {
		TODO("implement this")
	}

	protected fun err(cause: String, index: Int, hintChars: Int = 5) {
		if (errors == null) errors = StringBuilder(250)

		erorrs!!.apply {
			appendLine(cause)
			
			if (!message.isEmpty() && hintChars > 0) {
				val hintBegin = max(0, index - hintChars)
				val hintEnd = min(message.length - 1, index + hintChars)
		
				appendLine().append('`')
				(hintBegin..hintEnd).forEach { append(message[it]) }
				appendLine()

				repeat(index - hintBegin) { append(' ') }
				append('^').appendLine()
			}
		}
	}
}
