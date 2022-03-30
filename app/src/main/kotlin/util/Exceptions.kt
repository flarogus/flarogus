package flarogus.util;

import kotlin.contracts.*

@OptIn(ExperimentalContracts::class)
inline fun expect(
	condition: Boolean,
	thrower: (String) -> Nothing = { throw CommandException(it) },
	crossinline cause: () -> String
) {
	contract {
		returns() implies (condition)
	}

	if (!condition) {
		thrower(cause())
	}
}

open class CommandException(var description: String, cause: Throwable?) : Exception(cause) {
	/** Assigned either wt call-place or at catch-place after being caught by a command handler */
	var commandName: String? = null

	override val message get() = "Could not execute command $commandName: $description${if (cause != null) ", caused by: " + cause else ""}"

	constructor(description: String) : this(description, null)

	constructor(name: String?, description: String) : this(description, null) {
		commandName = name
	}

	constructor(name: String?, description: String, cause: Throwable) : this(description, cause) {
		commandName = name
	}
}
