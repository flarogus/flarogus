package flarogus.command.impl

import java.awt.image.*
import javax.imageio.*
import kotlin.time.*
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

			arguments {
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
