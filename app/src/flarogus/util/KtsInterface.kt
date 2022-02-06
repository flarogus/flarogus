package ktsinterface

import kotlinx.coroutines.*
import dev.kord.common.entity.*
import flarogus.*

lateinit var lastScope: CoroutineScope

inline fun launch(crossinline l: suspend CoroutineScope.() -> Unit) = flarogus.Vars.client.launch { l() };

inline fun createMessage(channel: ULong, message: String) = launch {
	Vars.client.unsafe.messageChannel(Snowflake(channel)).createMessage(message)
}
