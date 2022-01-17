package flarogus

import java.net.*
import kotlin.concurrent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import dev.kord.common.entity.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.event.message.*
import dev.kord.core.entity.*
import dev.kord.core.entity.channel.*
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import flarogus.*
import flarogus.util.*

/**
 * Retranslates messages sent in any channel of guild network, aka Multiverse, into other multiverse channels
 *
 * The bot is hosted on github actions and it's really hard to make it save the state.
 * Thus, multiverse settings are stored on a discord server.
 */
object Multiverse {

	/** All channels the multiverse works in */
	val multiverse = ArrayList<MessageChannel>(10)
	/** Guilds that are allowed to send messages in multiverse */
	val whitelist = ArrayList<Snowflake>(50)
	/** Guilds / users that are blacklisted from multiverse */
	val blacklist = ArrayList<Snowflake>(50)
	/** Custom user tags */
	val usertags = HashMap<Snowflake, String>(50)
	
	val whitelistChannel = 932632370354475028UL
	val blacklistChannel = 932524242707308564UL
	val usertagsChannel = 932690515667849246UL
	
	/** Sets up the multiverse */
	suspend fun start() {
		findChannels()
		
		Vars.client.launch {
			delay(10000L)
			brodcast { content = "***This channel is now a part of the Multiverse! There's ${multiverse.size - 1} other channels!***" }
		}
		
		//search for new channels every 30 seconds
		fixedRateTimer("channel search", true, initialDelay = 5000L, period = 45 * 1000L) {
			findChannels()
			
			//add blacklisted users and guilds
			fetchMessages(blacklistChannel) {
				if (!it.content.startsWith("g") && !it.content.startsWith("u")) return@fetchMessages
				
				val id = "[ug](\\d+)".toRegex().find(it.content)!!.groupValues[1].toULong()
				
				val snow = Snowflake(id)
				if (snow !in blacklist) blacklist.add(snow)
			}
			
			//add whitelisted guilds
			fetchMessages(whitelistChannel) {
				val id = it.content.trim().toULong()
					
				val snow = Snowflake(id)
				if (snow !in whitelist) whitelist.add(snow)
			}
			
			//find user tags
			fetchMessages(usertagsChannel) {
				val groups = "(\\d+):(.+)".toRegex().find(it.content)!!.groupValues //null pointer is acceptable
				
				val id = Snowflake(groups[1].toULong())
				val tag = groups[2].trim()
				usertags[id] = tag
			}
		}
		
		//retranslate any messages in multiverse channels
		Vars.client.events
			.filterIsInstance<MessageCreateEvent>()
			.filter { it.message.author?.id?.value != Vars.botId }
			.filter { it.message.channel.asChannel() in multiverse }
			.onEach { event ->
				val guild = event.getGuild()
				if (guild?.id in blacklist || guild?.id !in whitelist || event.message.author?.id in blacklist) return@onEach
				
				try {
					if (countPings(event.message.content) > 7) {
						blacklist(event.message.author!!.id)
						return@onEach
					}
					
					brodcast(event.message.channel.id.value) {
						val userid = event.message.author?.id
						val original = event.message.content
						val author = event.message.author?.tag ?: "webhook <${event.supplier.getWebhookOrNull(event.message.webhookId ?: Snowflake(0))?.name}>"
						val customTag = usertags.getOrDefault(userid, null)
						
						content = buildString {
							if (customTag != null) {
								append('[')
								append(customTag)
								append(']')
							} else if (userid?.value in Vars.runWhitelist) {
								append("[Admin]")
							}
							
							append("[")
							append(author)
							append(" — ")
							append(guild?.name)
							append("]: ")
							append(original.take(1800))
						}
						
						event.message.data.attachments.forEachIndexed { index, attachment ->
							addFile(attachment.filename, URL(attachment.url).openStream())
						}
					}
				} catch (e: Exception) {
					e.printStackTrace()
				}
			}.launchIn(Vars.client)
	}
	
	/** Searches for channels with "multiverse" in their names in all guilds this bot is in */
	fun findChannels() = Vars.client.launch {
		Vars.client.rest.user.getCurrentUserGuilds().forEach { 
			if (it.name.contains("@everyone") || it.name.contains("@here")) {
				//instant blacklist, motherfucker
				blacklist.add(it.id)
				return@forEach
			}
			
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
	
	/** Utility function: asynchronously calls the specified function for every message in the channel. Catches and ignores any exceptions, Errors can be used to stop execution */
	inline fun fetchMessages(channel: ULong, crossinline handler: (Message) -> Unit) = Vars.client.launch {
		try {
			val channel = Vars.client.unsafe.messageChannel(Snowflake(channel))
			
			channel.messages
				.onEach {
					try {
						handler(it)
					} catch (e: Exception) { //any invalid messages are ignored. this includes number format exceptions, nullpointers and etc
						e.printStackTrace()
					}
				}.collect()
		} catch (e: Throwable) { //this one should catch Error subclasseses too
			e.printStackTrace()
		}
	}
	
	/** Adds an id to the blacklist and sends a message in the blacklist channel (to save the entry) */
	fun blacklist(id: Snowflake) = Vars.client.launch {
		 try {
		 	blacklist.add(id)
		 	
		 	Vars.client.unsafe.messageChannel(Snowflake(blacklistChannel)).createMessage {
		 		content = "g${id.value}"
		 	}
		 } catch (ignored: Exception) {}
	}
	
	/** Sends a message into every multiverse channel expect blacklisted and the one with id == exclude  */
	inline fun brodcast(exclude: ULong = 0UL, crossinline message: suspend MessageCreateBuilder.() -> Unit) = Vars.client.launch {
		multiverse.forEach {
			if (exclude != it.id.value && it.id !in blacklist) {
				try {
					it.createMessage {
						message()
						
						content = content?.stripEveryone()
					}
				} catch (e: Exception) {
					e.printStackTrace()
				}
			}
		}
	}
	
}