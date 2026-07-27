package one.larkin.ilotoki.ui.screens

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal actual fun dayStamp(millis: Long, now: Long): String {
    val entry = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance().apply { timeInMillis = now }
    if (sameDay(entry, today)) return "TODAY"
    today.add(Calendar.DAY_OF_YEAR, -1)
    if (sameDay(entry, today)) return "YESTERDAY"
    val locale = Locale.getDefault()
    return SimpleDateFormat("d MMMM", locale).format(Date(millis)).uppercase(locale)
}

private fun sameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
