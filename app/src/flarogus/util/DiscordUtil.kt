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
	} catch (e: Exception) {
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
	} catch (e: Exception) {
		println(e)
	}
}

/** Sends the message into the same channel the original message was sent into */
suspend fun CoroutineScope.sendMessage(to: Message, content: String) = launch {
	try {
		to.channel.createMessage(content)
	} catch (e: Exception) {
		println(e)
	}
}

/** Tries to find the user by uid, returns the author of the event in case of an error */
suspend fun userOrAuthor(uid: String?, event: MessageCreateEvent): User? {
println(uid)
	if (uid == null || uid.isEmpty()) {
		return event.message.author
	}
	try {
		return event.supplier.getUser(Snowflake(uid.toULong()))
	} catch (e: Exception) {
		return event.message.author
	}
}

fun User.getAvatarUrl() = avatar?.url ?: "https://cdn.discordapp.com/embed/avatars/${discriminator.toInt() % 5}.png"

const val minute = 60L
const val hour = 60L * minute
const val day = 24L * hour
const val month = 30L * day
const val year = 12L * month

fun formatTime(millis: Long): String {
	val time: Long = millis / 1000L;
	return buildString {
		if (time >= year) {
			val c = time / year
			append(c)
			append(" year")
			if (c != 1L) append('s')
			append(", ")
		}
		if (time >= month) {
			val c = (time % year) / month
			append(c)
			append(" month")
			if (c != 1L) append('s')
			append(", ")
		}
		if (time >= day) {
			val c = (time % month) / day
			append(c)
			append(" day")
			if (c != 1L) append('s')
			append(", ")
		}
		if (time >= hour) {
			val c = (time % day) / hour
			append(c)
			append(" hour")
			if (c != 1L) append('s')
			append(", ")
		}
		if (time >= minute) {
			val c = (time % hour) / minute
			append(c)
			append(" minute")
			if (c != 1L) append('s')
			append(", ")
		}
		
		val c = time % 60
		append(c)
		append(" second")
		if (c != 1L) append('s')
	}
}