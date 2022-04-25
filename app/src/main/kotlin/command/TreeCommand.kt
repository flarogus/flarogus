package flarogus.command

import kotlin.math.*

open class TreeCommand(name: String) : FlarogusCommand<Any?>(name) {
	val subcommands = ArrayList<FlarogusCommand<*>>(10)

	init {
		description = "This command contains subcommands."
	}

	/** Do not use. */
	override open fun action(action: suspend Callback<Any?>.() -> Unit): Unit {
		throw RuntimeException("TreeCommand.action() should not be used.")
	}

	override suspend open fun useCallback(callback: Callback<Any?>): Unit {
		try {
			performChecks(callback)

			callback.command = this

			val commandName = callback.message.substring(
				min(callback.argumentOffset, callback.message.length)
			).trimStart().takeWhile { it != ' ' }

			if (commandName.isEmpty()) {
				fallback(callback)
			} else {
				val candidateCommand = subcommands.find { it.name.equals(commandName, true) } ?: throw IllegalArgumentException("command '$commandName' does not exist")
				
				// todo: can i not use indexOf?
				val offset = callback.message.indexOf(commandName) + commandName.length + 1
				callback.argumentOffset = offset
				(candidateCommand as FlarogusCommand<Any?>).useCallback(callback)
			}
		} catch (t: Throwable) {
			if (callback.originalMessage == null) throw t
			callback.reply(t)
		}
	}

	/** Called when an empty string is passed as a command name */
	protected open fun fallback(callback: Callback<out Any?>) {
		throw IllegalArgumentException("you must provide the name of a subcommand of ${this.getFullName()}")
	}
}
