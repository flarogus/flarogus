package flarogus.multiverse.state

import com.github.mnemotechnician.markov.*
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.Message
import flarogus.Vars
import flarogus.multiverse.*
import flarogus.multiverse.entity.MultiversalGuild
import flarogus.multiverse.entity.MultiversalUser
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.system.exitProcess

object StateManager {
	val stateChannel by lazy { Vars.client.unsafe.messageChannel(Channels.latestState) }
	var stateMessage: Message? = null
	val fileStorageChannel by lazy { Vars.client.unsafe.messageChannel(Channels.fileStorage) }
	val json = Json {
		ignoreUnknownKeys = true
		encodeDefaults = true
	}

	/** Last uploaded state */
	var lastState: State? = null
	/** Data storage for temporary scripts. */
	val arbitraryData = HashMap<String, String>()
	
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
		stateMessage = stateChannel.messages.firstOrNull {
			(it.content.startsWith("http") || it.content == "placeholder") && it.data.author.id == Vars.botId
		}
		
		if (stateMessage == null) {
			stateMessage = stateChannel.createMessage("placeholder")
		}
		
		Vars.restSupplier.getMessage(channelId = stateMessage!!.channelId, messageId = stateMessage!!.id).let {
			if (it.content.startsWith("http")) {
				val stateContent = downloadFromCdn<String>(it.content)
				val state = json.decodeFromString<State>(stateContent)
				
				if (state.ubid != Vars.ubid) {
					//load if the save is from older instance, shut down this instance if it's from newer, ignore otherwise
					if (state.startedAt > Vars.startedAt) {
						Multiverse.shutdown()
						Vars.commandHandler.shutdown()
						Log.info { "multiverse instance ${Vars.ubid} is shutting down (newer state was detected)" }
						delay(10000L)
						Vars.client.shutdown()
						exitProcess(0) // brutal
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
	suspend fun uploadToCdn(data: String) = uploadToCdn(ByteArrayInputStream(data.toByteArray()))

	/** Upload the contents of the input stream to cdn. returns url of the uploaded file */
	suspend fun uploadToCdn(data: InputStream): String {
		fileStorageChannel.createMessage {
			addFile("UPLOADED-DATA-${System.currentTimeMillis()}", data)
		}.also { return it.data.attachments.first().url }
	}
	
	/** Opposite of uploadToCdn(): downloads content from the uploaded file */
	suspend inline fun <reified T> downloadFromCdn(url: String) =
		Vars.client.resources.httpClient.get(url).body<T>()
}

/** A utility class that stores the most important parts of bot's state. Used for serialization. */
@kotlinx.serialization.Serializable
data class State(
	val ubid: String = Vars.ubid,
	val startedAt: Long = Vars.startedAt,
	var runWhitelist: Set<Snowflake> = Vars.superusers,
	var epoch: Long = Vars.flarogusEpoch,
	var logLevel: Int = Log.level.level,

	var history: List<Multimessage> = Multiverse.history.takeLast(150),

	var users: List<MultiversalUser> = Multiverse.users,
	var guilds: List<MultiversalGuild> = Multiverse.guilds
) {
	val multiverseLastInfoMessage = Multiverse.lastInfoMessage
	val serializedMarkov = Multiverse.markov.serializeToString()
	val arbitraryData = StateManager.arbitraryData

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

		Multiverse.lastInfoMessage = multiverseLastInfoMessage
		Multiverse.markov = MarkovChain.deserializeFromString(serializedMarkov)

		for ((k, v) in arbitraryData) {
			if (k !in StateManager.arbitraryData) {
				StateManager.arbitraryData[k] = v
			}
		}
	}
}
