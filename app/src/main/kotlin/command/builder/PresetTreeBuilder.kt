package flarogus.command.builder

import flarogus.command.*

typealias ArgumentPreset = Arguments.() -> Unit

class PresetTreeBuilder(
	name: String
) : AbstractTreeCommandBuilder<TreeCommand>(TreeCommand(name)) {
	var presetArguments = ArrayList<ArgumentPreset>(5)

	override fun build() = command

	fun presetArguments(arg: ArgumentPreset) {
		presetArguments.add(arg)
	}

	override fun onEachSubcommand(subcommand: FlarogusCommand<*>) {
		subcommand.arguments {
			presetArguments.forEach { it() }
		}
	}
}
