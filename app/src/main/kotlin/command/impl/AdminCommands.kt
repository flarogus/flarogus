package flarogus.command.impl

import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Message
import kotlin.math.*
import flarogus.command.*
import flarogus.command.builder.*
import flarogus.multiverse.*
import flarogus.multiverse.entity.*
import flarogus.multiverse.npc.impl.AmogusNPC
import flarogus.util.*
import kotlinx.serialization.json.JsonNull.content

fun TreeCommandBuilder.addAdminSubtree() = subtree("admin") {
	modOnly()

	description = "Mod/admin-only commands that allow to manage the multiverse"

	presetSubtree("listguilds") {
		adminOnly()

		description = "List multiversal guilds."
		
		fun Callback<String>.list(predicate: (MultiversalGuild) -> Boolean) {
			result(Multiverse.guilds.filter(predicate).sortedByDescending { it.discordId.value }.map {
				"${it.discordId} — $it"
			}.joinToString("\n"))
		}

		subaction<String>("all") {
			list { true && it.isValid }
		}
		subaction<String>("unwhitelisted") { 
			list { !it.isWhitelisted && it.isValid }
		}
		subaction<String>("whitelisted") {
			list { it.isWhitelisted && it.isValid }
		}
		subaction<String>("blacklisted") {
			list { it.isForceBanned && it.isValid }
		}
		subaction<String>("invalid") {
			list { !it.isValid }
		}
	}

	presetSubtree("banlist", "Manage the ban state of a user") {
		presetArguments {
			required<MultiversalUser>("user", "Multiversal user you want to manage")
		}
		subaction<Unit>("add", "Ban a user") {
			args.arg<MultiversalUser>("user").let {
				it.isForceBanned = true
				reply("$it has been banned.")
			}
		}

		subaction<Unit>("remove", "Unban a user") {
			args.arg<MultiversalUser>("user").let {
				it.isForceBanned = false
				reply("$it has been unbanned.")
			}
		}

		subaction<Boolean>("check", "Check if a user is banned") {
			args.arg<MultiversalUser>("user").isForceBanned.let {
				result(it, false)
				reply("This user is " + if (it) "banned" else "not banned.")
			}
		}
	}

	presetSubtree("blacklist", "Manage the blacklist state of a guild") {
		adminOnly()

		presetArguments {
			required<MultiversalGuild>("guild", "Multiversal guild you want to manage")
		}

		subaction<Unit>("add", "Blacklist a guild") {
			args.arg<MultiversalGuild>("guild").let {
				it.isForceBanned = true
				reply("$it has been blacklisted.")
			}
		}

		subaction<Unit>("remove", "Unblacklist a guild") {
			args.arg<MultiversalGuild>("guild").let {
				it.isForceBanned = false
				reply("$it has been unblacklisted.")
			}
		}

		subaction<Boolean>("check", "Check if a guild is banned") {
			args.arg<MultiversalGuild>("guild").isForceBanned.let {
				result(it, false)
				reply("This guild is " + if (it) "banned" else "not banned")
			}
		}
	}

	presetSubtree("whitelist") {
		description = "Manage the whitelist state of a guild"

		presetArguments {
			required<MultiversalGuild>("guild", "Multiversal guild you want to manage")
		}

		subaction<Unit>("add", "Whitelist a guild.") {
			args.arg<MultiversalGuild>("guild").let {
				val wasWhitelisted = it.isWhitelisted
				it.isWhitelisted = true
				// forcibly update it
				it.lastUpdate = 0L
				it.update()

				if (!wasWhitelisted) {
					reply("$it has been whitelisted.")
					Multiverse.broadcastSystemAsync { _ ->
						content = "A new guild has connected: $it."
					}
				}
			}
		}

		subaction<Unit>("remove", "Unwhitelist a guild.") {
			args.arg<MultiversalGuild>("guild").let {
				it.isWhitelisted = false
				reply("$it has been unwhitelisted.")
			}
		}

		subaction<Boolean>("check", "Check if a guild is whitelisted.") {
			args.arg<MultiversalGuild>("guild").isForceBanned.let {
				result(it, false)
				reply("This guild is " + if (it) "whitelisted" else "not whitelisted")
			}
		}
	}

	presetSubtree("tag") {
		description = "Manage the tag of a user."

		presetArguments {
			required<MultiversalUser>("user", "The user whose usertag you want to manage.")
		}

		subcommand<Unit>("set") {
			description = "set the usertag of the user"

			arguments {
				required<String>("tag")
			}

			action {
				args.arg<MultiversalUser>("user").let {
					it.usertag = args.arg<String>("tag")
					reply("Their name is now \"$it\".")
				}
			}
		}

		subaction<Unit>("clear", "Clear the usertag of the user.") {
			args.arg<MultiversalUser>("user").let {
				it.usertag = null
				reply("$it's usertag has been cleared.")
			}
		}

		subaction<String>("check", "Fetch the usertag of the user.") {
			result(args.arg<MultiversalUser>("user").usertag)
		}
	}

	presetSubtree("warn") {
		description = "Manage warnings of users."

		presetArguments {
			required<MultiversalUser>("user", "The user whose warnings you want to manage.")
		}

		subcommand<Unit>("add", "Warn a user.") {
			arguments {
				required<Int>("number", "The number of the rule you want to warn the user for")
				default<Int>("category", "Rule category to which the rule belongs. 1 (general) by default.") {
					1
				}
			}

			action {
				val rule = RuleCategory.of(args.arg("category"), args.arg<Int>("number") - 1)
				require(rule != null) { "Rule ${args.arg<Int>("category")}.${args.arg<Int>("number")} does not exist." }

				args.arg<MultiversalUser>("user").let {
					it.warnFor(rule, true)
					reply("$it has successffuly been warned for $rule")
				}
			}
		}

		val checkCommand by lazy { FlarogusCommand.find("!flarogus multiverse warnings") as FlarogusCommand<Int> }

		subaction<Int>("check", "Check warnings of the user. This command delegates to the 'multiverse warnings' command.") {
			val arg = args.arg<MultiversalUser>("user").discordId.toString()

			result(if (originalMessage != null) {
				checkCommand(originalMessage.asMessage(), arg).result
			} else {
				checkCommand(arg)
			})
		}

		subaction<Unit>("clear", "Clear warnings of the user") {
			val user = args.arg<MultiversalUser>("user")
			user.warns.clear()

			Multiverse.broadcastSystem { content = "User $user has just had their warnings cleared." }
		}
	}

	subcommand<Unit>("echo", "Send a system message in the multiverse") {
		arguments {
			required<String>("message", "Content of the system message. Trimmed.")
		}

		action {
			Multiverse.broadcastSystem {
				content = args.arg<String>("message").trim()
			}
		}
	}

	subcommand<Unit>("echo-as", "Send a message in the multiverse as one of the special users.") {
		val users = mapOf<String, suspend (String, Message?) -> Unit>(
			"local-amogus" to { msg, ref -> Multiverse.npcs.find { it is AmogusNPC }?.sendMessage(msg, ref) },
			"femboy-crawler" to { msg, ref ->
				Multiverse.broadcast("femboy crawler#6255 — oblivion settlement", "https://cdn.discordapp.com/attachments/732665247302942730/1011998607047663636/Miserable_situation_20220813_182228.png") {
					content = msg
					if (ref != null) quoteMessage(ref, it)
				}
			}
		)

		arguments {
			required<String>("alias", "Name alias of the special user.")
			required<String>("message", "Message to send.")
			optional<Snowflake>("reply", "Message to reference as if it was being replied to.")
		}

		action {
			val func = users.getOrDefault(args.arg<String>("alias").lowercase(), null)
			expect(func != null) { "Invalid alias. Available: ${users.keys.joinToString { "`$it`" }}" }

			val refId = args.opt<Snowflake>("reply")
			val ref = refId?.let { id -> Multiverse.history.find { id in it } ?: throw RuntimeException("message with id $refId could not be found in the history.") }

			func(args.arg("message"), (ref?.origin ?: ref?.retranslated?.firstOrNull())?.asMessage())
		}
	}

	subcommand<Boolean>("setloglevel", "Set the level of logging.") {
		arguments {
			required<String>("level", "New log level")
		}

		action {
			Log.level = Log.LogLevel.valueOf(args.arg<String>("level").uppercase())
			Log.force { "log level was set to ${Log.level}!" }
			result(true)
		}
	}

	subcommand<Int>("purge", "Deletes messages from the multiverse. Use with caution!") {
		arguments {
			required<Int>("count", "Number of messages to delete")
			flag("delete-origin").alias('o')
		}

		action {
			val deleteOrigin = args.flag("delete-origin")

			var errors = 0
			var deleted = 0
			Multiverse.history.takeLast(min(args.arg<Int>("count"), Multiverse.history.size)).forEach {
				it.retranslated.forEach { 
					try {
						it.delete()
						deleted++
					} catch (e: Exception) {
						errors++
					}
				}
				
				if (deleteOrigin) {
					try {
						it.origin?.delete()?.also { deleted++ }
					} catch (e: Exception) {
						errors++
					}
				}
			}
			
			result(deleted, false)
			reply("$deleted messages were deleted successfully, $errors messages could not be deleted.")
		}
	}
	
	subcommand<Unit>("update", "Invalidate all guilds and forcibly update them.") {
		action {
			Multiverse.guilds.forEach {
				it.lastUpdate = 0L
				it.update()
			}
		}
	}

	subcommand<String>("sanity-check", "Print the state of the multiverse to ensure everything is ok.") {
		action {
			var validGuilds = 0
			var connectedGuilds = 0
			var channels = 0
			var webhooks = 0
			Multiverse.guilds.forEach {
				if (it.isValid) validGuilds++
				if (it.webhooks.isNotEmpty()) connectedGuilds++
				channels += it.channels.size
				webhooks += it.webhooks.size
			}

			result("""
				```
				Is running:   ${Multiverse.isRunning}
				History size: ${Multiverse.guilds.size}
				-----
				Guilds:           ${Multiverse.guilds.size}
				Valid guilds:     $validGuilds
				Invalid guilds:   ${Multiverse.guilds.size - validGuilds}
				Connected guilds: $connectedGuilds
				-----
				Webhooks: $webhooks
				Channels: $channels
				-----
				Users:       ${Multiverse.users.size}
				Valid users: ${Multiverse.users.filter { it.isValid }.size}
				```
			""".trimIndent())
		}
	}
}
