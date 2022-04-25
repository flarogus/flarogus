package flarogus.command.impl

import java.net.*
import java.awt.image.*
import javax.imageio.*
import kotlin.time.*
import dev.kord.common.entity.*
import dev.kord.core.behavior.*
import flarogus.*
import flarogus.util.*
import flarogus.command.*
import flarogus.command.builder.*
import flarogus.multiverse.*

@OptIn(kotlin.time.ExperimentalTime::class)
fun createRootCommand() = createTree("flarogus") {
	subtree("fun") {
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
			}
		}
	}
}
