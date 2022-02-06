package flarogus.commands

import kotlinx.coroutines.*;
import dev.kord.core.entity.*;
import dev.kord.core.event.message.*;

data class Command(
	val handler: suspend MessageCreateEvent.(List<String>) -> Unit,
	var condition: (User) -> Boolean = { true },
	var header: String? = null,
	var description: String? = null
) {
	fun setHeader(header: String): Command {
		this.header = header
		return this
	}
	
	fun setDescription(description: String): Command {
		this.description = description
		return this
	}
	
	fun setCondition(condition: (User) -> Boolean): Command {
		this.condition = condition
		return this
	}
}
