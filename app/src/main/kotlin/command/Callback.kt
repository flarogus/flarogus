package flarogus.command

import java.io.*
import java.net.*
import java.awt.image.*
import javax.imageio.*
import kotlin.contracts.*
import kotlinx.coroutines.*
import dev.kord.rest.builder.message.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.entity.*
import dev.kord.core.behavior.*
import flarogus.*
import flarogus.util.*
import flarogus.command.parser.*

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

	/** The result of the command associated with this callback. */
	var result: R? = null
	/** Whether this callback should send a reply when the command replies to the original message or assigns a result. */
	var replyResult: Boolean = true
	/** Whether this message has sent a response to the original message. */
	var hasResponded = false
	
	/** Asyncronously replies to a message. Does not assign a result. */
	inline fun reply(
		crossinline builder: MessageCreateBuilder.() -> Unit
	): Deferred<Message>? {
		hasResponded = true

		return if (originalMessage == null || !replyResult) {
			null
		} else {
			Vars.client.async {
				originalMessage!!.reply {
					builder()
					content = content?.stripEveryone()?.take(1999)
				}
			}
		}
	}

	/** Asyncronously replies to a message with an embed. Same as `reply { embed { ... } }`. */
	inline fun replyEmbed(
		crossinline builder: EmbedBuilder.() -> Unit
	) = reply { embed(builder) }

	/**
	 * Asyncronously replies to a message. Does not assign a result.
	 * Supports primitive types (including booleans), strings, exceptions, buffered images; toString()s any other objects.
	 */
	fun reply(value: Any?) = reply { 
		content = when (value) {
			is String -> value
			is Boolean -> if (value) "success." else "fail."
			is Number -> "result: $value"

			Unit, null -> "No output."

			is IllegalArgumentException -> "Illegal argument(s):\n${value.message}"
			is IllegalStateException -> "Illegal state:\n${value.message}"
			is IllegalAccessException -> "Illegal access:\n${value.message}"
			is CommandException -> {
				value.commandName = command.getFullName()
				value.message
			}
			is AbstractArgumentParser.ParseException -> value.message
			is Exception -> "A fatal exception has occured: $value"
			is Error -> "A fatal error has occured:\n${value.stackTraceToString()}"

			is BufferedImage -> {
				ByteArrayOutputStream().use {
					ImageIO.write(value, "png", it);
					ByteArrayInputStream(it.toByteArray()).use {
						addFile("result.png", it)
					}
				}
				""
			}

			else -> "output: ${value.toString()}"
		}
	}

	/** Tries to get the original message as a Message, returns null if it doesn't exist */
	open suspend fun originalMessageOrNull() = originalMessage?.asMessage()

	/** Tries to get the original message as a Message, throws a [NullPointerExcpetion] if it doesn't exist */
	open suspend fun originalMessage() = originalMessageOrNull() ?: throw IllegalArgumentException("this message doesn't have an original message")

	/** 
	 * Assigns a result and, if [replyResult] and [doReply] are true, replies with the result to the original message.
	 * This function should not be used to reply with strings (unless it's actual output is a string), as other commands may try to read the result.
	 */
	open fun result(value: R?, doReply: Boolean = true) {
		result = value
		if (doReply) {
			reply(value)
		}
	}

	/** Throws a CommandException with the specified message */
	open fun fail(message: String?): Nothing {
		throw CommandException(command.name, message ?: "no reason specified")
	}
	
	/** Creates and assigns an ArgumentCallback for this callback */
	internal open suspend fun createArguments() = ArgumentCallback().also {
		_arguments = it
		command.arguments?.forEach {
			it.preprocess(this)
		}
	}

	/** Finalizes the creation this Callback */
	internal open suspend fun postprocess() {
		command.arguments?.forEach {
			it.postprocess(this)
		}
	}

	open inner class ArgumentCallback() {
		val positional: PositionalArguments = PositionalArguments()
		val flags: NonPositionalArguments = NonPositionalArguments()

		init {
			// if (command.arguments == null) throw IllegalStateException("an argument-less command cannot have argument callbacks!")	
		}

		/** Returns a positional argument or throws an exception if it is optional and not present */
		open fun <T> arg(name: String) = positional.arg<T>(name)

		/** Returns a positional argument or null if it is optional and not present */
		open fun <T> opt(name: String) = positional.opt<T>(name)
		
		/** Calls the function if an argument is present */
		inline fun <T, R> ifPresent(name: String, action: (T) -> R) = positional.ifPresent(name, action)

		/** Returns whether there's a positional argument with this name present in the callback */
		operator fun contains(name: String) = positional.getOrDefault(name, null) != null

		/** Returns whether a flag is present */
		fun flag(name: String) = name in flags

		/** Calls the function if a flag is present */
		inline fun <R> ifFlagged(name: String, action: () -> R) = flags.ifPresent(name, action)

		@Suppress("UNCHECKED_CAST")
		open inner class PositionalArguments : HashMap<String, Any?>() {
			/** Gets an argument. Throws an exception if it's optional and not present in the map or not present at all */
			fun <T> arg(name: String) = (super.get(name) as T?) ?: throw IllegalArgumentException("argument $name is not present")

			/** Gets an optional argument. May be null. */
			fun <T> opt(name: String) = super.getOrDefault(name, null) as T?

			/** If the argument is present, calls the lambda and provides the value to it */
			inline fun <T, R> ifPresent(name: String, action: (T) -> R): R? {
				return opt<T>(name)?.let(action)
			}
		}

		open inner class NonPositionalArguments : ArrayList<String>() {
			/** If the argument is present, calls the lambda */
			inline fun <R> ifPresent(argument: String, action: () -> R): R? {
				return if (argument in this) action() else null
			}
		}
	}
}
