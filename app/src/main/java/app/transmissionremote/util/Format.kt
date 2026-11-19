package app.transmissionremote.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatBytes(b: Long): String {
    if (b < 0) return "—"
    if (b < 1024) return "$b Б"
    val units = arrayOf("КБ", "МБ", "ГБ", "ТБ", "ПБ")
    var v = b.toDouble()
    var i = -1
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024
        i++
    }
    return String.format(Locale.getDefault(), if (v >= 100) "%.0f %s" else "%.1f %s", v, units[i])
}

fun formatSpeed(b: Long): String = formatBytes(b) + "/с"

fun formatEta(sec: Long): String {
    if (sec < 0) return "∞"
    val d = sec / 86400
    val h = sec % 86400 / 3600
    val m = sec % 3600 / 60
    val s = sec % 60
    return when {
        d > 0 -> "${d}д ${h}ч"
        h > 0 -> "${h}ч ${m}м"
        m > 0 -> "${m}м ${s}с"
        else -> "${s}с"
    }
}

fun formatDuration(sec: Long): String = if (sec <= 0) "—" else formatEta(sec)

fun formatDate(sec: Long): String =
    if (sec <= 0) "—" else SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(sec * 1000))

fun formatPercent(p: Double): String = String.format(Locale.getDefault(), "%.1f%%", p * 100)

fun formatRatio(r: Double): String = if (r < 0) "∞" else String.format(Locale.getDefault(), "%.2f", r)
