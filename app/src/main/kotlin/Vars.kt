package flarogus

import java.time.format.*
import javax.script.*
import kotlin.random.*
import kotlinx.coroutines.*
import dev.kord.common.entity.*
import dev.kord.core.*
import dev.kord.core.supplier.*
import flarogus.util.*
import flarogus.command.CommandHandler
import flarogus.command.impl.*
import java.util.Vector

/** An object declaration that stores variables/constants that are shared across the whole application */
object Vars {	
	/** The kord client instance. Must not be accessed until [launch] is called. */
	lateinit var client: Kord
	val supplier get() = client.defaultSupplier
	val restSupplier by lazy { RestEntitySupplier(client) }

	/** If true, the multiverse works in the test mode. Must be set before compiling. */
	val testMode = false
	/** Whether to enable experimental stuff. May or may not be meaningless at the current moment, */
	var experimental = false

	val rootCommand get() = createRootCommand()
	val commandHandler by lazy { CommandHandler(client, rootCommand) }
	
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

	val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
	
	/** Superusers that are allowed to do most things */
	val superusers = mutableSetOf(
		ownerId,
		691650272166019164UL.toSnowflake(), // smolkeys
		797257966973091862UL.toSnowflake()  // pineapple
	)

	val moderators = mutableSetOf<Snowflake>()

	/** Scripting engine */
	val scriptEngine by lazy {
		ScriptEngineManager(Vars::class.java.classLoader).getEngineByExtension("kts")!!
	}
	/* Global scripting context */
	val scriptContext = SimpleScriptContext()

	/** Markdown codeblock regex. */
	val codeblockRegex = "```([a-z]*)?((?s).*)```".toRegex()
	/** Default imports. Used with the script engine. */
	val defaultImports by lazy {
		ClassLoader::class.java.getDeclaredField("classes")
			.let {
				it.isAccessible = true
				it.get(Vars::class.java.classLoader) as Vector<Class<*>>
			}
			.filter { "internal" !in it.name && "$" !in it.name }
			.map { it.name.substringBeforeLast('.') + ".*" }
			.distinct()
			.let { it + "ktsinterface.*" }
			.joinToString(";") { "import $it" }
	}
	
	/**
	 * Initialises the kord client and sets up some variables.
	 */
	suspend fun launch(token: String) {
		if (::client.isInitialized) error("the bot has alreary been initialised")

		client = Kord(token) {
			//requestHandler { KtorRequestHandler(it.httpClient, ParallelRequestRateLimiter(), token = botToken) }
		}

		ubid = Random.nextInt(0, 1000000000).toString(10 + 26)
		startedAt = System.currentTimeMillis()
		flarogusEpoch = startedAt

		commandHandler.launch()
	}
}
