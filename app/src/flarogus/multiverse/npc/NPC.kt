package flarogus.multiverse.npc

import dev.kord.core.entity.*

/** Represents a multiversal NPC. */
abstract class NPC(open val cooldown: Long = 20000L) {
	abstract val name: String
	abstract val avatar: String
	
	abstract val dialog: RootNode
	
	var lastMessage = 0L
	
	/** Should be called when there's a multiversal message received */
	open fun messageReceived(message: Message) {
		TODO("implement npc")
	}
	
	/** If this method returns null, there is no reply */
	open fun processMessage(message: String): String? {
		val passed = System.currentTimeMillis() - lastMessage
		lastMessage = System.currentTimeMillis()
		
		return if (passed > cooldown) dialog.construct(message) else null
	}
}
