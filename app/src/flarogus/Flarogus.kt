package flarogus

import java.io.*;
import kotlin.random.*;
import kotlin.time.*
import kotlin.concurrent.*;
import kotlinx.coroutines.*;
import kotlinx.coroutines.flow.*;
import dev.kord.rest.builder.message.create.*
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
		.onEach { 
			try {
				CommandHandler.handle(this, it.message.content.substring(Vars.prefix.length), it)
			} catch (e: Exception) {} //how sad, don't care. we don't tolerate any exceptions!
		}.launchIn(Vars.client)
	
	initCommands()
	
	launch {
		//shutdown after 5.5 hours. This should never happen: the instance should shut itself down after noticing there's another instance running
		delay(1000 * 60 * 60 * 5L + 1000 * 60 * 30L)
		
		Multiverse.brodcast { 
			embed { description = "A Multiverse instance is restarting." }
		}
		
		delay(6000L)
		Vars.client.shutdown();                  
		//Vars.saveState()
	}
	
	launch {
		delay(15000L)
		Multiverse.start()
	}
	
	println("initialized");
	Vars.client.login()
}