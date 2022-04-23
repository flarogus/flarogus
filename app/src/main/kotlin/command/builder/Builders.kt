package flarogus.command.builder

import flarogus.command.*

typealias CommandBuilder<T> = FlarogusCommand<T>.() -> Unit
typealias TreeBuilder = TreeCommand.() -> Unit

inline fun <reified T> createCommand(
	name: String,
	builder: CommandBuilder<T>
) = FlarogusCommand<T>(name).also { it.builder() }

inline fun createTree(
	name: String,
	builder: TreeBuilder
) = TreeCommand(name).also { it.builder() }

inline fun <reified T> TreeCommand.subcommand(
	name: String,
	builder: CommandBuilder<T>
) = createCommand(name, builder).also { 
	it.parent = this
	subcommands.add(it)
}

inline fun TreeCommand.subtree(
	name: String,
	builder: TreeBuilder
) = createTree(name, builder).also {
	it.parent = this
	subcommands.add(it)
}
