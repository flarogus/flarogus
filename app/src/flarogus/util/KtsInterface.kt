package ktsinterface

import kotlinx.coroutines.*;

lateinit var lastScope: CoroutineScope

inline fun launch(crossinline l: suspend CoroutineScope.() -> Unit) = flarogus.Vars.client.launch { l() };