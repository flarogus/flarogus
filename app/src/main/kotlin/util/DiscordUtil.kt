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
import dev.kord.rest.request.KtorRequestException
import flarogus.*
import flarogus.multiverse.*

val ZERO_SNOWFLAKE = 0UL.toSnowflake()
val Snowflake.Companion.NONE get() = ZERO_SNOWFLAKE

val mentionRegex = "<[@#](!)?(\\d+)>".toRegex()
val userMentionRegex = "<[@](!)?(\\d+)>".toRegex()
val channelMentionRegex = "<[#](!)?(\\d+)>".toRegex()
val hypertextRegex = """\((.*)\)\[(https?://)([a-zA-Z.\-_]+/?)(.*)]""".toRegex()

val nameRegex = """^([.*])?(.*#\d\d\d\d) — .*""".toRegex()

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
fun String.revealHypertext() = this.replace(hypertextRegex, "(\$1)[\$2\$3\$4] (\$3)")

suspend fun String.explicitMentions(): String {
	var string = this
	var replaced = 0
	var match: MatchResult? = userMentionRegex.find(string)

	// replace all user mentions
	while (match != null && replaced++ < 3) {
		val replacement = Vars.supplier.getUserOrNull(match.value.toSnowflake())?.tag?.replace(mentionRegex, "[]") ?: ("<invalid user mention>")
		string = string.replaceRange(match.range, "@$replacement")
		match = userMentionRegex.find(string)
	}
	// ...and all channel mentions
	match = channelMentionRegex.find(string)
	while (match != null && replaced++ < 5) {
		val replacement = Vars.supplier.getChannelOrNull(match.value.toSnowflake())?.data?.name?.value?.replace(mentionRegex, "[]") ?: ("<invalid channel mention>")
		string = string.replaceRange(match.range, "#$replacement")
		match = channelMentionRegex.find(string)
	}

	return string
}

fun User.getAvatarUrl() = avatar?.url ?: "https://cdn.discordapp.com/embed/avatars/${discriminator.toInt() % 5}.png"

fun User?.isSuperuser() = this != null && this.id in Vars.superusers
fun User?.isModerator() = this != null && (this.id in Vars.moderators || isSuperuser())

/** Asynchronously eplies to a message */
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

/** Asynchronous reply based on a condition, useful for commands */
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
					addFile("image.png", it)
				}
			}
		}
	} catch (e: Exception) {
		println(e)
	}
}

const val minute = 60L
const val hour = 60L * minute
const val day = 24L * hour
const val month = 30L * day
const val year = 12L * month

fun formatTime(millis: Long): String {
	val time: Long = millis / 1000L;
	return buildString {
		fun part(div: Long, mod: Long, name: String) {
			val c = (time % mod) / div
			if (c <= 0L) return
			append(c).append(" ").append(name)
			if (c != 1L) append('s')
			append(", ")
		}
		part(year, Long.MAX_VALUE, "year")
		part(month, year, "month")
		part(day, month, "day")
		part(hour, day, "hour")
		part(minute, hour, "minute")

		append(time % 60).append(" second")
		if (time % 60 != 1L) append('s')
	}
}

/** 
 * Adds an embed referencing the original message
 * @param toChannel id of the channel this message is being sent to. 0 if not present.
 */
suspend fun MessageCreateBuilder.quoteMessage(message: Message?, toChannel: Snowflake?, titleText: String = "reply to") {
	if (message != null) {
		val author = User(message.data.author, Vars.client) //gotta construct one myself if the default api doesn't allow that
		val authorName = nameRegex.find(author.username)?.groupValues?.getOrNull(2) ?: "${author.username}#${author.discriminator}"

		// message in the same channel the new message is being sent to
		val multimessage = Multiverse.history.find { message in it }
		// first, check if the message is in the same channel as the current one. If it is not, try to find it in the multiverse.
		val closest = let {
			if (toChannel == message.channelId) {
				message
			} else {
				multimessage?.let {
					if (it.origin?.channelId == toChannel) it.origin else it.retranslated.find { it.channelId == toChannel }
				}?.asMessage()
			}
		}
		val closestChannel = closest?.getChannel()
		
		embed {
			url = if (closest != null) "https://discord.com/channels/${closestChannel?.data?.guildId?.value}/${closest.channelId}/${closest.id}" else null
			title = "$titleText $authorName" + if (closest != null) " (link)" else ""

			description = buildString {
				append(message.content.take(100).replace("/", "\\/"))
				if (message.content.length > 100) append("...")
				
				multimessage?.origin?.asMessage()?.attachments?.forEach {
					append('\n').append("file <").append(it.filename).append('>')
				}
			}.let { if (it.length > 200) it.take(200) + "..." else it }
			
			thumbnail { url = author.getAvatarUrl() }
		}
	}
}

/** Deletes all of the [messages] after [delay] milliseconds. [messages] can contain [Deferred]s. */
fun scheduleMessageRemoval(delay: Long, vararg messages: Any) = Vars.client.launch {
	delay(delay)
	messages.forEach {
		try {
			((if (it is Deferred<*>) it.await() else it) as? MessageBehavior)?.delete()
		} catch (_: KtorRequestException) {}
	}
}
