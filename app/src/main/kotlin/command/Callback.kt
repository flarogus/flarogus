package flarogus.command

import kotlinx.coroutines.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.entity.*
import dev.kord.core.behavior.*
import flarogus.*

open class Callback<R>(
	val command: FlarogusCommand<*>,
	val message: MessageBehavior? = null
) {
	protected var _arguments: ArgumentCallback? = null
	/** Returns the ArgumentCallback assigned to this callback or throws an IllegalStateException if it doesn't exist */
	val args: ArgumentCallback get() = _arguments ?: throw IllegalStateException("A command has attempted to access it's arguments, but it doesn't have any arguments.")
	
	var result: R? = null
	var replyResult: Boolean = true
	
	/** Asyncronously replies to a message. Does not assign a result. */
	inline fun reply(crossinline builder: MessageCreateBuilder.() -> Unit) = Vars.client.async {
		message?.reply(builder)
	}

	/** Asyncronously replies to a message. Does not assign a result. */
	fun reply(value: R?) = reply { 
		content = when (value) {
			is Boolean -> if (value) "success." else "fail."
			is Number -> "result: $value"
			is String -> value
			else -> "output: ${value.toString()}"
		}
	}

	/** 
	 * Assigns a result and, if [replyResult] is true, replies with the result to the original message.
	 * This function should not be used to reply with strings (unless it's actual output is a string), as other commands may try to read the result.
	 */
	open fun result(value: R?) {
		result = value
		if (replyResult) reply(value)
	}
	
	/** Creates and assigns an ArgumentCallback for this callback */
	internal open fun createArguments() = ArgumentCallback().also {
		_arguments = it
	}

	open inner class ArgumentCallback() {
		val positional: PositionalArguments = PositionalArguments()
		val flags: NonPositionalArguments = NonPositionalArguments()

		init {
			if (command.arguments == null) throw IllegalStateException("an argument-less command cannot have argument callbacks!")
		}

		/** Returns whether there's a poitional argument with this name present in the callback */
		operator fun contains(name: String) = positional.getOrDefault(name, null) != null

		open inner class PositionalArguments : HashMap<String, Any?>() {
			/** Gets an argument. Throws an exception if it's optional and not present in the map or not present at all */
			fun <T> arg(name: String) = (super.get(name) as T?) ?: throw IllegalArgumentException("argument $name is not present")

			/** Gets an optional argument. May be null. */
			fun <T> opt(name: String) = super.getOrDefault(name, null) as T?

			/** If the argument is present, calls the lambda and provides the value to it */
			inline fun <T> ifPresent(name: String, action: (T) -> Unit) {
				opt<T>(name)?.let(action)
			}
		}

		open inner class NonPositionalArguments : ArrayList<String>() {
			/** If the argument is present, calos the lambda */
			inline fun ifPresent(argument: String, action: () -> Unit) {
				if (argument in this) action()
			}
		}
	}
}
