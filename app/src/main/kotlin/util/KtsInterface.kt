/**
 * This package is supposed to be used in kotlin scripts and only in them.
 */
package ktsinterface

import javax.script.*
import kotlinx.coroutines.*
import dev.kord.common.entity.*
import dev.kord.core.entity.*
import flarogus.*

inline val Map<String, out Any?>.message get() = this["message"] as Message

inline fun launch(crossinline l: suspend CoroutineScope.() -> Unit) = flarogus.Vars.client.launch { l() }
inline fun <R> async(crossinline l: suspend CoroutineScope.() -> R) = flarogus.Vars.client.async { l() }
