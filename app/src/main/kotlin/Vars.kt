package flarogus

import java.time.format.*
import java.util.concurrent.*
import javax.script.*
import kotlin.random.*
import kotlinx.coroutines.*
import dev.kord.common.entity.*
import dev.kord.core.*
import dev.kord.core.supplier.*
import flarogus.util.*
import flarogus.command.impl.*

/** An object declaration that stores variables/constants that are shared across the whole application */
object Vars {	
	/** The kord client instance */
	lateinit var client: Kord
	val supplier get() = client.defaultSupplier
	val restSupplier by lazy { RestEntitySupplier(client) }

	/** If true, the multiverse works in the test mode. */
	val testMode = true

	val rootCommand = createRootCommand()
	
	/** The unique bot id used for shutdown command */
	lateinit var ubid: String
	/** The moment the bot has started */
	var startedAt = -1L
	/** The start of flarogus epoch, aka the last hard reset */
	var flarogusEpoch = -1L
	
	/** Flarogus#0233 — discord */
	val botId: Snowflake get() = client.selfId;
	/** Mnemotechnican#9967 — discord */
	val ownerId = Snowflake(502871063223336990UL)
	/** Flarogus-central */
	val flarogusGuild = Snowflake(932524169034358877UL)
	
	val threadPool = Executors.newFixedThreadPool(5)
	val dateFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
	
	/** Superusers that are allowed to do most things */
	val superusers = mutableSetOf<Snowflake>(
		ownerId,
		691650272166019164UL.toSnowflake(), // smolkeys
		794686191467233280UL.toSnowflake(), // real sushi
		797257966973091862UL.toSnowflake()  // pineapple
	)

	/** Scripting engine */
	val scriptEngine = ScriptEngineManager(Thread.currentThread().contextClassLoader).getEngineByExtension("kts");
	/* Global scripting context */
	val scriptContext = SimpleScriptContext()

	/** ```language *some script* ``` */
	val codeblockRegex = "```([a-z]*)?((?s).*)```".toRegex()
	/** Default imports. Used for the script engine. */
	val defaultImports = arrayOf(
		"flarogus.*", "flarogus.util.*", "flarogus.multiverse.*", "ktsinterface.*", "dev.kord.core.entity.*", "dev.kord.core.entity.channel.*",
		"dev.kord.common.entity.*", "dev.kord.rest.builder.*", "dev.kord.rest.builder.message.*", "dev.kord.rest.builder.message.create.*",
		"dev.kord.core.behavior.*", "dev.kord.core.behavior.channel.*", "kotlinx.coroutines.*", "kotlinx.coroutines.flow.*", "kotlin.system.*",
		"kotlinx.serialization.*", "kotlinx.serialization.json.*", "flarogus.multiverse.state.*", "flarogus.multiverse.entity.*"
	).map { "import $it;" }.joinToString("")

	/** Whether to enable experimental stuff. Should be enabled only using the run command */
	var experimental = false
	
	fun loadState() {
		ubid = Random.nextInt(0, 1000000000).toString(10 + 26)
		startedAt = System.currentTimeMillis()
		flarogusEpoch = startedAt
	}
}
