package flarogus.command

import kotlin.reflect.*
import dev.kord.common.entity.*
import flarogus.util.*

open class Arguments {
	val positional = ArrayList<PositionalArgument<*>>()
	val flags = ArrayList<NonPositionalArgument>()

	inline fun <reified T> argument(
		name: String,
		mandatory: Boolean = true,
		description: String? = null
	) = PositionalArgument.forType<T>(name, mandatory).also {
		it.description = description
		positional.add(it)
	}

	inline fun <reified T> required(name: String, description: String? = null) = argument<T>(name, true, description)

	inline fun <reified T> optional(name: String, description: String? = null) = argument<T>(name, false, description)

	inline fun <reified T> default(
		name: String,
		description: String? = null,
		noinline default: suspend Callback<*>.() -> T
	): DefaultPositionalArgument<T> {
		return DefaultPositionalArgument.forType<T>(name, default).also {
			it.description = description
			positional.add(it)
		}
	}

	fun flag(name: String, description: String? = null) = NonPositionalArgument(name).also {
		it.description = description
		flags.add(it)
	}

	inline fun forEach(action: (Argument) -> Unit) {
		positional.forEach(action)
		flags.forEach(action)
	}
}

abstract class Argument(val name: String, val mandatory: Boolean) {
	var description: String? = null

	/** Called upon the creation of an ArgumentCallback but before it's inflation. */
	open suspend fun preprocess(callback: Callback<*>) {}

	/** Called after the inflation of an ArgumentCallback (not guaranteed, however). */
	open suspend fun postprocess(callback: Callback<*>) {}
}

abstract class PositionalArgument<T>(name: String, mandatory: Boolean) : Argument(name, mandatory) {
	/** Constructs the value of this argument from a string */
	abstract suspend protected fun construct(from: String): T?

	/** Constructs the value of this agument from a string and throws an exception if this argument is mandatory but the value could not be constructed */
	open suspend fun constructFrom(from: String) = construct(from) ?:
		throw IllegalArgumentException("argument $name: expected a ${
			this::class.simpleName?.lowercase()?.let { if (it.endsWith("arg")) it.dropLast(3) else it }
		}, but a ${determineType(from)} was found")
	
	/** Determines the type of the value and returns its name */
	protected open fun determineType(value: String) = when {
		value.contains(" ") -> "quoted string"

		value.all { it.isDigit() } -> "integer number"

		value.let { var hasPoint = false; it.all { it.isDigit() || (it == '.' && !hasPoint.also { hasPoint = true }) } } -> "decimal number"

		value.startsWith("<@") && value.endsWith(">") -> "mention"

		value.startsWith("<#") && value.endsWith(">") -> "channel mention"

		else -> "string"
	}

	class IntArg(name: String, mandatory: Boolean) : PositionalArgument<Int>(name, mandatory) {
		override suspend fun construct(from: String) = from.toIntOrNull()
	}
	
	class LongArg(name: String, mandatory: Boolean) : PositionalArgument<Long>(name, mandatory) {
		override suspend fun construct(from: String) = from.toLongOrNull()
	}

	class ULongArg(name: String, mandatory: Boolean) : PositionalArgument<ULong>(name, mandatory) {
		override suspend fun construct(from: String) = from.toULongOrNull()
	}

	open class StringArg(name: String, mandatory: Boolean) : PositionalArgument<String>(name, mandatory) {
		override suspend fun construct(from: String) = from
	}

	open class SnowflakeArg(name: String, mandatory: Boolean) : PositionalArgument<Snowflake>(name, mandatory) {
		override suspend fun construct(from: String) = from.toSnowflakeOrNull()
	}

	open class UserArg(name: String, mandatory: Boolean) : PositionalArgument<User>(name, mandatory) {
		override suspend fun construct(from: String) = from.toSnowflakeOrNull()?.let {
			Vars.supplier.getUserOrNull(it) ?: throw RuntimeException("User with id $it doesn't exist")
		}
	}

	open class ChannelArg(name: String, mandatory: Boolean) : PositionalArgument<Channel>(name, mandatory) {
		override suspend fun construct(from: String) = from.toSnowflakeOrNull()?.let {
			Vars.supplier.getChannelOrNull(it) ?: throw RuntimeException("Channel with id $it doesn't exist")
		}
	}

	open class MessageChannelArg(name: String, mandatory: Boolean) : PositionalArgument<MessageChannel>(name, mandatory) {
		override suspend fun construct(from: String) = from.toSnowflakeOrNull()?.let {
			(Vars.supplier.getChannelOrNull(it) as? MessageChannel) ?: throw RuntimeException("Channel with id $it doesn't exist or is not a message channel")
		}
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

			Snowflake::class to { n, m -> SnowflakeArg(n, m) },
			Channel::class to { n, m -> ChannelArg(n, m) },
			MessageChannel::class to { n, m -> MessageChannelArg(n, m) },
			User::class to { n, m -> UserArg(n, m) }
		)

		inline fun <reified T> forType(name: String, mandatory: Boolean): PositionalArgument<T> {
			return mapping.getOrDefault(T::class, null)?.let {
				it(name, mandatory) as PositionalArgument<T>
			} ?: throw IllegalArgumentException("Type ${T::class} is not supported for argument type. Add a new entry to PositionalArgument.mapping in order to add support for it.")
		}
	}
}

/** 
 * A wrapper for PositionalArgumenT that provides a default value. Note that the name and other fields of the wrapped argument are ignored.
 * @param argument Wrapped argument
 */
open class DefaultPositionalArgument<T>(
	name: String,
	val default: suspend Callback<*>.() -> T,
	val argument: PositionalArgument<T>
) : PositionalArgument<T>(name, false) {
	override suspend open protected fun construct(from: String) = null

	override suspend open fun constructFrom(from: String) = argument.constructFrom(from)

	override suspend open fun postprocess(callback: Callback<*>) {
		if (callback.hasArgs && !callback.args.positional.contains(name)) {
			callback.args.positional[name] = default(callback)
		}
	}
	
	companion object {
		inline fun <reified T> forType(
			name: String,
			noinline default: suspend Callback<*>.() -> T
		) = DefaultPositionalArgument<T>(
			name, default, PositionalArgument.forType<T>("", false)
		)
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

		string.startsWith("-") -> {
			string.substring(1).any { char ->
				shortAliases.any { char == string[1] }
			}
		}

		else -> throw RuntimeException("A non-positional argument must begin with - or --.")
	}

	/** @see #applicable() */
	fun applicable(char: Char) = shortAliases.any { it == char }
}
