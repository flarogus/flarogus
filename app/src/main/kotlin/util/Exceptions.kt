package flarogus.util;

inline fun expect(
	condition: Boolean,
	thrower: (String) -> Nothing = { throw CommandException(it) },
	crossinline cause: () -> String
) {
	if (!condition) {
		thrower(cause())
	}
}

open class CommandException(var description: String) : Exception() {
	/** Assigned either wt call-place or at catch-place after being caught by a command handler */
	var commandName: String? = null

	override val message get() = "Could not execute command $commandName: $description"

	constructor(name: String, description: String) : this(description) {
		commandName = name
	}
}
