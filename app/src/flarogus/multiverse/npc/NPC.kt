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
	val history = ArrayList<List<WebhookMessageBehavior>>(30)
	
	var lastProcessed: Message? = null
	
	/** Should be called when there's a multiversal message received */
	open fun multiversalMessageReceived(message: Message) {
		val origin = buildString {
			append(message.content)
			message.attachments.forEach { append('\n').append(it.filename) }
		}.lowercase()
		
		val timePassed = System.currentTimeMillis() - lastMessage
		
		if (timePassed > cooldown && !origin.isEmpty()) {
			lastProcessed = message
			val reply = processMessage(origin)
			
			if (reply != null) Vars.client.launch {
				lastMessage = System.currentTimeMillis() + replyDelay
				delay(replyDelay)
				
				sendMessage(reply, message)
			}
		}
	}
	
	open suspend fun sendMessage(message: String, reference: Message?) {
		Multiverse.brodcast(0UL, obtainUsertag(), avatar) {
			content = message
			if (reference != null) quoteMessage(reference)
		}.also { history.add(it) }
	}
	
	/** If this method returns null, there is no message */
	open fun processMessage(message: String): String? {
		return dialog.construct(message).let { if (it.isEmpty()) null else it }
	}
	
	open fun obtainUsertag(): String {
		val hash = name.hashCode() xor 0b010101010101
		val discriminator = hash.toString().padStart(4, '0').takeLast(4)
		return "$name#$discriminator — $location"
	}
	
	/** This method assumes that the message was sent via a webhook (just like a normal multiversal message) */
	open fun isOwnMessage(message: Message?) = message != null && message.data.author.username.contains(obtainUsertag())
}
