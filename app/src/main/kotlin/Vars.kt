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
import kotlin.math.*
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

/** An object declaration that stores variables/constants that are shared across the whole application */
object Vars {	
	/** The kord client instance */
	lateinit var client: Kord
	val supplier get() = client.defaultSupplier
	val restSupplier by lazy { RestEntitySupplier(client) }

	/** If true, the multiverse works in the test mode. */
	val testMode = false

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
	
	val threadPool: ExecutorService = Executors.newFixedThreadPool(5)
	val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm")
	
	/** Superusers that are allowed to do most things */
	val superusers = mutableSetOf(
		ownerId,
		691650272166019164UL.toSnowflake(), // smolkeys
		794686191467233280UL.toSnowflake(), // real sushi
		797257966973091862UL.toSnowflake()  // pineapple
	)

	val moderators = mutableSetOf<Snowflake>(
		649306040604557322.toSnowflake() // bluewolf
	)

	val scriptHost by lazy { BasicJvmScriptingHost() }

	/** Markdown codeblock. */
	val codeblockRegex = "```([a-z]*)?((?s).*)```".toRegex()

	/** Whether to enable experimental stuff. Should be enabled only using the run command */
	var experimental = false
	
	fun loadState() {
		ubid = Random.nextInt(0, (10 + 26f).pow(5).toInt()).toString(10 + 26)
		startedAt = System.currentTimeMillis()
		flarogusEpoch = startedAt
	}
}
