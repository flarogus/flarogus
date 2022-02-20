package ktsinterface

import kotlinx.coroutines.*
import dev.kord.common.entity.*
import flarogus.*

inline fun launch(crossinline l: suspend CoroutineScope.() -> Unit) = flarogus.Vars.client.launch { l() };

inline fun <R> async(crossinline l: suspend CoroutineScope.() -> R) = flarogus.Vars.client.async { l() };

inline fun createMessage(channel: ULong, message: String) = launch {
	Vars.client.unsafe.messageChannel(Snowflake(channel)).createMessage(message)
};

inline fun fetchMessage(channel: ULong, message: ULong) = Vars.client.async {
	Vars.client.defaultSupplier.getMessage(Snowflake(channel), Snowflake(message))
}
