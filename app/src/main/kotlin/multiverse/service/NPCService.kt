package flarogus.multiverse.services

import flarogus.multiverse.npc.NPC
import flarogus.multiverse.Multiverse.MultiversalService

class NPCService(
	val npcs: ArrayList<NPC>
): MultiversalService {
	override fun fun onMessage(event: MessageCreateEvent, retranslated: Boolean) {
		if (!retranslated) return
		npcs.forEach { it.multiversalMessageReceived(event.message) }
	}
}
