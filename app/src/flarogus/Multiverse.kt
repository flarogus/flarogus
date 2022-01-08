package flarogus

import flarogus.*
import kotlin.concurrent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import dev.kord.common.entity.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.event.message.*
import dev.kord.core.entity.channel.*
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*

object Multiverse {

	/** All channels the multiverse works in */
	val multiverse = ArrayList<MessageChannel>(10)
	
	/** Sets up the multiverse */
	suspend fun start() {
		findChannels()
		
		Vars.client.launch {
			delay(5000L)
			brodcast { content = "***This channel is now a part of the Multiverse! There's ${multiverse.size} other channels!***" }
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
		
		Vars.client.events
			.filterIsInstance<MessageCreateEvent>()
			.filter { it.message.author?.id?.value != Vars.botId }
			.filter { it.message.channel.asChannel() in multiverse }
			.onEach {
				brodcast(it.message.channel.id.value) {
					content = "[${it.message.author?.tag} — ${it.getGuild()?.name}]: ${it.message.content}"
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
	
	/** Sends a message into every multiverse channel */
	inline fun brodcast(exclude: ULong = 0UL, crossinline message: suspend MessageCreateBuilder.() -> Unit) = Vars.client.launch {
		multiverse.forEach {
			if (exclude != it.id.value) {
				it.createMessage { message() }
			}
		}
	}
}