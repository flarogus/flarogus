package flarogus.util

import kotlinx.coroutines.*;
import dev.kord.core.entity.*;
import dev.kord.core.*
import dev.kord.core.event.*
import dev.kord.core.event.message.*
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import dev.kord.common.entity.*

/** Sends a message, waits delayMs, edits it to the output of the lambda */
fun CoroutineScope.sendEdited(origin: Message, message: String, delayMs: Long = 0, newMessage: (String) -> String) = launch {
	try {
		val msg = origin.channel.createMessage(message)
		if (delayMs > 0) delay(delayMs)
		msg.edit { content = newMessage(message) }
	} catch (e: Throwable) {
		println(e)
	}
}

/** Replies to the message, optionally deletes the message in selfdestructIn ms */
fun CoroutineScope.replyWith(origin: Message, message: String, selfdestructIn: Long? = null) = launch {
	try {
		val msg = origin.channel.createMessage {
			content = message
			messageReference = origin.id
		}
		if (selfdestructIn != null) {
			delay(selfdestructIn)
			msg.delete()
		}
	} catch (e: Throwable) {
		println(e)
	}
}

/** Sends the message into the same channel the original message was sent into */
suspend fun CoroutineScope.sendMessage(to: Message, content: String) = launch {
	try {
		to.channel.createMessage(content)
	} catch (e: Throwable) {
		println(e)
	}
}

/** Tries to find the user by uid, returns the author of the event in case of an error */
suspend fun userOrAuthor(uid: String?, event: MessageCreateEvent): User? {
	if (uid == null || uid.isEmpty()) {
		return event.message.author
	}
	try {
		return event.supplier.getUser(Snowflake(uid.toULong()))
	} catch (e: Exception) {
		return event.message.author
	}
}

fun User.getRealAvatar() = this.avatar ?: this.defaultAvatar