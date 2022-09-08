package flarogus.multiverse

import kotlin.time.*
import kotlin.random.*
import kotlin.concurrent.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import dev.kord.common.entity.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.event.message.*
import dev.kord.core.entity.*
import dev.kord.core.entity.channel.*
import flarogus.*
import flarogus.util.*
import flarogus.multiverse.state.*
import flarogus.multiverse.npc.impl.*
import flarogus.multiverse.entity.*
import kotlin.time.Duration.Companion.seconds

/**
 * Manages the retraslantion of actions performed in one channel of the multiverse, aka a guild network,
 * into other channels.
 */
@OptIn(ExperimentalTime::class)
object Multiverse {
	/** Array containing all messages sent in this instance */
	val history = ArrayList<Multimessage>(1000)
	
	/** Files with size exceeding this limit will be sent in the form of links */
	val maxFileSize = 1024 * 1024 * 1
	
	val webhookName = "MultiverseWebhook"
	val systemName = "Multiverse"
	val systemAvatar = "https://drive.google.com/uc?export=download&id=1JxmvN2hp4F7pFIOwYj17AO5lXAh7HReo"
	
	/** If false, new messages will be ignored */
	var isRunning = false

	val npcs = mutableListOf(AmogusNPC())

	val users = ArrayList<MultiversalUser>(90)
	val guilds = ArrayList<MultiversalGuild>(30)

	val rootJob = SupervisorJob()
	val scope = CoroutineScope(rootJob)
	val pendingActions = ArrayList<PendingAction<*>>(50)
	
	/** Sets up the multiverse */
	suspend fun start() {
		Log.setup()
		Settings.updateState()
		
		setupEvents()
		findChannels()
		
		scope.launch {
			delay(40000L)
			val channels = guilds.fold(0) { v, it -> if (!it.isForceBanned) v + it.channels.size else v }
			broadcastSystem {
				embed { description = """
					***This channel is a part of the Multiverse. There's ${channels - 1} other channels.***
					Some of the available commands: 
					    - `!flarogus multiverse rules` - see the rules
					    - `!flarogus report` - report an issue or contact the admins
					    - `!flarogus multiverse help` - various commands
				""".trimIndent() }
			}
		}
		
		isRunning = true

		fixedRateTimer("update state", true, initialDelay = 5 * 1000L, period = 180 * 1000L) {
			runBlocking { findChannels() }
		}
		fixedRateTimer("update settings", true, initialDelay = 5 * 1000L, period = 20 * 1000L) {
			//random delay is to ensure that there will never be situations when two instances can't detect each other
			scope.launch {
				delay(Random.nextLong(0L, 5000L))
				Settings.updateState()
			}
		}

		scope.launch {
			while (isActive) {
				if (pendingActions.isNotEmpty()) {
					val action = synchronized(pendingActions) { pendingActions.removeFirst() }
					action()
				}
				delay(50L) // a little delay to allow to send other messages outside the multiverse
			}
		}
	}
	
	/** Shuts the multiverse down, but allows to restart it later. */
	fun shutdown() {
		isRunning = false
		rootJob.children.forEach { it.cancel() }
	}
	
	suspend fun messageReceived(event: MessageCreateEvent) {
		if (!isRunning || isOwnMessage(event.message)) return
		if (!guilds.any { it.channels.any { it.id == event.message.channel.id } }) return
		if (event.message.type.let {
			it !is MessageType.Unknown 
			&& it !is MessageType.Default 
			&& it !is MessageType.Reply
		}) {
			event.message.replyWith("This message type (${event.message.type}) is not supported by the multiverse.")
			return
		}
		
		val user = userOf(event.message.data.author.id)
		user?.onMultiversalMessage(event) ?: event.message.replyWith("No user associated with your user id was found!")

		npcs.forEach { it.multiversalMessageReceived(event.message) }
	};

	private fun setupEvents() {
		Vars.client.events
			.filterIsInstance<MessageDeleteEvent>()
			.filter { isRunning }
			.filter { event -> !isRetranslatedMessage(event.messageId) }
			.filter { event -> isMultiversalChannel(event.channelId) }
			.filter { event -> event.guildId != null && guildOf(event.guildId!!).let { it != null && !it.isForceBanned } }
			.onEach { event ->
				var multimessage: Multimessage? = null
				delayUntil(20000L, 500L) { history.find { it.origin?.id == event.messageId }.also { multimessage = it } != null }

				try {
					if (multimessage != null) {
						multimessage!!.delete(false) //well...
						Log.info { "Message ${event.messageId} was deleted by deleting the original message" }
						history.remove(multimessage!!)
					}
				} catch (e: Exception) {
					Log.error { "An exception has occurred while trying to delete a multiversal message ${event.messageId}: $e" }
				}
			}
			.launchIn(Vars.client)
		
		//can't check the guild here
		Vars.client.events
			.filterIsInstance<MessageUpdateEvent>()
			.filter { isRunning }
			.filter { event -> !isRetranslatedMessage(event.messageId) }
			.filter { event -> isMultiversalChannel(event.message.channel.id) }
			.onEach { event ->
				var multimessage: Multimessage? = null
				delayUntil(20000L, 500L) { history.find { it.origin?.id == event.messageId }.also { multimessage = it } != null }

				try {
					if (multimessage != null) {
						val origin = multimessage!!.origin?.asMessage()
						val newContent = buildString {
							appendLine(event.new.content.value ?: multimessage!!.origin!!.asMessage().content)
							origin?.attachments?.forEach { attachment ->
								if (attachment.size >= maxFileSize) {
									appendLine(attachment.url)
								}
							}
						}

						multimessage!!.edit(false) {
							content = newContent
						}

						Log.info { "Message ${multimessage!!.origin?.id} was edited by it's author" }
					}
				} catch (e: Exception) {
					Log.error { "An exception has occurred while trying to edit a multiversal message ${event.messageId}: $e" }
				}
			}
			.launchIn(Vars.client)
	}
	
	/** Searches for channels with "multiverse" in their names in all guilds this bot is in */
	suspend fun findChannels() {
		Vars.client.rest.user.getCurrentUserGuilds().forEach {
			guildOf(it.id)?.update() //this will add an entry if it didn't exist
		}
	};

	fun addAction(action: PendingAction<*>) {
		synchronized(pendingActions) {
			pendingActions.add(action)
		}
	}

	/**
	 * Sends a message into every multiversal channel.
	 * Accepts username and pfp url parameters.
	 * Automatically adds the multimessage to the history.
	 *
	 * @return The multimessage containing all created messages but no origin.
	 */
	fun broadcastAsync(
		user: String? = null,
		avatar: String? = null,
		filter: (TextChannel) -> Boolean = { true },
		messageBuilder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
	) = SendMessageAction(user, avatar, filter, messageBuilder).also {
		addAction(it)
	}

	/** Samw as [broadcastAsync], except that it awaits for the result. */
	suspend fun broadcast(
		user: String? = null,
		avatar: String? = null,
		filter: (TextChannel) -> Boolean = { true },
		messageBuilder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
	) = broadcastAsync(user, avatar, filter, messageBuilder).await()

	/** See [broadcastSystemAsync]. */
	suspend fun broadcastSystem(
		message: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
	) = broadcastSystemAsync(message).await()

	/** Same as [broadcastAsync] but uses the system pfp & name */
	fun broadcastSystemAsync(
		message: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
	) = broadcastAsync(systemName, systemAvatar, { true }, message)
	
	/** Returns a MultiversalUser with the given id, or null if it does not exist */
	suspend fun userOf(id: Snowflake): MultiversalUser? = users.find { it.discordId == id } ?: let {
		MultiversalUser(id).also { it.update() }.let {
			if (it.isValid) it.also { users.add(it) } else null
		}
	}

	/** Returns a MultiversalGuild with the given id, or null if it does not exist */
	suspend fun guildOf(id: Snowflake): MultiversalGuild? = guilds.find { it.discordId == id } ?: let {
		MultiversalGuild(id).also { it.update() }.let { 
			if (it.isValid) it.also { guilds.add(it) } else null
		}
	}

	/** Returns whether this message was sent by flarogus */
	fun isOwnMessage(message: Message): Boolean {
		return (message.author?.id == Vars.botId) || (message.webhookId != null && isMultiversalWebhook(message.webhookId!!))
	}

	/** Returns whether this channel belongs to the multiverse. Does not guarantee that it is not banned. */
	fun isMultiversalChannel(channel: Snowflake) = guilds.any { it.channels.any { it.id == channel } }

	/** Returns whether this webhook belongs to the multiverse. */
	fun isMultiversalWebhook(webhook: Snowflake) = guilds.any { it.webhooks.any { it.id == webhook } }

	/** Returns whether a message with this id is a retranslated message */
	fun isRetranslatedMessage(id: Snowflake) = history.any { it.retranslated.any { it.id == id } }

	abstract class PendingAction<T>(timeLimitSeconds: Int) {
		protected val job = Job(rootJob)
		val timeLimit = timeLimitSeconds * 1000L
		var cachedResult: Deferred<T?>? = null
			protected set

		operator fun invoke(): Deferred<T?>  {
			if (cachedResult != null) return cachedResult!!
			return scope.async {
				try {
					return@async withTimeout(timeLimit) {
						execute()
					}
				} catch (e: TimeoutCancellationException) {
					Log.error { "Timed out while executing $this: $e" }
				} catch (e: Exception) {
					Log.error { "Uncaught exception while executing $this: $e" }
				} finally {
					job.complete()
				}
				null
			}.also { cachedResult = it }
		}

		@OptIn(ExperimentalCoroutinesApi::class)
		suspend fun await(): T? {
			job.join()
			return cachedResult?.getCompleted()
		}

		protected abstract suspend fun execute(): T
	}

	open class SendMessageAction(
		val user: String? = null,
		val avatar: String? = null,
		val filter: (TextChannel) -> Boolean = { true },
		val messageBuilder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
	) : PendingAction<Multimessage>(45) {
		override suspend fun execute(): Multimessage {
			val messages = ArrayList<WebhookMessageBehavior>(guilds.size)

			guilds.forEach {
				it.send(
					username = user,
					avatar = avatar,
					filter = filter,
					handler = { m, w -> messages.add(WebhookMessageBehavior(w, m)) },
					builder = messageBuilder
				)
				job.ensureActive()
				yield()
			}

			val multimessage = Multimessage(null, messages)
			history.add(multimessage)

			return multimessage
		}
	}
}

