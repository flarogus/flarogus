package flarogus.multiverse.npc

import kotlinx.coroutines.*
import dev.kord.core.entity.*
import flarogus.*
import flarogus.util.*
import flarogus.multiverse.*

/** Represents a multiversal NPC. */
abstract class NPC(open val cooldown: Long = 20000L, open val replyDelay: Long = 10000L) {
	abstract val name: String
	abstract val location: String
	abstract val avatar: String
	
	abstract val dialog: RootNode
	
	var lastMessage = 0L
	
	var lastProcessed: Message? = null
	
	/** Should be called when there's a multiversal message received */
	open fun multiversalMessageReceived(message: Message) {
		val origin = buildString {
			append(message.content)
			message.attachments.forEach { append('\n').append(it.filename) }
		}.lowercase()
		
		if (!origin.isEmpty()) {
			lastProcessed = message
			val reply = processMessage(origin)
			
			if (reply != null) Vars.client.launch {
				delay(replyDelay)
				
				Multiverse.brodcast(0UL, obtainUsertag(), avatar) {
					content = reply
					quoteMessage(message)
				}
			}
		}
	}
	
	/** If this method returns null, there is no reply */
	open fun processMessage(message: String): String? {
		val passed = System.currentTimeMillis() - lastMessage
		lastMessage = System.currentTimeMillis()
		
		return if (passed > cooldown) {
			dialog.construct(message).let { if (it.isEmpty()) null else it }
		} else {
			null
		}
	}
	
	open fun obtainUsertag(): String {
		val hash = name.hashCode() xor 0b010101010101
		val discriminator = hash.toString().takeLast(4).padStart(4, '0')
		return "$name#$discriminator — $location"
	}
}
