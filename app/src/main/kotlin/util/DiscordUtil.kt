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
import flarogus.multiverse.*

val mentionRegex = "<@(!)?(\\d+)>".toRegex()

fun ULong.toSnowflake() = Snowflake(this)
fun String.toSnowflakeOrNull(): Snowflake? = if (!startsWith("<")) {
	toULongOrNull()?.toSnowflake()
} else {
	mentionRegex.find(this)?.groupValues?.getOrNull(2)?.toULongOrNull()?.toSnowflake()
}
fun String.toSnowflake() = toSnowflakeOrNull() ?: throw NumberFormatException("invalid snowflake format")
fun Long.toSnowflake() = toULong().toSnowflake()

fun String.stripEveryone() = this.replace("@everyone", "@еveryonе").replace("@here", "@hеrе")
fun String.stripCodeblocks() = this.replace("```", "`'`")

fun Any?.isNotNull() = this != null

fun User.getAvatarUrl() = avatar?.url ?: "https://cdn.discordapp.com/embed/avatars/${discriminator.toInt() % 5}.png"

/** Waits up to [limit] ms for [condition] to become true. Returns true if the condition returned true, false if [limit] was reached. */
suspend inline fun delayUntil(limit: Long, period: Long = 50L, condition: () -> Boolean): Boolean {
	if (condition()) return true

	val begin = System.currentTimeMillis()
	do {
		delay(period)
		if (condition()) return true
	} while (System.currentTimeMillis() < begin + limit)

	return false
}

/** Replies to a message */
@Deprecated("This was created at the very beginning of the flarogus development. Use 'origin.replyWith(message)' instead.", ReplaceWith("origin.replyWith(message)"), DeprecationLevel.ERROR)
fun replyWith(origin: MessageBehavior, message: CharSequence) = origin.replyWith(message.toString())

/** Replies to a message */
fun MessageBehavior.replyWith(message: String) = Vars.client.async {
	try {
		channel.createMessage {
			content = message.take(1999).stripEveryone()
			messageReference = this@replyWith.id
		}
	} catch (ignored: Exception) {
		null
	}
}

/** Reply based on condition, useful for commands */
fun MessageBehavior.replyWithResult(
	condition: Boolean,
	success: String = "success.",
	fail: String = "fail."
) = replyWith(if (condition) success else fail)

/** Same as replyWithResult but uses infix notation and doesn't support different messages */
infix fun Boolean.sendResultTo(message: MessageBehavior) = message.replyWithResult(this)

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
@Deprecated("dumbass")
suspend fun userOrNull(uid: String?): User? {
	if (uid == null || uid.isEmpty()) {
		return null
	}
	if (uid.startsWith("<@")) {
		val id = "<@(\\d*)>".toRegex().find(uid)?.groupValues?.getOrNull(1)
		if (id != null) {
			return userOrNull(id)
		}
	}
	return Vars.supplier.getUserOrNull(Snowflake(uid.toULong()))
}

/** Tries to find a user by mention/userid, returns the author in case of error. May return null if it's a system message */
@Deprecated("dumbass")
suspend fun userOrAuthor(uid: String?, event: MessageCreateEvent): User? {
	return userOrNull(uid) ?: event.message.author
}

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
@Deprecated("dumbass", level = DeprecationLevel.ERROR)
inline suspend fun fetchMessages(channel: MessageChannelBehavior, crossinline handler: suspend (Message) -> Unit) {
	try {
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

/** 
 * Adds an embed referencing the original message
 * @param toChannel id of the channel this message is sent to. 0 if not present.
 */
suspend fun MessageCreateBuilder.quoteMessage(message: Message?, toChannel: Snowflake?, titleText: String = "reply to") {
	if (message != null) {
		val author = User(message.data.author, Vars.client) //gotta construct one myself if the default api doesn't allow that
		val authorName = nameRegex.find(author.username)?.groupValues?.getOrNull(2) ?: "${author.username}#${author.discriminator}"

		//message in the same channel the new message is being sent to
		val closest = Multiverse.history.find { message in it }?.let {
			if (it.origin?.channelId == toChannel) it.origin else it.retranslated.find { it.channelId == toChannel }
		}?.asMessage()
		val closestChannel = closest?.getChannel()
		
		embed {
			url = if (closest != null) "https://discord.com/channels/${closestChannel?.data?.guildId?.value}/${closest.channelId}/${closest.id}" else null
			title = "$titleText ${authorName}" + if (closest != null) " (link)" else ""

			description = buildString {
				append(message.content.take(100).replace("/", "\\/"))
				if (message.content.length > 100) append("...")
				
				message.attachments.forEach {
					append('\n').append("file <").append(it.filename).append('>')
				}
			}.let { if (it.length > 200) it.take(200) + "..." else it }
			
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
