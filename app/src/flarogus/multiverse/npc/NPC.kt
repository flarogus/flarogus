package flarogus.multiverse.npc

import dev.kord.core.entity.*

/** Represents a multiversal NPC. */
abstract class NPC {
	abstract val name: String
	abstract val avatar: String
	
	abstract val dialog: Node
	
	open fun messageReceived(message: Message) {
		TODO("implement npc")
	}
}
