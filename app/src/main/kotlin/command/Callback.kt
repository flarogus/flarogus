package flarogus.command

import kotlin.contracts.*
import kotlinx.coroutines.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.entity.*
import dev.kord.core.behavior.*
import flarogus.*
import flarogus.util.*

/**
 * A callback of a command.
 *
 * @param command Command to which this callback relates. May be replaced by subcommands.
 * @param message A message containing the arguments.
 * @param originalMessage If the command was triggered by a discord message, this property contains that message.
 */
@OptIn(ExperimentalContracts::class)
open class Callback<R>(
	var command: FlarogusCommand<*>,
	val message: String,
	val originalMessage: MessageBehavior? = null
) {
	/** The offset at which the arguments of a command begin. Used by parent commands. */
	var argumentOffset = 0

	protected var _arguments: ArgumentCallback? = null
	/** Returns the ArgumentCallback assigned to this callback or throws an IllegalStateException if it doesn't exist */
	val args: ArgumentCallback get() = _arguments ?: throw IllegalStateException("A command has attempted to access it's arguments, but it doesn't have any arguments.")
	/** Whether this collback has arguments */
	val hasArgs: Boolean get() = _arguments != null

	var result: R? = null
	var replyResult: Boolean = true
	
	/** Asyncronously replies to a message. Does not assign a result. */
	inline fun reply(
		crossinline builder: MessageCreateBuilder.() -> Unit
	) = if (originalMessage == null) null else Vars.client.async {
		originalMessage!!.reply {
			builder()
			content = content?.stripEveryone()?.take(1999)
		}
	}

	/**
	 * Asyncronously replies to a message. Does not assign a result.
	 * Supports primitive types (including booleans), strings, exceptions; toString()s any other objects.
	 */
	fun reply(value: Any?) = reply { 
		content = when (value) {
			is Boolean -> if (value) "success." else "fail."
			is Number -> "result: $value"
			is String -> value

			Unit, null -> "Executed with no output."

			is IllegalArgumentException -> "Illegal argument(s):\n${value.message}"
			is IllegalStateException -> "Illegal state:\n${value.message}"
			is IllegalAccessException -> "You are not allowed to execute this command:\n${value.message}"
			is CommandException -> {
				value.commandName = command.getFullName()
				value.message
			}
			is Exception -> "A fatal exception has occured: $value"
			is Error -> "A fatal error has occured:\n${value.stackTraceToString()}"

			else -> "output: ${value.toString()}"
		}
	}

	/** 
	 * Assigns a result and, if [replyResult] and [doReply] are true, replies with the result to the original message.
	 * This function should not be used to reply with strings (unless it's actual output is a string), as other commands may try to read the result.
	 */
	open fun result(value: R?, doReply: Boolean = true) {
		result = value
		if (replyResult && doReply) reply(value)
	}
	
	/** Creates and assigns an ArgumentCallback for this callback */
	internal open fun createArguments() = ArgumentCallback().also {
		_arguments = it
		command.arguments?.forEach {
			it.preprocess(this)
		}
	}

	/** Finalizes the creation this Callback */
	internal open fun postprocess() {
		command.arguments?.forEach {
			it.postprocess(this)
		}
	}

	open inner class ArgumentCallback() {
		val positional: PositionalArguments = PositionalArguments()
		val flags: NonPositionalArguments = NonPositionalArguments()

		init {
			if (command.arguments == null) throw IllegalStateException("an argument-less command cannot have argument callbacks!")	
		}

		/** Returns a positional argument or throws an exception if it is optional and not present */
		open fun <T> arg(name: String) = positional.arg<T>(name)

		/** Returns a positional argument or null if it is optional and not present */
		open fun <T> opt(name: String) = positional.opt<T>(name)
		
		/** Calls the function if an argument is present */
		inline fun <T> ifPresent(name: String, action: (T) -> Unit) = positional.ifPresent(name, action)

		/** Returns whether there's a poitional argument with this name present in the callback */
		operator fun contains(name: String) = positional.getOrDefault(name, null) != null

		/** Returns whether a flag is present */
		fun flag(name: String) = name in flags

		/** Calls the function if a flag is present */
		inline fun ifFlagged(name: String, action: () -> Unit) = flags.ifPresent(name, action)

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
