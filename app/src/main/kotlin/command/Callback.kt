package flarogus.command

import dev.kord.rest.builder.message.create.*
import dev.kord.core.entity.*
import flarogus.command.util.*

open class Callback(
	val command: FlarogusCommand,
	val message: Message? = null
) {
	var _arguments: ArgumentCallback? = null
	/** Returns the ArgumentCallback assigned to this callback or throws an IllegalStateException if it doesn't exist */
	val args: ArgumentCallback get() = _arguments ?: throw IllegalStateException("A command has attempted to access it's arguments, but it doesn't have any arguments.")
	
	var result: Any? = null
	var replyResult: Boolean = true
	
	/** Asyncronously replies to a message. Does not assign a result. */
	inline fun reply(builder: suspend MessageCreateBuilder.() -> Unit) = Vars.client.async {
		message?.reply(builder)
	}

	/** Asyncronously replies to a message. Does not assign a result. */
	inline fun reply(value: Any?) = reply { content = value.toString() }

	/** Assigns a result and, if [replyResult] is true, replies with the result to the original message */
	inline fun result(value: Any?) {
		result = value
		if (replyResult) reply { content = value }
	}

	open inner class ArgumentCallback(
		val positional: PositionalArguments = PositionalArguments(),
		val nonPositional: NonPositionalArguments = NonPositionalArguments()
	) {
		init {
			if (command.arguments == null) throw IllegalStateException("an argument-less command cannot have argument callbacks!")
		}

		/** Returns whether there's a poitional argument with this name present in the callback */
		operator fun contains(name: String) = positional.get(name)

		open inner class PositionalArguments : HashMap<String, Any?> {
			/** Gets an argument. Throws an exception if it's optional and not present in the map or not present at all */
			fun arg<T>(name: String) = (super.get(name) as T?) ?: throw IllegalArgumentException("argument $name is not present")

			/** Gets an optional argument. May be null. */
			fun opt<T>(name: String) = super.getOrDefault(name, null) as T?

			/** If the argument is present, calls the lambda and provides the value to it */
			inline fun ifPresent<T>(name: String, action: (T) -> Unit) {
				opt(name)?.let(action)
			}
		}

		open inner class NonPositionalArguments : List<String> {
			/** Returns returns these Arguments contain a specific non-positional argument */
			operator fun contains(argument: String) = any { it == argument }

			/** If the argument is present, calos the lambda */
			inline fun ifPresent(argument: String, action: () -> Unit) {
				if (argument in this) action()
			}
		}
	}
}
