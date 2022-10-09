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
import java.util.Vector

/** An object declaration that stores variables/constants that are shared across the whole application */
object Vars {	
	/** The kord client instance */
	lateinit var client: Kord
	val supplier get() = client.defaultSupplier
	val restSupplier by lazy { RestEntitySupplier(client) }

	/** If true, the multiverse works in the test mode. Must be set before compiling. */
	val testMode = false
	/** Whether to enable experimental stuff. May or may not be meaningless at the current moment, */
	var experimental = false

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

	val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
	
	/** Superusers that are allowed to do most things */
	val superusers = mutableSetOf(
		ownerId,
		691650272166019164UL.toSnowflake(), // smolkeys
		794686191467233280UL.toSnowflake(), // real sushi
		797257966973091862UL.toSnowflake()  // pineapple
	)

	val moderators = mutableSetOf(
		649306040604557322.toSnowflake() // bluewolf
	)

	/** Scripting engine */
	val scriptEngine by lazy {
		ScriptEngineManager(Thread.currentThread().contextClassLoader).getEngineByExtension("kts")!!
	}
	/* Global scripting context */
	val scriptContext = SimpleScriptContext()

	/** Markdown codeblock.  */
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
	
	fun loadState() {
		ubid = Random.nextInt(0, 1000000000).toString(10 + 26)
		startedAt = System.currentTimeMillis()
		flarogusEpoch = startedAt
	}
}
