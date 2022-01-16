package flarogus

import java.net.*
import kotlin.concurrent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import dev.kord.common.entity.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.event.message.*
import dev.kord.core.entity.channel.*
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import flarogus.*

object Multiverse {

	/** All channels the multiverse works in */
	val multiverse = ArrayList<MessageChannel>(10)
	
	/** Sets up the multiverse */
	suspend fun start() {
		findChannels()
		
		Vars.client.launch {
			delay(5000L)
			brodcast { content = "***This channel is now a part of the Multiverse! There's ${multiverse.size - 1} other channels!***" }
		}
		
		//search for new channels every 30 seconds
		fixedRateTimer("channel search", true, period = 30 * 1000L) {
			val lastSize = multiverse.size
			findChannels()
			
			val newChannels = multiverse.size - lastSize
			if (newChannels > 0) brodcast {
				content = buildString {
					append("***Looks like there's ")
					append(newChannels)
					append(" new channel")
					if (newChannels > 1) append("s")
					append(" in the multiverse!***")
				}
			}
		}
		
		//retranslate any messages in multiverse channels
		Vars.client.events
			.filterIsInstance<MessageCreateEvent>()
			.filter { it.message.author?.id?.value != Vars.botId }
			.filter { it.message.channel.asChannel() in multiverse }
			.onEach { event ->
				try {
					brodcast(event.message.channel.id.value) {
						val original = event.message.content.replace("@everyone", "@еveryonе").replace("@here", "@hеrе") //russian in e's the second case
						val author = event.message.author?.tag ?: "webhook <${event.supplier.getWebhookOrNull(event.message.webhookId ?: Snowflake(0))?.name}>"
						content = "[${author} — ${event.getGuild()?.name}]: ${original.take(1800)}"
						
						event.message.data.attachments.forEachIndexed { index, attachment ->
							addFile(attachment.filename, URL(attachment.url).openStream())
						}
					}
				} catch (e: Exception) {
					e.printStackTrace()
				}
			}
			.launchIn(Vars.client)
	}
	
	/** Searches for channels with "multiverse" in their names in all guilds this bot is in */
	fun findChannels() = Vars.client.launch {
		Vars.client.rest.user.getCurrentUserGuilds().forEach { 
			Vars.client.unsafe.guild(it.id).asGuild().channelBehaviors.forEach {
				var c = it.asChannel()
				
				if (c.data.type == ChannelType.GuildText && c.data.name.toString().contains("multiverse")) {
					if (!multiverse.any { it.id.value == c.id.value }) {
						multiverse += TextChannel(c.data, Vars.client)
					}
				}
			}
		}
	}
	
	/** Sends a message into every multiverse channel expect the one with id == exclude */
	inline fun brodcast(exclude: ULong = 0UL, crossinline message: suspend MessageCreateBuilder.() -> Unit) = Vars.client.launch {
		multiverse.forEach {
			if (exclude != it.id.value) {
				try {
					it.createMessage { message() }
				} catch (e: Exception) {
					e.printStackTrace()
				}
			}
		}
	}
}