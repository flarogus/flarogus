package flarogus

import kotlin.time.*
import kotlinx.coroutines.*;
import dev.kord.common.entity.*
import dev.kord.gateway.*
import dev.kord.core.*
import flarogus.util.*;
import flarogus.multiverse.*
import kotlin.system.exitProcess

suspend fun main(vararg args: String) {
	val botToken = args.getOrNull(0)
	if (botToken == null) {
		println("[ERROR] no token nor '--test' specified")
		exitProcess(1)
	} else if (botToken.trim() == "--test") {
		args.drop(1).map {
			runCatching {
				Vars.rootCommand(it)
			}.recover { e ->
				e.message ?: e
			}.getOrThrow().toString().replace("`", "")
		}.joinToString("\n").let { it: String ->
			println(it)
		}
		exitProcess(0)
	}

	if (args.getOrNull(1)?.lowercase() == "true") {
		Vars.testMode = true
	}

	Vars.launch(botToken)
	
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
	
	@OptIn(PrivilegedIntent::class)
	Vars.client.login {
		intents += Intent.MessageContent

		presence { competing("execute `!flarogus help` to see the list of available commands.") }
	}
}
