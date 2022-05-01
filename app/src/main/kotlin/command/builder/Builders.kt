package flarogus.command.builder

import flarogus.command.*

typealias CommandBuilder<T> = FlarogusCommand<T>.() -> Unit
typealias TreeBuilder = TreeCommand.() -> Unit

inline fun <reified T> createCommand(
	name: String,
	description: String? = null,
	builder: CommandBuilder<T>
) = FlarogusCommand<T>(name).also {
	if (description != null) it.description = description	
	it.builder()
}

inline fun createTree(
	name: String,
	description: String? = null,
	builder: TreeBuilder
) = TreeCommand(name).also {
	if (description != null) it.description = description
	it.builder()
}

inline fun <reified T> TreeCommand.subcommand(
	name: String,
	description: String? = null,
	builder: CommandBuilder<T>
) = createCommand(name, description, builder).also { 
	addChild(it)
}

inline fun TreeCommand.subtree(
	name: String,
	description: String? = null,
	builder: TreeBuilder
) = createTree(name, description, builder).also {
	addChild(it)
}

inline fun <reified T> TreeCommand.adminSubcommand(
	name: String,
	description: String? = null,
	crossinline builder: CommandBuilder<T>
) = subcommand<T>(name, description) {
	adminOnly()
	builder()
}

inline fun TreeCommand.adminSubtree(
	name: String,
	description: String? = null,
	crossinline builder: TreeBuilder
) = subtree(name, description) {
	adminOnly()
	builder()
}
