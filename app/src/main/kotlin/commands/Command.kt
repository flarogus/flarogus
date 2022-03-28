package flarogus.commands

import kotlinx.coroutines.*;
import dev.kord.core.entity.*;
import dev.kord.core.event.message.*;
import flarogus.*

open class Command(
	var name: String,
	var handler: suspend MessageCreateEvent.(List<String>) -> Unit,
	var condition: (User) -> Boolean = { true },
	var header: String? = null,
	var description: String? = null
) {
	open val fancyName get() = if (header != null && !header!!.isEmpty()) "$name [$header]" else "$name"

	fun header(header: String): Command {
		this.header = header
		return this
	}
	
	fun description(description: String): Command {
		this.description = description
		return this
	}
	
	fun condition(condition: (User) -> Boolean): Command {
		this.condition = condition
		return this
	}
	
	companion object {
		val ownerOnly: (User) -> Boolean = { it.id.value == Vars.ownerId }
		
		val adminOnly: (User) -> Boolean = { it.id.value in Vars.superusers }
	}
}
