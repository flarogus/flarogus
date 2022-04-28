package flarogus.command.impl

import java.net.*
import java.awt.image.*
import javax.imageio.*
import kotlin.time.*
import kotlin.math.*
import kotlinx.coroutines.*
import dev.kord.common.entity.*
import dev.kord.rest.builder.message.create.*
import dev.kord.core.behavior.*
import dev.kord.core.behavior.channel.*
import flarogus.*
import flarogus.util.*
import flarogus.command.*
import flarogus.command.builder.*
import flarogus.multiverse.*

@OptIn(kotlin.time.ExperimentalTime::class)
fun createRootCommand() = createTree("flarogus") {
	subtree("fun") {
		description = "Commands made for fun."

		addMinesweeperSubcommand()

		subcommand<BufferedImage>("flaroficate") {
			val flarsusBase = ImageIO.read({}::class.java.getResource("/flarsus.png") ?: throw RuntimeException("aaaaa le flar has escaped"))

			description = "Turn a user into a flarogus. -i flag can be used to flaroficate an image."

			arguments {
				default<Snowflake>("user", "The user you want to flaroficate. Defaults to you if -i is not present.") {
					originalMessage?.asMessage()?.author?.id ?: Snowflake.NONE
				}
				flag("image").alias('i')
			}

			action {
				val url = if (args.flag("image")) {
					originalMessage?.asMessage()?.attachments?.find {
						it.isImage && it.width!! < 2000 && it.height!! < 2000
					}?.url ?: fail("no valid image attached")
				} else {
					Vars.supplier.getUserOrNull(args.arg<Snowflake>("user"))?.getAvatarUrl() ?: fail("cannot determine user id")
				}
	
				val origin = ImageIO.read(URL(url))
				val flaroficated = ImageUtil.multiply(origin, flarsusBase)
				result(flaroficated)
			}
		}

		subcommand<String>("impostor") {
			val vowels = listOf('a', 'A', 'e', 'E', 'i', 'I', 'o', 'O', 'u', 'U', 'y', 'Y')

			description = "Display the name of the user as if they were an impostor"

			arguments {
				default<String>("user", "The user you want to turn into an impostor. Can be a string or a mention. Defaults to you.") {
					originalMessage?.asMessage()?.data?.author?.username ?: "unknown user"
				}
			}

			action {
				var name = args.arg<String>("user")
				name.toSnowflakeOrNull()?.let { name = Vars.supplier.getUserOrNull(it)?.username ?: "invalid-user" }
				
				result(buildString {
					var usAdded = false
					for (i in name.length - 1 downTo 0) {
						val char = name[i]
						if (!usAdded && char.isLetter() && char !in vowels) {
							insert(0, "us")
							insert(0, char)
							usAdded = true
						} else if (usAdded || char !in vowels) {
							insert(0, char)
						}
					}
					if (length == 0) append("sus")
				})
			}
		}

		subcommand<BufferedImage>("merge") {
			description = "Merge pfps of two users"

			arguments {
				required<Snowflake>("first", "The first user")
				default<Snowflake>("second", "The second user. Defaults to you.") {
					originalMessage?.asMessage()?.author?.id ?: Snowflake.NONE
				}
			}

			action { 
				val image1 = Vars.supplier.getUserOrNull(args.arg<Snowflake>("first"))?.getAvatarUrl()?.let {
					ImageIO.read(URL(it))
				} ?: fail("invalid first user")

				val image2 = Vars.supplier.getUserOrNull(args.arg<Snowflake>("second"))?.getAvatarUrl()?.let {
					ImageIO.read(URL(it))
				} ?: fail("invalid second user")

				val result = ImageUtil.merge(image1, image2)
				result(result)
			}
		}

		subcommand<Unit>("daily") {
			description = "Claim your daily reward. Can only be used once an hour."
			discordOnly()

			val dailies = arrayOf(
				"you are brainless", "you have no friends", "you fell off", "1 social credit was deducted from your account",
				"you lost", "amogus was sent to your house", "you are an amogus", "go commit suicide",
				"what did you expect", "go seek for help", "this was a triumph...", "*amogus appears!*",
				"*a vent opens right below you*", "you were voted off.", "mistakes were made"
			)
			
			action {
				val user = originalMessage?.asMessage()?.author?.id?.let { Multiverse.userOf(it) }
				require(user != null) { "couldn't find nor acquire a user entry for your account!" }

				if (System.currentTimeMillis() > user.lastReward + 1000L * 60 * 60 * 24) {
					user.lastReward = System.currentTimeMillis()
					reply("daily reward: " + dailies.random())
				} else {
					val wait = ((user.lastReward + 1000L * 60 * 60 * 24) - System.currentTimeMillis())
					reply("you have already claimed your daily reward! wait ${formatTime(wait)}!")
				}
			}
		}
	}

	subtree("util") {
		description = "Utility commands."

		subcommand<String>("echo") {
			description = "replies with the providen argument."

			arguments { 
				default<String>("text") { "<no text>" }
			}
			action {
				result(args.arg<String>("text"))
			}
		}
	}

	subcommand<Long>("sus") {
		description = "Shows info of the current instance"

		action {
			val msg = reply("sussificating... <a:loading:967451900213624872>")?.await()
			msg?.edit {
				val ping = msg.id.timeMark.elapsedNow().toLong(DurationUnit.MILLISECONDS)

				this@action.result(ping, false)
				content = """
					${Vars.ubid} — running for ${formatTime(System.currentTimeMillis() - Vars.startedAt)}, sussification time: ${ping} ms.
					Time since flarogus epoch: ${formatTime(System.currentTimeMillis() - Vars.flarogusEpoch)}
				""".trimIndent()
			} ?: result(0L, false)
		}
	}

	adminSubcommand<Boolean>("shutdown") {
		description = "Shuts down an instance by it's ubid (acquired using 'flarogus sus') and optionally purge the multiverse."

		arguments {
			required<String>("ubid", "Instance id to shut down")
			optional<Int>("purgeCount", "Number of messages to delete (only from this instance). Used to clean up after double-instance periods.")
		}

		action {
			if (args.arg<String>("ubid") == Vars.ubid) {
				Multiverse.shutdown()

				args.ifPresent("purgeCount") { purgeCount: Int ->
					Multiverse.history.takeLast(min(20, purgeCount)).forEach {
						it.retranslated.forEach { it.delete() }
					}
				}

				result(true)
				
				Multiverse.brodcastSystem {
					content = "A multiverse instance is shutting down... (This is not neccesary a problem)"
				}

				Vars.client.shutdown()
				System.exit(0) // exit completely to stop the workflow
			} else {
				result(false, false)
			}
		}
	}

	subcommand<Boolean>("report") {
		val reportsChannel = Snowflake(944718226649124874UL)
		val linkRegex = """discord.com/channels/\d+/(\d+)/(\d+)""".toRegex()

		description = "Report an issue related to Flarogus or Multiverse. If you're reporting a violation, please, include a message link!"

		arguments {
			required<String>("message", "Content of the report. Supports message links!")
		}

		action {
			try {
				val msg = originalMessage?.asMessage()
				Vars.client.unsafe.messageChannel(reportsChannel).createMessage {
					content = """
						${msg?.author?.tag} (channel ${msg?.channelId}, guild ${msg?.data?.guildId?.value}) reports:
						```
						${args.arg<String>("message").stripCodeblocks().stripEveryone().take(1800)}
						```
					""".trimIndent()

					try {
						val result = linkRegex.findAll(args.arg<String>("message"))
						
						result.forEach {
							quoteMessage(
								Vars.supplier.getMessage(it.groupValues[1].toSnowflake(), it.groupValues[2].toSnowflake()),
								reportsChannel,
								"linked message by"
							)
							embeds.lastOrNull()?.url = "https://" + it.value
						}
					} catch (e: Exception) {
						embed { description = "failed to include a message reference: $e" }
					}
				}
				
				result(true)
			} catch (e: Exception) {
				throw CommandException("Could not send a report: $e")
			}
		}
	}

	subcommand<Unit>("server") {
		discordOnly()

		description = "DM you an invite to the official server"

		action {
			try {
				originalMessage!!.asMessage().author!!.getDmChannel().createMessage(
					"invite to the core guild: https://discord.gg/kgGaUPx2D2"
				)
			} catch (e: Exception) {
				reply("Could not create a DM channel. Ensure that you allow everyone to DM you. $e")
			}
		}
	}

	adminSubcommand<Any?>("run") {
		val codeblockRegex = "```([a-z]*)?((?s).*)```".toRegex()
		val defaultImports = arrayOf(
			"flarogus.*", "flarogus.util.*", "flarogus.multiverse.*", "ktsinterface.*", "dev.kord.core.entity.*", "dev.kord.core.entity.channel.*",
			"dev.kord.common.entity.*", "dev.kord.rest.builder.*", "dev.kord.rest.builder.message.*", "dev.kord.rest.builder.message.create.*",
			"dev.kord.core.behavior.*", "dev.kord.core.behavior.channel.*", "kotlinx.coroutines.*", "kotlinx.coroutines.flow.*", "kotlin.system.*",
			"kotlinx.serialization.*", "kotlinx.serialization.json.*", "flarogus.multiverse.state.*", "flarogus.multiverse.entity.*"
		).map { "import $it;" }.joinToString("")

		description = "execute an arbitrary kotlin script"

		arguments {
			required<String>("script", "A kotlin script. Can contain a code block.")

			flag("su", "Run as a superuser. Mandatory.")
			flag("imports", "Add default imports").alias('i')
			flag("trace", "Display stack trace upon an exception").alias('t')
		}

		action {
			require(args.flag("su")) { "This command requires the --su flag to be present." }

			var script = args.arg<String>("script").let {
				codeblockRegex.find(it)?.groupValues?.getOrNull(2) ?: it
			}
			if (args.flag("imports")) script = "$defaultImports\n$script"

			Vars.scriptEngine.put("message", message)
			val result = try {
				Vars.scriptEngine.eval(script, Vars.scriptContext).let {
					when (it) {
						is Deferred<*> -> it.await()
						is Job -> it.join()
						else -> it
					}
				}.also { result(it, false) }
			} catch (e: Throwable) {
				result(e, false)
				(e.cause ?: e).let {
					if (args.flag("trace")) it.stackTraceToString() else it.toString()
				}
			}
			reply("```\n$result\n```")
		}
	}
}
