package flarogus.util;

open class CommandException(command: String, description: String) : RuntimeException() {
	override val message = "Could not execute command $command: $description"
}
