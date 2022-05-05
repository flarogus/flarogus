package flarogus.multiverse

import dev.kord.common.entity.*
import flarogus.*

val Channels = if (!Vars.testMode) NormalChannels else TestChannels

abstract class AbstractChannels(
	val multiverseChannelName: String,

	val settings: Snowflake,
	val fileStorage: Snowflake,
	val log: Snowflake,

	val autorun: Snowflake
)

object NormalChannels : AbstractChannels(
	multiverseChannelName = "multiverse",

	settings = Snowflake(937781472394358784UL),
	fileStorage = Snowflake(949667466156572742UL),
	log = Snowflake(942139405718663209UL),

	autorun = Snowflake(962823075357949982UL)
)

object TestChannels : AbstractChannels(
	multiverseChannelName = "test-multiverse",

	settings = Snowflake(971766803401433131),
	fileStorage = Snowflake(971766838381936720),
	log = Snowflake(971766733562077276),

	autorun = Snowflake(971766765065490482)
)
