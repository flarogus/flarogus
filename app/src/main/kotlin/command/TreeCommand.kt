package flarogus.command

import kotlin.math.*
import info.debatty.java.stringsimilarity.*
import flarogus.command.parser.*

val levenshtein = NormalizedLevenshtein()

open class TreeCommand(name: String) : FlarogusCommand<Any?>(name) {
	val subcommands = ArrayList<FlarogusCommand<*>>(10)

	init {
		description = "This command contains subcommands."

		addHelpSubcommand()
	}

	open fun addChild(command: FlarogusCommand<*>) {
		subcommands.add(command)
		command.parent = this
	}

	open fun addHelpSubcommand() {
		if (subcommands.any { it is HelpCommand }) throw RuntimeException("Tree command $name already has a help subcommand")

		addChild(HelpCommand())
	}

	override suspend fun useCallback(callback: Callback<Any?>): Unit {
		try {
			performChecks(callback)

			callback.command = this
			val commandName = TreeCommandArgumentParser(callback, this).also {
				it.parse()
			}.resultSubcommand
			
			action?.invoke(callback)

			if (commandName.isEmpty()) {
				fallback(callback)
			} else {
				val candidateCommand = subcommands.find {
					it.name.equals(commandName, true)
				} ?: let {
					val possible = findSimmilar(commandName, 3)

					throw NoSuchCommandException(buildString {
						append("command '").append(commandName).append("' does not exist").append('\n')
						if (possible.isNotEmpty()) {
							appendLine("possible matches:")
							
							possible.forEach {
								append('`').append(it.getFullName()).append("` ")
							}
						}
					})
				}

				(candidateCommand as FlarogusCommand<Any?>).useCallback(callback)
			}
		} catch (t: Throwable) {
			if (callback.originalMessage == null) throw t
			replyWithError(callback, t)
		}
	}

	/** Called when an empty string is passed as a command name */
	protected open fun fallback(callback: Callback<out Any?>) {
		throw IllegalArgumentException("you must provide the name of a subcommand of ${this.getFullName()}")
	}

	/** Finds up to [count] most similar commands */
	open fun findSimmilar(command: String, count: Int, maxDistance: Double = 0.5): List<FlarogusCommand<*>> {
		var fitting = 0
		return subcommands.sortedBy { flarogusCommand ->
			levenshtein.distance(command, flarogusCommand.name).also {
				if (it < maxDistance) fitting++
			}
		}.take(min(count, fitting))
	}

	class NoSuchCommandException(message: String) : IllegalArgumentException(message)
}
