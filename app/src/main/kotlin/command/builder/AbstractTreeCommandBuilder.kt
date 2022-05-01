package flarogus.command.builder

import flarogus.command.*

abstract class AbstractTreeCommandBuilder<T: TreeCommand>(
	command: T
) : AbstractCommandBuilder<Any?, TreeCommand>(command) {
	abstract fun onEachSubcommand(command: FlarogusCommand<*>)

	/** Adds a subcommand with this action and no additional arguments */
	inline fun <reified R> subaction(
		name: String,
		description: String? = null,
		noinline builder: CommandAction<R>
	) {
		subcommand<R>(name) {
			description = description

			action(builder)
		}
	}

	inline fun <reified T> subcommand(
		name: String,
		builder: CommandBuilder<T>
	) = command.subcommand<T>(name) {
		onEachSubcommand(this)
		builder()
	}

	inline fun subtree(
		name: String,
		builder: TreeBuilder
	) = command.subtree(name) {
		onEachSubcommand(this)
		builder()
	}

	inline fun <reified T> adminSubcommand(
		name: String,
		crossinline builder: CommandBuilder<T>
	) = subcommand<T>(name) {
		adminOnly()
		builder()
	}

	inline fun adminSubtree(
		name: String,
		crossinline builder: TreeBuilder
	) = subtree(name) {
		adminOnly()
		builder()
	}
}
