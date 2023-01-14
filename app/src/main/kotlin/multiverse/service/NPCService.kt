package flarogus.multiverse.service

import dev.kord.core.event.message.MessageCreateEvent
import flarogus.multiverse.npc.NPC
import flarogus.multiverse.Multiverse.MultiversalService

class NPCService(
	val npcs: List<NPC>
): MultiversalService() {
	override val name = "npc"

	override suspend fun onMessageReceived(event: MessageCreateEvent, retranslated: Boolean) {
		if (!retranslated) return
		npcs.forEach { it.multiversalMessageReceived(event.message) }
	}
}
