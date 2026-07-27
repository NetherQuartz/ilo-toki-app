package one.larkin.ilotoki.ui.screens

import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.dateWithTimeIntervalSince1970

internal actual fun dayStamp(millis: Long, now: Long): String {
    val date = NSDate.dateWithTimeIntervalSince1970(millis / 1000.0)
    val calendar = NSCalendar.currentCalendar
    if (calendar.isDateInToday(date)) return "TODAY"
    if (calendar.isDateInYesterday(date)) return "YESTERDAY"
    val formatter = NSDateFormatter().apply { dateFormat = "d MMMM" }
    return formatter.stringFromDate(date).uppercase()
}
