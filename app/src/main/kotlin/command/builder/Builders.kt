package flarogus.command.builder

import flarogus.command.*

inline fun <reified T> createCommand(
	name: String,
	description: String? = null,
	builder: CommandBuilder<T>.() -> Unit
) = CommandBuilder<T>(name).also {
	if (description != null) it.description = description
	it.builder()
}.build()

inline fun createTree(
	name: String,
	description: String? = null,
	builder: TreeCommandBuilder.() -> Unit
) = TreeCommandBuilder(name).also {
	if (description != null) it.description = description
	it.builder()
}.build()

inline fun createPresetTree(
	name: String,
	description: String? = null,
	builder: PresetTreeBuilder.() -> Unit
) = PresetTreeBuilder(name).also {
	if (description != null) it.description = description
	it.builder()
}.build()
