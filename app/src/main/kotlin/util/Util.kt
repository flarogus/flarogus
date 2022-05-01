package flarogus.util

import java.time.*
import java.time.temporal.*
import kotlinx.coroutines.*
import flarogus.*

fun Any?.isNotNull() = this != null

fun Instant.atUTC() = atZone(ZoneId.of("Z"))
fun TemporalAccessor.format() = Vars.dateFormatter.format(this)
fun Instant.formatUTC() = atUTC().format()

/** Waits up to [limit] ms for [condition] to become true. Returns true if the condition returned true, false if [limit] was reached. */
suspend inline fun delayUntil(limit: Long, period: Long = 50L, condition: () -> Boolean): Boolean {
	if (condition()) return true

	val begin = System.currentTimeMillis()
	do {
		delay(period)
		if (condition()) return true
	} while (System.currentTimeMillis() < begin + limit)

	return false
}
