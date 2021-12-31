package flarogus

import java.io.*;
import kotlin.random.*;
import kotlin.time.*
import kotlin.concurrent.*;
import kotlinx.coroutines.*;
import kotlinx.coroutines.flow.*;
import dev.kord.core.*
import dev.kord.core.event.*
import dev.kord.core.event.message.*
import dev.kord.core.supplier.*;
import dev.kord.core.entity.*;
import flarogus.util.*;
import flarogus.commands.*;

suspend fun main(vararg args: String) = runBlocking {
	val token = args.getOrNull(0)
	if (token == null) {
		println("[ERROR] no token specified")
		return@runBlocking
	}
	
	Vars.loadState()
	Vars.client = Kord(token)
	
	Vars.client.events
		.filterIsInstance<MessageCreateEvent>()
		.filter { it.message.author?.isBot == false }
		.filter { it.message.content.startsWith(Vars.prefix) }
		.onEach { CommandHandler.handle(this, it.message.content.substring(Vars.prefix.length), it) }
		.launchIn(Vars.client)
	
	initCommands()
	
	launch {
		delay(1000 * 60 * 60 * 5L);
		Vars.client.shutdown(); //shutdown after 5 hours — execution time of a single GitHub Actions job is limited to 6 hours
		Vars.saveState()
	}
	
	fixedRateTimer("autosaves", true, period = 10000L) {
		Vars.saveState() //save state every 10 seconds. shouldn't cause much of an issue since it's very lightweight
	}
	
	println("initialized");
	Vars.client.login()
}