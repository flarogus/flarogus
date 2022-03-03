package flarogus.util

import java.io.*
import java.awt.image.*
import javax.imageio.*
import kotlinx.coroutines.*;
import kotlinx.coroutines.flow.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.cache.data.*
import dev.kord.core.entity.*;
import dev.kord.core.*
import dev.kord.core.event.*
import dev.kord.core.event.message.*
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import dev.kord.common.entity.*
import flarogus.*

/** Sends a message, waits delayMs, edits it to the output of the lambda */
fun sendEdited(origin: MessageBehavior, message: String, delayMs: Long = 0, newMessage: (String) -> String) = Vars.client.launch {
	try {
		val msg = origin.channel.createMessage(message.take(1999).stripEveryone())
		if (delayMs > 0) delay(delayMs)
		msg.edit { content = newMessage(message).take(1999).stripEveryone() }
	} catch (e: Exception) {
		println(e)
	}
};

/** Replies to the message, optionally deletes the message in selfdestructIn ms */
fun replyWith(origin: MessageBehavior, message: String, selfdestructIn: Long = -1) = Vars.client.launch {
	try {
		val msg = origin.channel.createMessage {
			content = message.take(1999).stripEveryone()
			messageReference = origin.id
		}
		if (selfdestructIn > 0L) {
			delay(selfdestructIn)
			msg.delete()
		}
	} catch (e: Exception) {
		println(e)
	}
}

/** Sends the message into the same channel the original message was sent into */
/*fun sendMessage(to: MessageBehavior, content: String) = Vars.client.launch {
	try {
		to.channel.createMessage(content.take(1999).stripEveryone())
	} catch (e: Exception) {
		println(e)
	}
}*/

fun sendImage(origin: MessageBehavior, text: String = "", image: BufferedImage) = Vars.client.launch {
	try {
		ByteArrayOutputStream().use {
			ImageIO.write(image, "png", it);
			ByteArrayInputStream(it.toByteArray()).use {
				origin.channel.createMessage {
					content = text.take(1999).stripEveryone()
					messageReference = origin.id
					addFile("SUSSUSUSUSUSUSUSUSU.png", it)
				}
			}
		}
	} catch (e: Exception) {
		println(e)
	}
}


/** Tries to find the user by uid / mention, returns null in case of an error */
suspend fun userOrNull(uid: String?, event: MessageCreateEvent): User? {
	if (uid == null || uid.isEmpty()) {
		return null
	}
	if (uid.startsWith("<@")) {
		val id = "<@(\\d*)>".toRegex().find(uid)?.groupValues?.getOrNull(1)
		if (id != null) {
			return userOrNull(id, event)
		}
	}
	try {
		return event.supplier.getUser(Snowflake(uid.toULong()))
	} catch (e: Exception) {
		return null
	}
}

/** Tries to find a user by mention/userid, returns the author in case of error. May return null if it's a system message */
suspend fun userOrAuthor(uid: String?, event: MessageCreateEvent): User? {
	return userOrNull(uid, event) ?: event.message.author
}

fun User.getAvatarUrl() = avatar?.url ?: "https://cdn.discordapp.com/embed/avatars/${discriminator.toInt() % 5}.png"

fun String.stripEveryone() = this.replace("@everyone", "@еveryonе").replace("@here", "@hеrе")

fun countPings(string: String): Int {
	var r = "<@(!)?\\d+>".toRegex().find(string)
	if (r != null) {
		var c = 0;
		while (true) {
			r = r?.next() ?: break
			c++
		}
		return c
	}
	return 0
}

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

/** Utility function: calls the specified function for every message in the channel. Catches and ignores any exceptions, Errors can be used to stop execution */
inline suspend fun fetchMessages(channelId: Snowflake, crossinline handler: suspend (Message) -> Unit) {
	try {
		val channel = Vars.client.unsafe.messageChannel(channelId)
		
		channel.messages
			.onEach {
				try {
					handler(it)
				} catch (e: Exception) { //any invalid messages are ignored. this includes number format exceptions, nullpointers and etc
					//e.printStackTrace()
				}
			}.collect()
	} catch (e: Throwable) { //this one should catch Error subclasseses too
		//e.printStackTrace()
	}
};

val nameRegex = """^([.*])?(.*#\d\d\d\d) — .*""".toRegex()

/** Adds an embed referencing the original message */
fun MessageCreateBuilder.quoteMessage(message: Message?) {
	if (message != null) {
		val author = User(message.data.author, Vars.client) //gotta construct one myself if the default api doesn't allow that
		val authorName = nameRegex.find(author.username)?.groupValues?.getOrNull(2) ?: "${author.username}#${author.discriminator}"
		
		embed {
			title = "(reply to ${authorName})"
			description = buildString {
				val replyOrigin = "(> .+\n)?((?s).+)".toRegex().find(message.content)?.groupValues?.getOrNull(2)?.replace('\n', ' ') ?: "<no content>"
				append(replyOrigin.take(100).replace("/", "\\/"))
				if (replyOrigin.length > 100) append("...")
			}
			
			thumbnail { url = author.getAvatarUrl() }
		}
	}
}

/** Creates a message with different content */
fun fakeMessage(message: Message, newContent: String) = with(message.data) {
	Message(MessageData(
		id, channelId, guildId, author, 
		newContent, timestamp, editedTimestamp, tts,
		mentionEveryone, mentions, mentionRoles, mentionedChannels,
		attachments, embeds, reactions, nonce,
		pinned, webhookId, type, activity,
		application, applicationId, messageReference, flags,
		stickers, referencedMessage, interaction
	), message.kord)
};
