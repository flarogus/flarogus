package flarogus.multiverse

import dev.kord.common.entity.*
import flarogus.*

val Config get() = if (!Vars.testMode) NormalConfig else TestConfig

abstract class AbstractConfig(
	val multiverseChannelName: String,
	val dataDirectoryName: String,

	/** Flarogus-central */
	val flarogusGuild: Snowflake,

	val fileStorage: Snowflake,
	val reports: Snowflake,
)

object NormalConfig : AbstractConfig(
	multiverseChannelName = "multiverse",
	dataDirectoryName = "flarogus",

	flarogusGuild = Snowflake(932524169034358877UL),

	fileStorage = Snowflake(949667466156572742UL),
	reports = Snowflake(944718226649124874UL),
)

object TestConfig : AbstractConfig(
	multiverseChannelName = "test-multiverse",
	dataDirectoryName = "flarogus-test",

	flarogusGuild = Snowflake(932524169034358877UL),

	fileStorage = Snowflake(971766838381936720),
	reports = Snowflake(0UL), // unsupported.
)
