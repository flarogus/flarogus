package flarogus

import dev.kord.common.entity.*
import dev.kord.core.*
import dev.kord.core.entity.Message
import dev.kord.core.supplier.*
import dev.kord.gateway.*
import flarogus.util.*
import flarogus.command.CommandHandler
import flarogus.command.impl.*
import flarogus.multiverse.*
import flarogus.multiverse.npc.NPC
import flarogus.multiverse.npc.impl.*
import flarogus.multiverse.service.*
import flarogus.multiverse.state.StateManager
import java.time.format.*
import javax.script.*
import kotlin.random.*
import kotlin.reflect.full.createType
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.*
import kotlin.script.experimental.jvmhost.*
import kotlinx.coroutines.*

/** An object declaration that stores variables/constants that are shared across the whole application */
object Vars {
	/** The global coroutine dispatcher. */
	val dispatcher = newFixedThreadPoolContext(8, "flarogus")

	/** The kord client instance. Must not be accessed until [launch] is called. */
	lateinit var client: Kord
	val supplier get() = client.defaultSupplier
	val restSupplier by lazy { RestEntitySupplier(client) }

	val npcs = mutableListOf<NPC>(AmogusNPC())
	val subreddits = mutableListOf("femboymemes", "hopeposting", "unixporn")
	
	/** The Multiverse. */
	lateinit var multiverse: Multiverse
	val infoMessageService = InfoMessageService()
	val npcService = NPCService(npcs)
	val markovService = MarkovChainService()
	val redditRepostService = RedditRepostService(8.hour, subreddits)

	/** If true, the multiverse works in the test mode. Do not change at runtime. */
	var testMode = false
		set(value) {
			if (::client.isInitialized) error("do not.")
			field = value
		}
	/** Whether to enable experimental stuff. May or may not be meaningless at the current moment, */
	var experimental = false

	val rootCommand = createRootCommand()
	val commandHandler by lazy { CommandHandler(client, rootCommand) }
	
	/** The unique bot id used for shutdown command. */
	lateinit var ubid: String
	/** The moment this bot instance has started. */
	var startedAt = -1L
	/** The start of flarogus epoch, aka the last hard reset. */
	var flarogusEpoch = -1L
	
	/** Flarogus#0233 — discord */
	val botId: Snowflake get() = client.selfId;
	/** Mnemotechnican#9967 — discord */
	val ownerId = Snowflake(502871063223336990UL)

	/** Superusers that are allowed to do most things */
	val superusers = mutableSetOf(
		ownerId,
		691650272166019164UL.toSnowflake(), // smolkeys
		797257966973091862UL.toSnowflake()  // pineapple
	)

	val moderators = mutableSetOf<Snowflake>()

	val scriptCompiler by lazy { JvmScriptCompiler() }
	val scriptEvaluator by lazy { kotlin.script.experimental.jvm.BasicJvmScriptEvaluator() }
	val scriptCompileConfig by lazy {
		ScriptCompilationConfiguration {
			providedProperties("message" to Message::class.createType(nullable = true))
			jvm { dependenciesFromCurrentContext(wholeClasspath = true) }
		}
	}

	/** Markdown codeblock regex. */
	val codeblockRegex = "```([a-z]*)?((?s).*)```".toRegex()
	/** Default imports. Used with the scripting api. */
	val defaultImports by lazy {
		Vars::class.java.getResourceAsStream("/import-classpath.txt")
			?.bufferedReader()
			?.use { it.readText() }
			?.lines()
			.orEmpty()
	}

	val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
	
	/**
	 * Initialises and sets up everything.
	 * @login if true, an attempt to log into the account is made.
	 * @launchMultiverse if true, the multiverse is launched.
	 */
	suspend fun launch(
		token: String,
		login: Boolean,
		launchMultiverse: Boolean
	) {
		if (::client.isInitialized) error("the bot has alreary been initialised")

		client = Kord(token) {
			stackTraceRecovery = true
			defaultDispatcher = dispatcher
		}

		ubid = Random.nextInt(0, 1000000000).toString(10 + 26)
		startedAt = System.currentTimeMillis()

		// if possible. load the previous multiverse. Otherwise, create a new one.
		runCatching { StateManager.loadState() }.onSuccess {
			it.loadFromState() // it will load the multiverse as well
			Log.info { "State loaded successfully." }
		}.onFailure {
			Log.error(it) { "An exception has occurred while loading the state" }

			flarogusEpoch = startedAt
			multiverse = Multiverse()

			Log.info { "Couldn't load the state. Created a new multiverse." }
		}
		arrayOf(infoMessageService, npcService, markovService, redditRepostService)
			.forEach(multiverse::addService)

		commandHandler.launch()

		// must login before launching the multiverse to receive the gateway events
		if (login) client.launch {
			@OptIn(PrivilegedIntent::class)
			Vars.client.login {
				intents += Intent.MessageContent

				presence { competing("execute `!flarogus help` to see the list of available commands.") }
			}
		}
		
		var errors = 0
		if (launchMultiverse) {
			// try to boot the multiverse up
			while (!multiverse.isRunning && Vars.client.isActive) {
				try {
					multiverse.start()
				} catch (e: Exception) {
					Log.error(e) { "Couldn't start the multiverse (${++errors})" }
					multiverse.stop()
					delay(3000L * errors)
				}
			}
		}
		Log.info { "Flarogus instance $ubid has started with $errors errors." }
	}
}
