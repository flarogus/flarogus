package flarogus.multiverse

import dev.kord.common.entity.*
import flarogus.*

val Config get() = if (!Vars.testMode) NormalConfig else TestConfig

abstract class AbstractConfig(
	val multiverseChannelName: String,
	val dataDirectoryName: String,

	/** Flarogus-central */
	val flarogusGuild: Snowflake,

	val latestState: Snowflake,
	val fileStorage: Snowflake,
	val log: Snowflake,
	val reports: Snowflake,

	val autorun: Snowflake
)

object NormalConfig : AbstractConfig(
	multiverseChannelName = "multiverse",
	dataDirectoryName = "flarogus",

	flarogusGuild = Snowflake(932524169034358877UL),

	latestState = Snowflake(937781472394358784UL),
	fileStorage = Snowflake(949667466156572742UL),
	log = Snowflake(942139405718663209UL),
	reports = Snowflake(944718226649124874UL),

	autorun = Snowflake(962823075357949982UL)
)

object TestConfig : AbstractConfig(
	multiverseChannelName = "test-multiverse",
	dataDirectoryName = "flarogus-test",

	flarogusGuild = Snowflake(932524169034358877UL),

	latestState = Snowflake(971766803401433131),
	fileStorage = Snowflake(971766838381936720),
	log = Snowflake(971766733562077276),
	reports = Snowflake(0UL), // unsupported.

	autorun = Snowflake(971766765065490482)
)
