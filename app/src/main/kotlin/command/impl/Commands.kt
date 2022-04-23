package flarogus.command.impl

import kotlin.time.*
import dev.kord.core.behavior.*
import flarogus.*
import flarogus.util.*
import flarogus.command.*
import flarogus.command.builder.*

@OptIn(kotlin.time.ExperimentalTime::class)
fun createRootCommand() = createTree("flarogus") {
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
