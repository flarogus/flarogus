package flarogus.util

import java.io.File
import java.time.*
import java.time.temporal.*
import kotlin.math.*
import kotlinx.coroutines.*
import kotlinx.datetime.toJavaInstant
import flarogus.*

inline val Int.second get() = this * 1000L
inline val Int.minute get() = this * 1000L * 60
inline val Int.hour get() = this * 1000L * 60 * 60
inline val Int.day get() = this * 1000L * 60 * 60 * 24
inline val Int.month get() = this * 1000L * 60 * 60 * 24 * 30
inline val Int.year get() = this * 1000L * 60 * 60 * 24 * 365

fun Any?.isNotNull() = this != null

fun Instant.atUTC(): ZonedDateTime = atZone(ZoneId.of("Z"))
fun TemporalAccessor.format(): String = Vars.dateFormatter.format(this)
fun Instant.formatUTC(): String = atUTC().format()

fun kotlinx.datetime.Instant.formatUTC() = toJavaInstant().formatUTC()

fun String.truncate(maxLength: Int, ellipsis: String = "..."): String {
	return if (length <= maxLength) {
		this
	} else {
		this.take(max(maxLength - ellipsis.length, 0)) + ellipsis
	}
}

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

fun File.ensureDir() = also {
	if (exists() && isDirectory().not()) delete()
	if (!exists()) mkdirs()
}
fun File.ensureFile() = also {
	if (exists() && isDirectory()) deleteRecursively()
}
