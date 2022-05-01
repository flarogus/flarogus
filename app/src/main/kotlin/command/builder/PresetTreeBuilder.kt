package flarogus.command.builder

import flarogus.command.*

typealias ArgumentPreset = Arguments.() -> Unit

inline fun TreeCommand.presetSubtree(
	name: String,
	builder: PresetTreeBuilder.() -> Unit
) = PresetTreeBuilder(name).also {
	it.builder()
}.build().also {
	addChild(it)
}

inline fun TreeCommand.presetAdminSubtree(
	name: String,
	builder: PresetTreeBuilder.() -> Unit
) = presetSubtree(name) {
	adminOnly()
	builder()
}

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
