package flarogus.command.builder

import flarogus.command.*

abstract class AbstractCommandBuilder<R, T: FlarogusCommand<R>>(
	val command: T
) {
	abstract fun build(): T

	fun check(check: CommandCheck) {
		command.checks.add(check)
	}

	fun adminOnly() = command.adminOnly()

	fun noBots() = command.noBots()

	fun discordOnly() = command.discordOnly()
}
