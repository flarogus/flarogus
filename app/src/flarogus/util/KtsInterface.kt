package ktsinterface

import kotlinx.coroutines.*
import dev.kord.common.entity.*
import flarogus.*

val global = HashMap<String, Any?>(50)

inline fun launch(crossinline l: suspend CoroutineScope.() -> Unit) = flarogus.Vars.client.launch { l() };

inline fun <R> async(crossinline l: suspend CoroutineScope.() -> R) = flarogus.Vars.client.async { l() };

inline fun createMessage(channel: ULong, message: String) = launch {
	Vars.client.unsafe.messageChannel(Snowflake(channel)).createMessage(message)
};

inline fun fetchMessage(channel: ULong, message: ULong) = Vars.client.async {
	Vars.supplier.getMessage(Snowflake(channel), Snowflake(message))
}
