package flarogus.command.impl

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.User
import dev.kord.rest.builder.message.create.embed
import flarogus.Vars
import flarogus.command.TreeCommand
import flarogus.command.builder.createTree
import flarogus.multiverse.*
import flarogus.multiverse.entity.*
import flarogus.util.*
import kotlinx.coroutines.*
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO
import javax.script.ScriptContext
import kotlin.math.min
import kotlin.time.DurationUnit

@OptIn(kotlin.time.ExperimentalTime::class)
fun createRootCommand(): TreeCommand = createTree("!flarogus") {
	subtree("multiverse") {
		description = "Commands related to the multiverse."

		addAdminSubtree()

		addManagementSubtree()

		subcommand<Int>("warnings") {
			description = "Show the warnings of a user"

			arguments {
				default<MultiversalUser>("user", "The user whose warnings you want to see. Defaults to you.") {
					originalMessage?.asMessage()?.author?.id?.let {
						Multiverse.userOf(it)
					} ?: error("anonymous caller must specify the target user")
				}

				action {
					val user = args.arg<MultiversalUser>("user")
					val points = user.warningPoints
					result(points, false)

					reply { embed {
						title = "User $user has $points warning points"
						
						user.warns.forEachIndexed { index, warn ->
							val expires = warn.expires.formatUTC()

							field {
								value = "$index — expires at $expires. «${warn.rule}»"
							}
						}
					} }
				}
			}
		}

		subcommand<List<MultiversalUser>>("lastusers") {
			description = "List last users who have recently sent a message in the multiverse"
			
			arguments {
				default<Int>("max", "Maximum user count. Defaults to 20.") { 20 }
			}

			action {
				val users = Multiverse.users.sortedBy { -it.lastSent }.take(args.arg<Int>("max"))
				result(users, false)
				reply(users.map { "${it.name} — ${it.discordId}" }.joinToString("\n"))
			}
		}

		subcommand<Unit>("rules") {
			discordOnly()
			
			action {
				reply {
					RuleCategory.values().forEach {
						embed { description = it.toString() }
					}
				}
			}
		}

		subcommand<Int>("deletereply") {
			discordOnly()

			description = "Delete a message sent in the multiverse by replying to it."

			arguments {
				flag("origin", "Delete original message").alias('o')
				flag("silent", "Do not reply, delete the message that invoked this command").alias('s')
			}

			action {
				val msg = originalMessage!!.asMessage()
				val reply = msg.referencedMessage ?: fail("you must reply to a multiversal message")
				
				val multimessage = Multiverse.history.find { reply.id in it } ?: fail("This message wasn't found in the history. Perhaps, it was sent too long time ago or is not a multiversal message?")
				
				if (!msg.author.isModerator() && multimessage.origin?.asMessage()?.data?.author?.id != msg.data.author.id) {
					throw IllegalAccessException("you are not allowed to delete others' messages")
				}

				var deleted = 0
				val silent = args.flag("silent")
				
				multimessage.retranslated.forEach { 
					try { 
						it.delete();
						deleted++
					} catch (ignored: Exception) {}
				}
				
				Log.info { "${msg.author?.tag} deleted a multiversal message with id ${multimessage.origin?.id}" }
				Multiverse.history.remove(multimessage)

				if (args.flag("origin")) {
					try {
						multimessage.origin?.delete()?.also { deleted++ }
					} catch (e: Exception) {
						if (!silent) multimessage.origin?.replyWith("""
							This message was deleted from other multiversal channels but this (original) message could not be deleted.
							Check whether the bot has the necessary permissions.
						""".trimIndent())
					}
				}
				
				result(deleted, false)
				if (!silent) {
					reply("Deleted a total of $deleted messages")
				} else if (originalMessage != null) {
					try {
						originalMessage.asMessage().delete()
					} catch (_: Exception) {}
				}
			}
		}

		subcommand<String>("replyinfo") {
			discordOnly()

			description = "View info about a message sent in the multiverse by replying to it"

			action {
				val reply = originalMessage!!.asMessage().referencedMessage
					?: fail("You must reply to a multiversal message")
				val msg = Multiverse.history.find { reply in it }
					?: fail("this message wasn't found in the history. perhaps, it was sent too long time ago?")

				val originMsg = msg.origin?.asMessage() ?: fail("This message doesn't have an origin.")
				val author = User(originMsg.data.author, Vars.client)

				result("""
					Multiversal message #${originMsg.id}
					Author: ${author.tag}, uid: ${author.id}
					Channel id: ${originMsg.channelId}
					Guild id: ${originMsg.getChannel().data.guildId.value}
				""".trimIndent())
			}
		}
	}

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
	
				val origin = withContext(Dispatchers.IO) {
					ImageIO.read(URL(url))
				}
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
					if (isEmpty()) append("sus")
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
			description = "Claim your daily reward. Can only be used once a day."
			discordOnly()

			val dailies = arrayOf(
				"you have got no mental power", "you have no friends", "you fell off", "1 social credit was deducted from your account",
				"*a vent opens right below you*", "you were voted out.", "mistakes were made",
				"you lost", "amogus was sent to your house", "you are an amogus", "go do something more useful",
				"what did you expect", "sorry, your daily reward has been taken by someone else.", "this was a triumph...", "you were killed by the impostor",
				"you get nothing", "you are sus", "nothing, try again tomorrow","you have mere seconds.", "you ruined the task!",
				"your parents don't love you", "you are so fat, if you were an impostor, you would get stuck in the vent",
				"you will never finish your tasks", "two tasks have been added to your list, crewmate!"
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

		addUserinfoSubcommand()

		subcommand<String>("echo") {
			description = """
				Replies with the providen argument, allowing substitutions (see help).

				Anything inside a $( ... ) structure is treated as a command substitution.
				The command located inside such a structure is immediately executed and it's result takes the place of that structure.
				Technicially, any command allows this, but this command is the most useful.
			""".trimIndent()

			arguments {
				default<String>("string", "An arbitrary string.") { "" }
			}
			action {
				result(args.arg<String>("string").trim())
			}
		}

		subcommand<String>("username", "fetch the name of the user") {
			arguments {
				required<MultiversalUser>("user")
			}
			action {
				args.arg<MultiversalUser>("user").let {
					it.update()
					result(it.name)
				}
			}
		}

		subcommand<String>("guildname", "fetch the name of the guild") {
			arguments {
				required<MultiversalGuild>("guild")
			}
			action {
				args.arg<MultiversalGuild>("guild").let {
					it.update()
					result(it.name)
				}
			}
		}

		presetSubtree("var", "Manage environment variables") {
			val environment = HashMap<String, String>()

			presetArguments {
				required<String>("name", "Name of the environment variable")
			}

			subcommand<Unit>("set", "Set the value of a variable") {
				arguments { required<String>("value") }
				action {
					val name = args.arg<String>("name")
					environment[name] = args.arg<String>("value")
				}
			}

			subaction<String?>("get", "Get the value of a variable") {
				result(environment.getOrDefault(args.arg<String>("name"), null))
			}
		}

		presetSubtree("reply", "Fetch info of a message you reply to.") {
			discordOnly()
			// TODO: help can not be called without a reply
			check { m, _ -> if (m?.referencedMessage != null) null else "you must reply to a message." }

			subaction<String>("username", "Fetch the username of the author of the message.") {
				result(originalMessage().referencedMessage!!.data.author.let { it.username + "#" + it.discriminator })
			}

			subaction<String>("pfp", "Fetch the pfp url of the author of the message.") {
				result(originalMessage().referencedMessage!!.let {
					it.author?.getAvatarUrl() ?: it.data.author.avatar ?: "null"
				})
			}

			subaction<Snowflake>("userid", "Fetch the id of the user that has sent this message.") {
				result(originalMessage().referencedMessage!!.data.author.id)
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
					${Vars.ubid} — running for ${formatTime(System.currentTimeMillis() - Vars.startedAt)}, sussification time: $ping ms.
					Time since flarogus epoch: ${formatTime(System.currentTimeMillis() - Vars.flarogusEpoch)}
				""".trimIndent()
			} ?: result(0L, false)
		}
	}

	subcommand<Boolean>("shutdown") {
		adminOnly()

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
					content = "A multiverse instance is shutting down... (This is not neccesarily a problem)"
				}

				Vars.client.shutdown()
				System.exit(0) // stop the workflow
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

	subcommand<Any?>("run") {
		adminOnly()

		description = "execute an arbitrary kotlin script"

		arguments {
			required<String>("script", "A kotlin script. Can contain a code block.")

			flag("su", "Run as a superuser. Mandatory.").alias('s')
			flag("imports", "Add default imports").alias('i')
			flag("trace", "Display stack trace upon an exception").alias('t')
		}

		action {
			require(args.flag("su")) { "This command requires the --su flag to be present." }

			var script = args.arg<String>("script").let {
				Vars.codeblockRegex.find(it)?.groupValues?.getOrNull(2) ?: it
			}
			if (args.flag("imports")) script = "${Vars.defaultImports}\n$script"

			val msg = originalMessage?.asMessage()
			Vars.scriptEngine.put("message", msg)
			Vars.scriptContext.setAttribute("message", msg, ScriptContext.ENGINE_SCOPE)

			val result = try {
				Vars.scriptEngine.eval(script, Vars.scriptContext).let {
					when (it) {
						is Deferred<*> -> it.await()
						is Job -> it.join()
						else -> it
					}
				}.also { 
					result(it, false)
					Log.info { "${msg?.author?.tag} has successfully executed a kotlin script (${msg?.id} in ${msg?.channelId})" }
				}
			} catch (e: Throwable) {
				result(e, false)
				(e.cause ?: e).let {
					if (args.flag("trace")) it.stackTraceToString() else it.toString()
				}
			}
			reply("```\n${result.toString().take(1950)}\n```")
		}
	}
}
