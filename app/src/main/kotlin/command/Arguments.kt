package flarogus.command

import kotlin.reflect.*
import dev.kord.common.entity.*
import flarogus.util.*

open class Arguments {
	val positional = ArrayList<PositionalArgument<*>>()
	val flags = ArrayList<NonPositionalArgument>()

	inline fun <reified T> argument(name: String, mandatory: Boolean = true) = PositionalArgument.forType<T>(name, mandatory).also {
		positional.add(it)
	}

	inline fun <reified T> required(name: String) = argument<T>(name, true)

	inline fun <reified T> optional(name: String) = argument<T>(name, false)

	fun flag(name: String) = NonPositionalArgument(name).also {
		flags.add(it)
	}
}

abstract class Argument(val name: String, val mandatory: Boolean)

abstract class PositionalArgument<T>(name: String, mandatory: Boolean) : Argument(name, mandatory) {
	/** Constructs the value of this argument from a string */
	abstract protected fun construct(from: String): T?

	/** Constructs the value of this agument from a string and throws an exception if this argument is mandatory but the value could not be constructed */
	open fun constructFrom(from: String) = construct(from) ?: 
		throw IllegalArgumentException("argument $name: expected a ${
			this::class.simpleName?.lowercase()?.let { if (it.endsWith("arg")) it.dropLast(3) else it }
		}, but a ${determineType(from)} was found")
	
	/** Determines the type of the value and returns its name */
	protected open fun determineType(value: String) = when {
		value.contains(" ") -> "quoted string"

		value.all { it.isDigit() } -> "integer number"

		value.let { var hasPoint = false; it.all { it.isDigit() || (it == '.' && !hasPoint.also { hasPoint = true }) } } -> "decimal number"

		value.startsWith("<@") && value.endsWith(">") -> "mention"

		else -> "string"
	}

	class IntArg(name: String, mandatory: Boolean) : PositionalArgument<Int>(name, mandatory) {
		override fun construct(from: String) = from.toIntOrNull()
	}
	
	class LongArg(name: String, mandatory: Boolean) : PositionalArgument<Long>(name, mandatory) {
		override fun construct(from: String) = from.toLongOrNull()
	}

	class ULongArg(name: String, mandatory: Boolean) : PositionalArgument<ULong>(name, mandatory) {
		override fun construct(from: String) = from.toULongOrNull()
	}

	open class StringArg(name: String, mandatory: Boolean) : PositionalArgument<String>(name, mandatory) {
		override fun construct(from: String) = from
	}

	open class SnowflakeArg(name: String, mandatory: Boolean) : PositionalArgument<Snowflake>(name, mandatory) {
		override fun construct(from: String) = from.toSnowflakeOrNull()
	}

	companion object {
		/** 
		 * Maps possible argument classes to their constructors. 
		 * If you are declaring a new argument type, yoh should add your class here.
		 * Every lambda must return a PositionalArgument whose generic type is equal to that of the class the class assigned to.
		 */
		val mapping = mutableMapOf<KClass<out Any>, (name: String, mandatory: Boolean) -> PositionalArgument<*>>(
			Int::class to { n, m -> IntArg(n, m) },
			Long::class to { n, m -> LongArg(n, m) },
			ULong::class to { n, m -> ULongArg(n, m) },
			String::class to { n, m -> StringArg(n, m) },
			Snowflake::class to { n, m -> SnowflakeArg(n, m) }
		)

		inline fun <reified T> forType(name: String, mandatory: Boolean): PositionalArgument<T> {
			return mapping.getOrDefault(T::class, null)?.let {
				it(name, mandatory) as PositionalArgument<T>
			} ?: throw IllegalArgumentException("Type ${T::class} is not supported for argument type. Add a new entry to PositionalArgument.mapping in order to add support for it.")
		}
	}
}

open class NonPositionalArgument(name: String) : Argument(name, false) {
	val aliases = ArrayList<String>(5)
	val shortAliases = ArrayList<Char>(5)

	operator fun contains(other: String) = applicable(other)

	fun alias(alias: Char) = this.also { shortAliases.add(alias) }

	fun alias(alias: String) = this.also { aliases.add(alias) }
	
	/** Checks whether the string is this argument. The string must start either witb -- (long) or - (short) */
	fun applicable(string: String): Boolean = when {
		string.startsWith("--") -> string.substring(2).let { it == name || it in aliases }

		string.startsWith("-") ->
			if (string.length != 2) {
				throw IllegalArgumentException("When using the short argument notation, you must type a minus sign followed by exactly one char.")
			} else {
				shortAliases.any { it == string[1] }
			}

		else -> throw RuntimeException("A non-positional argument must begin with - or --.")
	}
}
