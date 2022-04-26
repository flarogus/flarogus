package flarogus.command.parser

import flarogus.command.*

abstract class AbstractArgumentParser<T: FlarogusCommand>(
	val content: String,
	val callback: Callback<out Any?>,
	val command: T
) {
	var index = callback.argumentOffset - 1
	val tempBuilder = StringBuilder(25)
	val exception = ParseException()

	init {
		if (NONE_CHAR in content) throw IllegalArgumentException("The content contains a null character")
	}

	suspend fun parse() {
		parseImpl()

		if (!error.isEmpty()) throw error
	}

	abstract protected suspend fun parseImpl(): Callback.ArgumentCallback

	open fun currentOrNone(): Char {
		return if (index < 0 || index >= content.length) {
			NONE_CHAR
		} else {
			content[index]
		}
	}

	open fun current(): Char = currentOrNone()?.throwIndexIfNone()

	open fun readOrNone(): Char {
		val i = ++index
		return if (i < 0 || i >= content.length) {
			NONE_CHAR
		} else {
			content[i]
		}
	}

	open fun read(): Char {
		return readOrNone().throwIndexIfNone()
	}

	/** Does not append if modifier returns NONE_CHAR, stops when predicate returns false or end is reached */
	inline fun readWhile(
		modifier: (Char) -> Char = { it },
		predicate: (Char) -> Boolean
	): String {
		tempBuilder.clear()
		var char: Char
		while (!readOrNone().also { char = it }.isNone()) {
			if (!predicate(char)) break
			modifier(char).let { if (!it.isNone()) tempBuilder.append(it) }
		}
		return tempBuilder.toString()
	}

	inline fun readWhole() = readWhile { true }

	inline fun readQuoted(): String {
		if (currentOrNone() != '"') throw IllegalArgumentException("Current char is not a quote char: ${current()}")

		var escape = false
		return readWhile(
			modifier = { when {
				escape -> it
				!escape && it == '\\' -> {
					escape = true
					NONE_CHAR
				}
				else -> it
			} }
			predicate = { it != '"' }
		})
	}

	open fun lookaheadOrNone(at: Int = 1) {
		val i = index + at
		if (i < 0 || i >= content.length) {
			NONE_CHAR
		} else {
			content[i]
		}
	}

	open fun lookahead(at: Int = 1) = lookaheadOrNone(at).throwIndexIfNone()

	open fun lookbehindOrNone(at: Int = 1) = lookaheadOrNone(-at)

	open fun lookbehind(at: Int = 1) = lookahead(-at)

	open fun error(message: String, type: InputError.Type, at: Int = index, length: Int = 1) {
		error.
	}

	fun Char.isNone() = this == NONE_CHAR

	inline fun <T> Char.ifNone(then: () -> Unit): Char = this.also { if (isNone()) then() }

	inline fun Char.throwIfNone(message: () -> String) = ifNone { throw IllegalArgumentException(message()) }

	fun Char.throwIndexIfNone() = throwIfNone { "Character index out of bounds: ${index} !in [0; ${content.length}" }

	companion object {
		val NONE_CHAR = 0.toChar()
	}

	inner open class ParseException(val errors: List<String>) : Exception() {
		override val message get() = "Errors have occured while parsing your command:\n" + errors.joinToString('\n')

		fun contains(type: InputError.Type) = errors.any { it.type == type }

		fun isEmpty() = errors.isEmpty()
	}

	inner open class InputError(val message: String, val type: InputError.Type, val at: Int, val length: Int) {
		override fun toString() = buildString {
			append(type.message).append(": ").append(type).append(" at char ").append(at).appendLine(":")
			
			val hintChars = 9
			val hintBegin = max(0, at - hintChars + 1)
			val hintEnd = min(content.length - 1, at + hintChars + 1)
	
			appendLine().append('`')
			(hintBegin..hintEnd).forEach { append(content[it]) }
			appendLine()

			repeat(at - hintBegin) { append(' ') }
			repeat(min(hintChars, length)) { append('^') }
			appendLine('`')
		}

		enum class Type(val message: String) {
			WRONG_ARGUMENT_TYPE("Wrong argument type"),
			TRAILING_ARGUMENT("Trailing argument"),
			UNEXPECTED_FLAG("Unexpexted flag"),
			UNTERMINATED_QUOTE("Unterminated quoted string"),
			MISSING_ARGUMENT("Missing argument")
		}
	}
}

