package flarogus.command.parser

import kotlin.math.*
import flarogus.command.*

abstract class AbstractArgumentParser<T: FlarogusCommand<out Any?>>(
	val callback: Callback<out Any?>,
	val command: T,
	val content: String = callback.message
) {
	var index = max(callback.argumentOffset, 0)
	val tempBuilder = StringBuilder(25)
	val exception = ParseException()

	init {
		if (NONE_CHAR in content) throw IllegalArgumentException("The content contains a null character")
	}

	suspend fun parse() {
		parseImpl()

		callback.argumentOffset = index + 1
		if (!exception.isEmpty()) throw exception
	}

	protected abstract suspend fun parseImpl()

	open fun currentOrNone(): Char {
		return if (index < 0 || index >= content.length) {
			NONE_CHAR
		} else {
			content[index]
		}
	}

	open fun current(): Char = currentOrNone().throwIndexIfNone()

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

	open fun readWhole() = readWhile { true }

	open fun readQuoted(char: Char = '"'): String {
		if (currentOrNone() != char) throw IllegalArgumentException("Current char is not a quote char: ${current()}")

		val begin = index

		var escape = false
		var isClosed = false
		return readWhile(
			modifier = { when {
				escape -> it
				it == '\\' -> {
					escape = true
					NONE_CHAR
				}
				else -> it
			} },
			predicate = {
				if (it == char) isClosed = true
				it != char
			}
		).also {
			if (!isClosed) error("$char", Type.UNTERMINATED_QUOTE, begin, it.length)
			skip() // skip the quotation mark
		}
	}

	/** Reads everything between a pair of two different characters, allowing nested pairs. */
	open fun readPair(begin: Char, end: Char): String {
		require(begin != end) { "The begin char can not be equal to the end char" }
		require(currentOrNone() == begin) { "The current char is not the begin char" }

		val beginIndex = index
		var depth = 1

		return readWhile {
			if (it == begin) depth++
			if (it == end) depth--

			it != end || depth > 0
		}.also {
			if (depth > 0) error("$end", Type.UNTERMINATED_CONSTRUCT, beginIndex, it.length + 1)
			skip() // skip the end char
		}
	}

	open fun lookaheadOrNone(at: Int = 1): Char {
		val i = index + at
		return if (i < 0 || i >= content.length) {
			NONE_CHAR
		} else {
			content[i]
		}
	}

	open fun lookahead(at: Int = 1) = lookaheadOrNone(at).throwIndexIfNone()

	open fun lookbehindOrNone(at: Int = 1) = lookaheadOrNone(-at)

	open fun lookbehind(at: Int = 1) = lookahead(-at)

	open fun skip(chars: Int = 1) {
		index += chars
	}

	inline fun skipWhile(predicate: (Char) -> Boolean) {
		if (currentOrNone().isNone() || !predicate(currentOrNone())) return

		while (readOrNone().let { !it.isNone() && predicate(it) }) {
		}
	}

	open fun skipWhitespace() = skipWhile { it == ' ' || it == '\n' }

	open fun error(message: String, type: Type, at: Int = index, length: Int = 1) {
		exception.errors.add(InputError(message, type, at, length))
	}

	fun Char.isNone() = this == NONE_CHAR

	inline fun <T> Char.ifNone(then: () -> Unit): Char = this.also { if (isNone()) then() }

	inline fun Char.throwIfNone(message: () -> String) = ifNone<Nothing> { throw IllegalArgumentException(message()) }

	fun Char.throwIndexIfNone() = throwIfNone { "Character index out of bounds: $index !in [0; ${content.length}" }

	companion object {
		const val NONE_CHAR = 0.toChar()
	}

	open class ParseException(
		val errors: MutableList<AbstractArgumentParser<*>.InputError> = ArrayList()
	) : Exception() {
		override val message get() = "Errors have occurred while parsing your command:\n" + errors.joinToString("\n")

		fun contains(type: Type) = errors.any { it.type == type }

		fun isEmpty() = errors.isEmpty()
	}

	open inner class InputError(val message: String, val type: Type, val at: Int, val hintLength: Int) {
		val info = buildString {
			if (type.message.isNotEmpty()) append(type.message)
			if (message.isNotEmpty()) {
				if (type.message.isNotEmpty()) append(": ")
				append("'").append(message).append("'")
			}
			append(" at char ").append(at + 1).appendLine(":")
			
			val hintChars = 9
			val hintBegin = max(0, at - hintChars + 1)
			val hintEnd = min(content.length - 1, at + hintChars + 1)
	
			appendLine().append('`')
			(hintBegin..hintEnd).forEach { append(content[it]) }
			appendLine()

			repeat(at - hintBegin) { append(' ') }
			repeat(min(hintLength, hintEnd - at + 2)) { append('^') }
			appendLine('`')
		}

		override fun toString() = info
	}

	enum class Type(val message: String) {
		WRONG_ARGUMENT_TYPE("Wrong argument type"),
		TRAILING_ARGUMENT("Trailing argument"),
		UNRESOLVED_FLAG("Unresolved flag"),
		UNTERMINATED_QUOTE("Unterminated quoted string: expected an ending char"),
		UNTERMINATED_CONSTRUCT("Unterminated construction: expected an ending char"),
		MISSING_ARGUMENT("Missing argument"),
		ILLEGAL_CHARACTER("Illegal character"),
		OTHER("")
	}
}

