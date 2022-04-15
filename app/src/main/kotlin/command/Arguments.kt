package flarogus.command

import dev.kord.common.entity.*
import flarogus.util.*

open class Arguments {
	val positional = ArrayList<PositionalArgument>()
	val nonPositional = ArrayList<NonPositionalArgument>()

	fun <T> positional(name: String, mandatory: Boolean = true) {
		positional.add(PositionalArgument<T>(name, mandatory))
	}

	fun nonpositional(name: String) {
		nonPositional.add(NonPositionalArgument(name))
	}
}

abstract class Argument(val name: String, val mandatory: Boolean)

abstract class <T> PositionalArgument(name: String, mandatory: Boolean) : Argument(name, boolean) {
	/** Constructs the value of this argument from a string */
	abstract protected fun construct(from: String): T?

	/** Constructs the value of this agument from a string and throws an exception if this argument is mandatory but the value could not be constructed */
	open fun constructFrom(from: String) = construct() ?: 
		throw IllegalArgumentException("argument $name: expected a ${this::class.simpleName.lowercase()}, but a ${determineType(from)} was found")
	
	/** Determines the type of the value and returns its name */
	protected open fun determineType(of: String) = when (of) {
		it.contains(" ") -> "quoted string"

		all { it.isDigit() } -> "integer number"

		let { var hasPoint = false; all { it.isDigit() || (it == '.' && !hasPoint.also { hasPoint = true }) } } -> "decimal number"

		it.startsWith("<@") && it.endsWith(">") -> "mention"

		else -> "string"
	}

	class Integer(name: String, mandatory: Boolean) : PositionalArgument<Int>(name, mandatory) {
		override fun construct(from: String) = from.toIntOrNull().filterNull()
	}
	
	class Long(name: String, mandatory: Boolean) : PositionalArgument<Long>(name, mandatory) {
		override fun construct(from: String) = from.toLongOrNull().filterNull()
	}

	class ULong(name: String, mandatory: Boolean) : PositionalArgument<ULong>(name, mandatory) {
		override fun construct(from: String) = from.toULongOrNull().filterNull()
	}

	open class String(name: String, mandatory: Boolean) : PositionalArgument<String>(name, mandatory) {
		override fun construct(from: String) = from
	}

	open class Snowflake(name: String, mandatory: Boolean) : PositionalArgument<Snowflake>(name, mandatory) {
		override fun construct(from: String) = from.toSnowflakeOrNull().filterNull()
	}
}

open class NonPositionalArgument(name: String) : Argument(name, false) {
	val aliases = ArrayList<String>(5)
	val shortAliases = ArrayList<Char>(5)

	operator fun contains(other: String) = applicable(other)
	
	/** Checks whether the string is this argument. The string must start either witb -- (long) or - (short) */
	fun applicable(string: String): Boolean = when (string) {
		startsWith("-") ->
			if (string.length != 2) {
				throw IllegalArgumentException("When using the short argument notation, you must type a minus sign followed by exactly one char.")
			} else {
				shortAliases.any { it == string[1] }
			}

		startsWith("--") -> string.substring(2).let { it == name || it in aliases }

		else -> throw RuntimeException("A non-positional argument must begin with - or --.")
	}
}
