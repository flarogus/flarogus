package flarogus.command.impl

import dev.kord.common.Color
import dev.kord.rest.builder.message.create.*
import flarogus.Vars
import flarogus.command.*
import flarogus.command.builder.*
import flarogus.multiverse.entity.*
import flarogus.multiverse.entity.MultiversalUser.BankAccount.Transaction
import flarogus.multiverse.entity.MultiversalUser.BankAccount.Transaction.*
import flarogus.util.*
import kotlin.math.*
import kotlinx.datetime.*

fun TreeCommandBuilder.addBankSubtree() = subtree("bank") {
	description = "Manage your FlarCoin bank account."

	discordOnly()

	subtree("government", "Government bank commands.") {
		adminOnly()

		subcommand<Unit>("donate", "Donate money from the government account to a user account.") {
			addTransactionArguments()
			action {
				val account = args.user.flarcoinBank
				val transaction = Vars.multiverse.government.flarcoinBank.sendMoney(args.amount, account)

				reply { describeTransaction(transaction, Vars.multiverse.government, true) }
			}
		}
	}

	subaction<Unit>("balance", "Check your current balance") {
		val user = getMultiversalUser()
		val balance = user.flarcoinBank.balance

		reply(when {
			balance > 0 -> "Your current bank account balance is $balance FlarCoins."
			balance == 0 -> "You have no FlarCoins in your bank account."
			else -> "You are in debt for ${-balance} FlarCoins."
		})
	}

	subcommand<Unit>("transactions", "List all transactions performed with you bank account.") {
		val perPage = 15

		arguments {
			default<Int>("page", "Which page of the list of transactions to show. Each page shows $perPage transactions. Default is the last.") {
				-1
			}
		}

		action {
			val user = getMultiversalUser()
			val list = user.flarcoinBank.transactions.orEmpty()

			if (list.isEmpty()) {
				replyEmbed { description = "Nothing to show: you have not made any transactions yet." }
				return@action
			}
			
			val page = args.arg<Int>("page").let { if (it < 0) list.size + it else it - 1 }
			val pagesInList = ceil(list.size.toFloat() / perPage).toInt()
			require(page in 0 until pagesInList) { "'page' argument must be in range of [1, $pagesInList] or [-$pagesInList, -1]" }

			val slice = list.subList(perPage * page, min(perPage * (page + 1), list.size) - 1)

			replyEmbed {
				description = """
					Showing page ${page + 1} out of $pagesInList.
					Type `!flarogus bank show-transaction [[id]]` to see the detailed info of a transaction.
				""".trimIndent()

				slice.forEachIndexed { index, transaction ->
					val amount = transaction.amount
					// prefix and preposition
					val event = when {
						transaction is IncomingTransaction && amount >= 0 -> "Received" to "from"
						amount >= 0 -> "Sent" to "to"
						else -> "Lost" to "due to"
					}
					val user = Vars.multiverse.userOf(when(transaction) {
						is IncomingTransaction -> transaction.sender ?: Vars.multiverse.system.discordId
						is OutcomingTransaction -> transaction.receiver
					})?.name ?: "<invalid user>"

					field("id: $index. ${event.first} $amount FlarCoins") {
						val time = transaction.timestamp?.toJavaInstant()?.formatUTC() ?: "<?>"

						"${event.second} $user at $time UTC"
					}
				}
			}
		}
	}

	subcommand<Unit>("show-transaction", "View detailed info about a transaction.") {
		arguments { required<Int>("id", "The id of your transaction.") }

		action {
			val user = getMultiversalUser()
			val id = args.arg<Int>("id")
			val transaction = user.flarcoinBank.transactions?.getOrNull(id)

			require(transaction != null) { "Transaction #$id does not exist. Use `!flarogus bank transaction` to see all transactions." }

			reply { describeTransaction(transaction, null, false) }
		}
	}

	subcommand<Unit>("pay", "Pay someone with FlarCoins.") {
		addTransactionArguments()
		action {
			require(args.amount > 0) { "You can only pay a positive amount of money." }

			val user = getMultiversalUser()
			val target = args.user

			require(user.flarcoinBank.balance >= args.amount) { "You don't have enough FlarCoins to perform this transaction." }

			val transaction = user.flarcoinBank.sendMoney(args.amount, target.flarcoinBank)
			reply { describeTransaction(transaction, user, true) }
		}
	}

	@Suppress("UNCHECKED_CAST")
	subcommand<Unit>("notifications", "Toggle bank account notifications for your account.") {
		arguments { optional<String>("value", "'On' or 'off'. Leave empty to toggle the notifications.") }

		action {
			val user = getMultiversalUser()
			val newValue = when (val value = args.opt<String>("value")?.lowercase()) {
				"off", "false", "no" -> false
				"on", "true", "yes" -> true
				"toggle", null -> user.showNotifications.not()

				else -> fail("Incomprehensible value: $value. Must be 'on', 'off', or empty.")
			}
			(user as RealMultiversalUser).showNotifications = newValue
			reply("Notifications were turned " + when (newValue) {
				true -> "on"
				false -> "off"
			} + " for your account.")
		}
	}
}

suspend fun MessageCreateBuilder.describeTransaction(
	transaction: Transaction,
	owner: MultiversalUser?,
	showCurrentBalance: Boolean
) = embed {
	var user: MultiversalUser? = null
	val amount = transaction.amount

	timestamp = transaction.timestamp

	when (transaction) {
		is IncomingTransaction -> {
			user = owner ?: transaction.sender?.let { Vars.multiverse.userOf(it) }

			title = "Incoming FlarCoin transaction"
			description = when {
				amount == 0 -> "Nothing was transferred?.."
				amount > 0 -> "$amount FlarCoins were added to your bank account."
				else -> "$amount FlarCoins were deducted from your bank account."
			}
			color = Color(0x69e269)
		}
		is OutcomingTransaction -> {
			user = transaction.receiver?.let { Vars.multiverse.userOf(it) }

			title = "Outcoming FlarCoin transaction."
			description = when {
				amount >= 0 -> "$amount FlarCoins were transferred from your account to that of $user."
				else -> "$amount FlarCoins were deducted from the account of $user and added to yours."
			}
			color = Color(0xe2696b)
		}
	}

	thumbnail { url = user?.avatar.orEmpty() }

	author {
		name = user?.name ?: "<invalid user>"
		icon = user?.avatar
	}

	if (showCurrentBalance) {
		field("Your current FlarCoin balance is") { (owner?.flarcoinBank?.balance ?: -1).toString() }
	}
}

private val Callback<*>.ArgumentCallback.user get() = arg<MultiversalUser>("user")
private val Callback<*>.ArgumentCallback.amount get() = arg<Int>("amount")

private fun AbstractCommandBuilder<*, *>.addTransactionArguments() {
	arguments {
		required<MultiversalUser>("user", "The user that's going to receive the FlarCoins.")
		required<Int>("amount", "The amount of FlarCoins to transfer.")
	}
}

private suspend fun Callback<*>.getMultiversalUser() =
	Vars.multiverse.userOf(originalMessage().author!!.id)!!
