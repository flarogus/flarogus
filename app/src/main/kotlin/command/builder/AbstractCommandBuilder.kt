package flarogus.command.builder

import flarogus.command.*

abstract class AbstractCommandBuilder<R, T: FlarogusCommand<R>>(
	val command: T
) {
	var description by command::description
	var hidden by command::hidden
	var performSubstitutions by command::performSubstitutions
	var errorDeleteDelay by command::errorDeleteDelay

	abstract fun build(): T

	inline fun arguments(builder: Arguments.() -> Unit) = command.arguments(builder)

	fun action(act: CommandAction<R>) {
		command.action(act)
	}

	fun check(check: CommandCheck) {
		command.checks.add(check)
	}

	fun adminOnly() = command.adminOnly()

	fun modOnly() = command.modOnly()

	fun noBots() = command.noBots()

	fun discordOnly() = command.discordOnly()
}

class CommandBuilder<R>(
	name: String
) : AbstractCommandBuilder<R, FlarogusCommand<R>>(FlarogusCommand<R>(name)) {
	override fun build() = command
}
