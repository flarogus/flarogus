package flarogus.multiverse

import dev.kord.common.entity.*
import flarogus.*

val Channels = if (!Vars.testMode) NormalChannels else TestChannels

abstract class AbstractChannels(
	val multiverseChannelName: String,

	val latestState: Snowflake,
	val fileStorage: Snowflake,
	val log: Snowflake,
	val reports: Snowflake,

	val autorun: Snowflake
)

object NormalChannels : AbstractChannels(
	multiverseChannelName = "multiverse",

	latestState = Snowflake(937781472394358784UL),
	fileStorage = Snowflake(949667466156572742UL),
	log = Snowflake(942139405718663209UL),
	reports = Snowflake(944718226649124874UL),

	autorun = Snowflake(962823075357949982UL)
)

object TestChannels : AbstractChannels(
	multiverseChannelName = "test-multiverse",

	latestState = Snowflake(971766803401433131),
	fileStorage = Snowflake(971766838381936720),
	log = Snowflake(971766733562077276),
	reports = Snowflake(0UL), // unsupported.

	autorun = Snowflake(971766765065490482)
)
