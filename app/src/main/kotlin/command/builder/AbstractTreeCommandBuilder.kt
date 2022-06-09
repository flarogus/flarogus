package flarogus.command.builder

import flarogus.command.*

@Suppress("SpellCheckingInspection")
abstract class AbstractTreeCommandBuilder<T: TreeCommand>(
	command: T
) : AbstractCommandBuilder<Any?, TreeCommand>(command) {
	abstract fun onEachSubcommand(command: FlarogusCommand<*>)

	/** Adds a subcommand with this action and no additional arguments */
	inline fun <reified R> subaction(
		name: String,
		description: String? = null,
		noinline subaction: CommandAction<R>
	) {
		subcommand<R>(name, description) {
			action(subaction)
		}
	}

	fun addChild(command: FlarogusCommand<*>) = this.command.addChild(command)

	inline fun <reified T> subcommand(
		name: String,
		description: String? = null,
		builder: CommandBuilder<T>.() -> Unit
	) = createCommand<T>(name, description) {
		onEachSubcommand(this@createCommand.command)
		builder()
	}.also { addChild(it) }

	inline fun subtree(
		name: String,
		description: String? = null,
		builder: TreeCommandBuilder.() -> Unit
	) = createTree(name, description) {
		onEachSubcommand(this@createTree.command)
		builder()
	}.also { addChild(it) }

	inline fun presetSubtree(
		name: String,
		description: String? = null,
		builder: PresetTreeBuilder.() -> Unit
	) = createPresetTree(name, description) {
		onEachSubcommand(this@createPresetTree.command)
		builder()
	}.also { addChild(it) }
}

class TreeCommandBuilder(name: String) : AbstractTreeCommandBuilder<TreeCommand>(TreeCommand(name)) {
	override fun build() = command
	override fun onEachSubcommand(command: FlarogusCommand<*>) {}
}
