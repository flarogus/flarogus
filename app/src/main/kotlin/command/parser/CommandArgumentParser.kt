package flarogus.command.parser

import flarogus.command.*

class CommandArgumentParser(
	content: String,
	callback: Callback,
	command: FlarogusCommand
) : AbstractArgumentParser<FlarogusCommand>(content, callback, command) {
	lateinit var argcb: Callback.ArgumentCallback

	override protected suspend fun parseImpl() {
		TODO: this
	}
}
