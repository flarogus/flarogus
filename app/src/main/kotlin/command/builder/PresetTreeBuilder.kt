package flarogus.command.builder

import flarogus.command.*

typealias ArgumentPreset = Arguments.() -> Unit

class PresetTreeBuilder(
	name: String
) : AbstractTreeCommandBuilder<TreeCommand>(TreeCommand(name)) {
	var presetChecks = ArrayList<CommandCheck>(3)
	var presetArguments = ArrayList<ArgumentPreset>(5)

	override fun build() = command

	fun presetCheck(check: CommandCheck) {
		presetChecks.add(check)
	}

	fun presetArguments(arg: ArgumentPreset) {
		presetArguments.add(arg)
	}

	override fun onEachSubcommand(subcommand: FlarogusCommand<*>) {
		presetChecks.forEach {
			subcommand.check(it)
		}

		subcommand.arguments {
			presetArguments.forEach { it() }
		}

		if (subcommand is TreeCommand) {
			subcommand.subcommands.forEach(::onEachSubcommand)
		}
	}

	override fun addChild(command: FlarogusCommand<*>) {
		if (command is TreeCommand) error("PresetTreeBuilder doesn't support subtrees!")
		super.addChild(command)
	}
}
