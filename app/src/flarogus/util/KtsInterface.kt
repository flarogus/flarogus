package ktsinterface

import kotlinx.coroutines.*;

lateinit var lastScope: CoroutineScope

fun launch(l: CoroutineScope.() -> Unit) = lastScope.l()