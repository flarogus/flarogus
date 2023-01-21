package flarogus

import dev.kord.common.entity.*
import dev.kord.core.*
import flarogus.util.*;
import flarogus.multiverse.*
import kotlinx.coroutines.*;
import kotlin.system.exitProcess
import picocli.CommandLine
import picocli.CommandLine.Command // these need to be imported one-by-one. otherwise kapt dies.
import picocli.CommandLine.Parameters
import picocli.CommandLine.Option

const val flarogusVersion = "v1.0"

fun main(vararg args: String) {
	val exitCode = CommandLine(MainCommand()).execute(*args)
	exitProcess(exitCode)
}

@Command(
	name = "flarogus",
	description = ["The root command of the flarogus executable. Only cotains subcommands."],
	subcommands = [FlarogusLauncher::class, CommandTester::class],
	mixinStandardHelpOptions = true,
	version = ["Flarogus $flarogusVersion"]
)
open class MainCommand

@Command(
	name = "launch",
	description = ["Launch flarogus."],
	version = ["Flarogus launcher $flarogusVersion"],
	mixinStandardHelpOptions = true
)
open class FlarogusLauncher : Runnable {
	@Option(names = ["--test", "-t"], description = ["Run the bot with [Vars.test = true]."])
	var testMode = false

	@Option(names = ["--singleverse", "-s"], description = ["Prevent the multiverse from starting up."])
	var singleverse = false

	@Parameters(index = "0", description = ["The bot's token which will be used to log into discord."])
	lateinit var token: CharArray

	override fun run() = runBlocking {
		if (testMode) {
			Vars.testMode = true
			Log.info { "Running in test mode." }
		}

		Vars.launch(String(token), login = true, launchMultiverse = singleverse.not())

		// clear the token char array just in case
		repeat(token.size) { token[it] = '\u0000' }

		Vars.client.launch {
			delay(10_000L)

			if (Vars.multiverse.isRunning) {
				// something is wrong, this happens sometimes
				if (Vars.multiverse.guilds.filter { it.isValid }.size <= 0) {
					Log.error { "No valid guilds found! Forcibly updating all guilds!" }
					Vars.multiverse.guilds.forEach {
						it.lastUpdate = 0L
						it.update()
					}
				}
			}
		}
		Vars.client.coroutineContext[Job]!!.join() // wait until the client is stopped
	}
}


@Command(
	name = "test-command",
	description = ["Test flarogus commands passed as arguments."],
	version = ["Flarogus launcher $flarogusVersion"],
	mixinStandardHelpOptions = true
)
open class CommandTester : Runnable {
	@Parameters(description = ["Commands to test."])
	lateinit var commands: Array<String>

	override fun run() = runBlocking {
		commands
			.map { it.removePrefix("!flarogus").trim() }
			.forEach {
				runCatching { Vars.rootCommand(it) }
					.recover { it }
					.getOrThrow()
					.toString()
					.replace("`", "")
					.let(::println)
				println()
			}
	}
}
