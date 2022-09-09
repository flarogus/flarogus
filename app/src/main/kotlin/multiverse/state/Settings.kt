package flarogus.multiverse.state

import java.io.*
import java.net.*
import kotlin.math.*
import kotlin.concurrent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import dev.kord.common.entity.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.event.message.*
import dev.kord.core.entity.*
import dev.kord.core.entity.channel.*
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import flarogus.*
import flarogus.util.*
import flarogus.multiverse.*
import flarogus.multiverse.state.*
import flarogus.multiverse.entity.*

object Settings {
	val settingsChannel by lazy { Vars.client.unsafe.messageChannel(Channels.settings) }
	var settingsMessage: Message? = null
	val fileStorageChannel by lazy { Vars.client.unsafe.messageChannel(Channels.fileStorage) }
	val json = Json {
		ignoreUnknownKeys = true
		encodeDefaults = true
	}

	/** Last uploaded state */
	var lastState: State? = null
	
	/** Tries to update the state. Retries up to [attempts - 1] times if an exception was thrown. */
	suspend fun updateState(attempts: Int = 3) {
		var attempt = 0
		while (true) {
			try {
				updateStateAttempt()
				break
			} catch (e: Exception) {
				if (++attempt >= attempts) throw e
			}
		}
	}
	
	/** Tries to update the state once */
	suspend fun updateStateAttempt() {
		settingsMessage = settingsChannel.messages.firstOrNull { (it.content.startsWith("http") || it.content == "placeholder") && it.data.author.id == Vars.botId }
		
		if (settingsMessage == null) {
			settingsMessage = settingsChannel.createMessage("placeholder")
		}
		
		Vars.restSupplier.getMessage(channelId = settingsMessage!!.channelId, messageId = settingsMessage!!.id).let {
			if (it.content.startsWith("http")) {
				val stateContent = downloadFromCdn<String>(it.content)
				val state = json.decodeFromString<State>(stateContent)
				
				if (state.ubid != Vars.ubid) {
					//load if the save is from older instance, shut down this instance if it's from newer, ignore otherwise
					if (state.startedAt > Vars.startedAt) {
						Multiverse.shutdown()
						Log.info { "multiverse instance ${Vars.ubid} is shutting down (newer state was detected)" }
						delay(5000L)
						Vars.client.logout()
						System.exit(0) // brutal
					} else {
						state.loadFromState()
					}
				}
			}
			
			val newState = State()
			if (newState != lastState) {
				val newStateEncoded = json.encodeToString(newState)
				val uploadedUrl = uploadToCdn(newStateEncoded)
			
				it.edit {
					content = uploadedUrl
				}

				lastState = newState
			}
		}
	}
	
	/** Uploads the providen string to cdn. Returns url of the uploaded file */
	suspend fun uploadToCdn(data: String) = uploadToCdn(ByteArrayInputStream(data.toByteArray()));
	
	/** Upload the contents of the input stream to cdn. returns url of the uploaded file */
	suspend fun uploadToCdn(data: InputStream): String {
		fileStorageChannel.createMessage {
			addFile("UPLOADED-DATA-${System.currentTimeMillis()}", data)
		}.also { return it.data.attachments.first().url }
	}
	
	/** Opposite of uploadToCdn(): downloads content from the uploaded file */
	inline suspend fun <reified T> downloadFromCdn(url: String) = Vars.client.resources.httpClient.get<HttpResponse>(url).receive<T>()

}

/** A utility class that stores the most important parts of bot's state. Used for serialization. */
@kotlinx.serialization.Serializable
data class State(
	val ubid: String = Vars.ubid,
	val startedAt: Long = Vars.startedAt,
	var runWhitelist: Set<Snowflake> = Vars.superusers,
	var epoch: Long = Vars.flarogusEpoch,
	var logLevel: Int = Log.level.level,

	var history: List<Multimessage> = Multiverse.history.takeLast(100),

	var users: List<MultiversalUser> = Multiverse.users,
	var guilds: List<MultiversalGuild> = Multiverse.guilds
) {
	/** Updates the current bot state in accordance with this state */
	fun loadFromState() {
		Vars.superusers.addAll(runWhitelist)
		Vars.flarogusEpoch = epoch
		Log.level = Log.LogLevel.of(logLevel)

		Multiverse.history.addAll(history)

		users.forEach { u ->
			Multiverse.users.removeAll { it.discordId == u.discordId }
			Multiverse.users.add(u)
		}
		guilds.forEach { g ->
			Multiverse.guilds.removeAll { it.discordId == g.discordId }
			Multiverse.guilds.add(g)
		}
	}
}
