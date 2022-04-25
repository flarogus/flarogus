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

@OptIn(kotlin.time.ExperimentalTime::class)
fun createRootCommand() = createTree("flarogus") {
	subtree("fun") {
		subcommand<BufferedImage>("flaroficate") {
			val flarsusBase = ImageIO.read({}::class.java.getResource("/flarsus.png") ?: throw RuntimeException("aaaaa le flar has escaped"))

			description = "Turn a user into a flarogus. -i flag can be used to flaroficate an image."

			arguments {
				optional<Snowflake>("user", "The user you want to flaroficate. Defaults to you if -i is not present.")
				flag("image").alias('i')
			}

			action {
				val url = if (args.flag("image")) {
					originalMessage?.attachments?.find {
						it.isImage && it.width!! < 2000 && it.height!! < 2000
					}?.url ?: fail("no valid image attached")
				} else {
					(args.opt<Snowflake>("user") ?: originalMessage?.author?.id)?.getAvatarUrl() ?: fail("cannot determine user id")
				}
	
				val origin = ImageIO.read(URL(image))
				val flaroficate = ImageUtil.multiply(origin, flarsusBase)
				result(sussyImage)
			}
		}

		subcommand<String>("impostor") {
			val vowels = listOf('a', 'A', 'e', 'E', 'i', 'I', 'o', 'O', 'u', 'U', 'y', 'Y')

			description = "Display the name of the user as if they were an impostor")

			arguments {
				default<String>("user", "The user you want to turn into an impostor. Can be a string or a mention. Defaults to you.") {
					it.originalMessage?.data?.user?.username ?: "unknown user"
				}
			}

			action {
				var name = args.arg("user")
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

		//subcommand<BufferedImage>("merge")
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
