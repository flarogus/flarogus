package flarogus.util;

inline fun expect(
	condition: Boolean,
	thrower: (String) -> Nothing = { throw CommandException(it) },
	crossinline cause: () -> String
) {
	if (condition) {
		thrower(cause())
	}
}

open class CommandException(val description: String) : RuntimeException() {
	/** Either assigned at call-place or after being caught by a command handler */
	var commandName: String? = null

	constructor(name: String, description: String) : this(description) {
		commandName = name
	}

	override val message = "Could not execute command $commandName: $description"
}
