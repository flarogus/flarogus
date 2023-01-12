package flarogus.multiverse

import com.github.mnemotechnician.markov.MarkovChain
import dev.kord.common.entity.*
import dev.kord.common.entity.optional.Optional
import dev.kord.core.behavior.*
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.*
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import dev.kord.rest.builder.message.create.embed
import flarogus.Vars
import flarogus.multiverse.entity.MultiversalGuild
import flarogus.multiverse.entity.MultiversalUser
import flarogus.multiverse.npc.NPC
import flarogus.multiverse.npc.impl.AmogusNPC
import flarogus.multiverse.state.*
import flarogus.util.replyWith
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random

/**
 * Manages the retraslantion of actions performed in one channel of the multiverse, aka a guild network,
 * into other channels.
 */
object MultiverseOld {

	val pendingActions = ArrayList<PendingAction<*>>(50)
	
	/** Sets up the multiverse */
	suspend fun start() {
		setupEvents()
		findChannels()
		
	}
	
	suspend fun messageReceived(event: MessageCreateEvent) {
		if (!isRunning || isOwnMessage(event.message)) return
		if (!guilds.any { it.channels.any { it.id == event.message.channel.id } }) return
		if (event.message.type.let {
			it !is MessageType.Unknown 
			&& it !is MessageType.Default 
			&& it !is MessageType.Reply
		}) {
			return
		}
		
		val user = userOf(event.message.data.author.id)
		val success = user?.onMultiversalMessage(event) ?: run {
			event.message.replyWith("No user associated with your user id was found!")
			return
		}

		if (success) {
			npcs.forEach { it.multiversalMessageReceived(event.message) }

			//if (event.message.content.trim().count { it == ' ' } > 3) {
			markov.train(event.message.content)
			//}
		}
	}

	private fun setupEvents() {
		Vars.client.events
			.filterIsInstance<MessageDeleteEvent>()
			.filter { isRunning }
			.filter { event -> !isRetranslatedMessage(event.messageId) }
			.filter { event -> isMultiversalChannel(event.channelId) }
			.filter { event -> event.guildId != null && guildOf(event.guildId!!).let { it != null && !it.isForceBanned } }
			.onEach { event ->
				addAction(DeleteMultimessageAction(event.messageId))
			}
			.launchIn(Vars.client)
		
		//can't check the guild here
		Vars.client.events
			.filterIsInstance<MessageUpdateEvent>()
			.filter { isRunning }
			.filter { event -> !isRetranslatedMessage(event.messageId) }
			.filter { event -> isMultiversalChannel(event.message.channel.id) }
			.filter { event -> event.old?.content != null || event.new.content !is Optional.Missing } // discord sends fake update events sometimes
			.onEach { event ->
				addAction(ModifyMultimessageAction(event.messageId, event.new))
			}
			.launchIn(Vars.client)
	}

	abstract class PendingAction<T>(timeLimitSeconds: Int) {
		protected val job = Job(rootJob)
		val timeLimit = timeLimitSeconds * 1000L
		var cachedResult: Deferred<T?>? = null
			protected set
		protected var lastException: Throwable? = null

		/** Immediately begins executing this action in a separate coroutine. Caches the result. */
		operator fun invoke(): Deferred<T?>  {
			if (cachedResult != null) return cachedResult!!
			return scope.async {
				try {
					return@async withTimeout(timeLimit) {
						execute()
					}
				} catch (e: TimeoutCancellationException) {
					lastException = e
					Log.error { "Timed out while executing $this: $e" }
				} catch (e: Exception) {
					lastException = e
					Log.error { "Uncaught exception while executing $this: $e" }
				} finally {
					job.complete()
				}
				null
			}.also { cachedResult = it }
		}

		/** Await the result of executing this action and returns the result or null if an exception has occurred. */
		@OptIn(ExperimentalCoroutinesApi::class)
		suspend fun await(): T? {
			job.join()
			return cachedResult?.getCompleted()
		}

		/** Awaits the result of executing this action and returns it, throws an exception if there's no result or the result is null. */
		suspend fun awaitOrThrow(): T
			= await() ?: throw (lastException ?: RuntimeException("The action had result and no exception."))

		protected abstract suspend fun execute(): T

		override fun toString() = this::class.java.name.substringAfterLast(".")
	}

	open class SendMessageAction(
		val user: String? = null,
		val avatar: String? = null,
		val filter: (TextChannel) -> Boolean = { true },
		val messageBuilder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
	) : PendingAction<Multimessage>(100) {
		override suspend fun execute(): Multimessage {

			coroutineScope {
				for (guild in guilds) launch {
					try {
						guild.send(
							username = user,
							avatar = avatar,
							filter = filter,
							handler = { m, w -> messages.add(WebhookMessageBehavior(w, m)) },
							builder = messageBuilder
						)
					} catch (e: Exception) {
						Log.error { "Exception thrown while retranslating a message to ${guild.name}: `$e`" }
					}
				}
			}

			val multimessage = Multimessage(null, messages)
			synchronized(history) {
				history.add(multimessage)
			}

			return multimessage
		}
	}

	open class DeleteMultimessageAction(
		val messageId: Snowflake,
		val deleteOrigin: Boolean = false
	) : PendingAction<Unit>(45) {
		override suspend fun execute() {
			val multimessage = history.find { it.origin?.id == messageId } ?: return

			coroutineScope {
				for (message in multimessage.retranslated) launch {
					message.delete()
				}
				if (deleteOrigin) multimessage.origin?.delete()
			}
			Log.info { "Message ${messageId} was deleted by deleting the original message" }

			synchronized(history) { history.remove(multimessage) }
		}
	}

	open class ModifyMultimessageAction(
		val messageId: Snowflake,
		val newMessage: DiscordPartialMessage,
		val editOrigin: Boolean = false
	) : PendingAction<Unit>(45) {
		override suspend fun execute() {
			val multimessage = history.find { it.origin?.id == messageId } ?: return

			val origin = multimessage.origin?.asMessage()
			// only the content can be modified. if it's intact, this is a false modification.
			if (newMessage.content.value == (origin ?: multimessage.retranslated.firstOrNull()?.asMessage())?.content) {
				return
			}

			val newContent = buildString {
				appendLine(newMessage.content.value ?: origin?.content.orEmpty())
				origin?.attachments?.forEach { attachment ->
					if (attachment.size >= maxFileSize) {
						appendLine(attachment.url)
					}
				}
			}
			
			coroutineScope {
				for (message in multimessage.retranslated) launch {
					message.edit { content = newContent }
				}
				if (editOrigin) multimessage.origin?.edit { content = newContent }
			}

			Log.info { "Message ${multimessage.origin?.id} was edited by it's author" }
		}
	}
}

