package flarogus.multiverse.entity

import dev.kord.common.entity.*
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.entity.User
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.Image
import dev.kord.rest.builder.message.create.*
import flarogus.Vars
import flarogus.command.impl.describeTransaction
import flarogus.multiverse.*
import flarogus.multiverse.ScamDetector
import flarogus.multiverse.state.Multimessage
import flarogus.multiverse.state.TransactionSerializer
import flarogus.util.*
import kotlinx.serialization.*
import java.net.URL
import kotlin.time.ExperimentalTime
import kotlinx.datetime.*

/** Represents a user within the multiverse. */
@Serializable
sealed class MultiversalUser : MultiversalEntity() {
	abstract val discordId: Snowflake
	abstract val user: User?
	abstract val isSuperuser: Boolean
	abstract val isModerator: Boolean
	/** The name of the user */
	abstract val name: String
	/** The avatar of the user */
	abstract val avatar: String?

	abstract val warns: MutableList<WarnEntry>
	val warningPoints get() = warns.fold(0) { total: Int, warn -> total + warn.rule.points }

	@SerialName("ls")
	var lastSent = 0L
	@SerialName("ts")
	var totalSent = 0

	/** The FlarCoin bank account of this user. By default contains 10 FlarCoins. */
	abstract val flarcoinBank: BankAccount
	/** If true, the user wants to receive DM notifications about their FlarCoin account. */
	abstract val showNotifications: Boolean
	
	/** Alias for [discordId]. */
	val id get() = discordId

	/**
	 * Should be called when this user sends a multiversal message.
	 * Automatically saves the message to history.
	 *
	 * @return whether the message was retranslated.
	 */
	suspend open fun onMultiversalMessage(event: MessageCreateEvent): Boolean {
		update()
		if (!canSend()) {
			event.message.replyWith(when {
				isForceBanned -> "You are banned from the Multiverse. Contact one of the admins for more info."
				warningPoints >= criticalWarns -> "You have too many warning points. You cannot send messages in the Multiverse until some of your warns expire."
				!isValid -> "Your user entry is invalid. This should be fixed automatically after some time."
				else -> "For an unknown reason, you're not allowed to send mssages in the Multiverse. Contact the admins for more info."
			})
			return false
		} else {
			// rate limiting
			val sentAt = event.message.id.timestamp.toEpochMilliseconds()
			val delay = lastSent + messageRateLimit - sentAt
			lastSent = sentAt

			if (delay > 0) {
				val reply = event.message.replyWith("This message was not retranslated because you were rate limited. Please, wait $messageRateLimit ms.")
				scheduleMessageRemoval(10.second, reply, event.message)
				return false
			} else if (ScamDetector.hasScam(event.message.content)) {
				event.message.replyWith("[!] your message contains a potential scam. if you're not a bot, remove any links and try again")
				Log.info { "a potential scam message sent by $name was blocked: ```${event.message.content.take(200)}```" }
				return false
			}

			val guild = event.guildId?.let { Vars.multiverse.guildOf(it) }

			if (guild == null) {
				Log.info { "A user with a non-existent guild has attempted to send a message in the multiverse: `${event.message.content}`" }
			} else if (!guild.isWhitelisted) {
				event.message.replyWith("""
					This guild is not whitelisted.
					Contact the admins (e.g. by executing `!flarogus report 'pls whitelist my server thx'`) to get whitelisted.
				""".trimIndent())
			} else {
				// notify the global message filters
				Vars.multiverse.messageFilters.find { !it.filter(this, event.message) }?.let {
					val suffix = it.reason?.let { reason -> "The reason was: $reason" }.orEmpty()
					event.message.replyWith("This message was not retranslated. $suffix")

					if (it.log) {
						Log.info { "Message sent by $name was filtered out (${it.reason}): ```${event.message.content}```" }
					}
					return false
				}

				// retranslating the message
				val message = send(
					guild = guild,
					filter = { it.id != event.message.channelId }
				) { channelId ->
					content = event.message.toRetranslatableContent()
					quoteMessage(event.message.referencedMessage, channelId)
					
					event.message.attachments.forEach { attachment ->
						if (attachment.isImage) {
							embed { image = attachment.url }
						} else if (attachment.size < maxFileSize) {
							addFile(attachment.filename, URL(attachment.url).openStream())
						}
					}

					if (content!!.isEmpty() && event.message.data.attachments.isEmpty()) content = "<no content>"
				}

				message.origin = event.message

				return true
			}
			return false
		}
	}
	
	/**
	 * Sends a message as if it was sent from the specified guild by this user
	 * @param guild Guild to send from
	 * @param filter Should return true if the message is to be retranslated into the providen channel
	 */
	suspend inline fun send(
		guild: MultiversalGuild,
		noinline filter: (TextChannel) -> Boolean = { true },
		crossinline builder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
	): Multimessage {
		update()
		return guild.retranslateUserMessage(this, filter) {
			builder(it)
			content = content?.revealHypertext()?.explicitMentions()
		}.also { totalSent++ }
	}

	/** Broadcast into the multiverse using this user's username and avatar. */
	suspend inline fun broadcastAsync(
		noinline filter: (TextChannel) -> Boolean = { true },
		crossinline builder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
	): Multiverse.MultimessageDefinition {
		update()
		return Vars.multiverse.broadcastAsync(name, avatar, filter) {
			builder(it)
			content = content?.explicitMentions()
		}.also { totalSent++ }
	}
	
	/** Same as [broadcastAsync] but awaits for the result. */
	suspend inline fun broadcast(
		noinline filter: (TextChannel) -> Boolean = { true },
		crossinline builder: suspend MessageCreateBuilder.(id: Snowflake) -> Unit
	) = broadcastAsync(filter, builder).await()

	/** Warns this user for a rule. */
	open suspend fun warnFor(rule: Rule, informMultiverse: Boolean) {
		if (rule.points <= 0) return;
		warns.add(WarnEntry(rule, System.currentTimeMillis()))

		if (informMultiverse) {
			Vars.multiverse.system.broadcastAsync {
				content = "User $name was warned for rule ${rule.category}.${rule.index + 1}: «$rule»"
			}
		}
	}
	
	/** Whether this user can send multiversal messages */
	open fun canSend(): Boolean {
		return !isForceBanned && warningPoints < criticalWarns && isValid
	}

	/** Try to open a DM channel with this user. Return null on failure. */
	suspend open fun getDmChannel() = runCatching {
		user?.getDmChannel()
	}.getOrNull()

	override fun toString() = name

	companion object {
		val criticalWarns = 5
		val updateInterval = 30.minute
		val messageRateLimit = 3500L

		val linkRegex = """https?://([a-zA-Z0-9_\-]+?\.?)+/[a-zA-Z0-9_\-%\./]+""".toRegex()
		/**
		 * Files with size exceeding this limit will be sent in the form of links.
		 * Since i'm now hosting it on a device with horrible connection speed, it's set to 0. 
		 */
		const val maxFileSize = 1024 * 1024 * 0
	}

	/** Represents the fact that a user has violated a rule */
	@Serializable
	@OptIn(ExperimentalTime::class)
	data class WarnEntry(val rule: Rule, val received: Long = System.currentTimeMillis()) {
		val instant: Instant get() = Instant.fromEpochMilliseconds(received)

		val expires: Instant get() = Instant.fromEpochMilliseconds(received + expiration)
		
		fun isValid() = received + expiration > System.currentTimeMillis()

		companion object {
			/** Time in ms required for a warn to expire. 60 days. */
			val expiration = 60.day
		}
	}

	/** Represents a FlarCoin bank account of a user. */
	@Serializable
	data class BankAccount(
		@SerialName("id") val ownerId: Snowflake, 
		@SerialName("bal") var balance: Int = 0
	) {
		/** All transactions performed by this user. */
		@SerialName("ts")
		var transactions: MutableList<Transaction>? = null
			private set

		/** Returns the list of transactions, creating it if neccesary. */
		fun getTransactionsList() = transactions ?: ArrayList<Transaction>().also { transactions = it }

		/** Add/substract [amount] FlarCoins to/from this account and record the transaction. */
		fun addMoney(amount: Int, sender: Snowflake? = null, timestamp: Instant = Clock.System.now()) = let {
			balance += amount
			Transaction.IncomingTransaction(amount, sender, timestamp)
				.also(getTransactionsList()::add)
		}

		/** 
		 * Send FlarCoins from this bank account to another bank account and record the transaction.
		 * Unless [forcibly] is true, throws an [IllegalArgumentException] if [balance] is lower than [amount].
		 *
		 * @param sendNotification if true, an attempt to find the target user and send a notification will be made.
		 */
		suspend fun sendMoney(
			amount: Int, 
			receiver: BankAccount, 
			forcibly: Boolean = false,
			sendNotification: Boolean = true
		): Transaction.OutgoingTransaction {		
			if (!forcibly && amount > balance) {
				error("The amount of money on the $ownerId bank account is insufficient to perform the transaction.")
			}
			balance -= amount

			Transaction.OutgoingTransaction(
				amount = amount,
				receiver = receiver.ownerId,
				timestamp = Clock.System.now()
			).also { transaction ->
				getTransactionsList().add(transaction)
				val incomingTransaction = receiver.addMoney(amount, ownerId, transaction.timestamp!!)

				if (sendNotification) {
					val target = Vars.multiverse.userOf(receiver.ownerId)

					if (target != null && target.showNotifications) runCatching {
						target.getDmChannel()?.createMessage {
							describeTransaction(incomingTransaction, target, true)
							// add a "you can stop seeing such messages..." footer
							embeds.last().footer {
								text = "You can stop getting such DM messages by typing `!flarogus bank notifications off`."
							}
						}
					}.onFailure {
						Log.error(it) { "Failed to notify $target about an incoming transaction" }
					}
				}

				return transaction
			}
		}

		@Serializable(with = TransactionSerializer::class)
		sealed class Transaction(val amount: Int, val timestamp: Instant?) {
			/** A transaction from another bank account to this bank account. */
			class IncomingTransaction(amount: Int, val sender: Snowflake?, timestamp: Instant?) 
				: Transaction(amount, timestamp)

			/** A transaction from this bank account to another bank account. */
			class OutgoingTransaction(amount: Int, val receiver: Snowflake, timestamp: Instant?)
				: Transaction(amount, timestamp)
		}
	}

	/** Represents a dynamic message filter. */
	data class MessageFilter(
		/** When the message is declined, this (if not null) is sent as a reason for it was declined. */
		val reason: String? = null,
		/** Whether to send a log message when a message gets declined by this filter. */
		val log: Boolean = false,
		/** When this function returns false, the message is declined. */
		val filter: MultiversalUser.(Message) -> Boolean
	)
}

/** Merges the content of this message with links to its attachments. */
fun Message.toRetranslatableContent() = buildString {
	append(content.stripEveryone())
	attachments.forEach { attachment ->
		if (!attachment.isImage && attachment.size >= MultiversalUser.maxFileSize) {
			append('\n')
			append("[file: ${attachment.filename}](${attachment.url})")
			append(" (%.3f MiB)".format(attachment.size / 1024 / 1024f))
		}
	}
}
/** Merges the content of this message with links to its attachments. */
fun DiscordPartialMessage.toRetranslatableContent() = buildString {
	append(content.value?.stripEveryone().orEmpty())
	attachments.value?.forEach { attachment ->
		if (!Image.Format.isSupported(attachment.filename) && attachment.size >= MultiversalUser.maxFileSize) {
			append('\n')
			append("[file: ${attachment.filename}](${attachment.url})")
			append(" (%.3f MiB)".format(attachment.size / 1024 / 1024f))
		}
	}
}

