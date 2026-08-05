package one.larkin.ilotoki.model

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import one.larkin.ilotoki.MainActivity
import one.larkin.ilotoki.R
import one.larkin.ilotoki.appContext
import one.larkin.ilotoki.currentActivity
import one.larkin.ilotoki.ui.formatGiB
import one.larkin.ilotoki.ui.gibLabel

private const val CHANNEL_ID = "downloads"

/**
 * The first attempt, at IMPORTANCE_LOW. A channel's importance cannot be raised
 * from code once it exists — and recreating one under the same id restores what it
 * had — so moving up meant a new id and clearing this one away.
 */
private const val LEGACY_CHANNEL_ID = "model-download"

private const val NOTIFICATION_ID = 1

/**
 * Android freezes a cached process, so without a foreground service backgrounding
 * the app stops the transfer where it stands — no error, no notice, and on coming
 * back the figure has simply not moved. The service does no work itself; being
 * started is the whole of it, because that is what keeps the process out of the
 * freezer while the coroutine in ModelRepository carries on.
 *
 * `dataSync` is the type this fits, and on Android 15 and up it comes with a daily
 * budget of a few hours — a model is minutes, so the cap is not in reach.
 */
class ModelDownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildDownloadNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        // Restarting the service by itself would resume nothing — the download lives
        // in the app's own scope — so a killed process should not leave one behind.
        return START_NOT_STICKY
    }
}

/** What the notification currently says. Written by the download, read by the service. */
@Volatile private var announced: ModelSpec? = null

@Volatile private var latest: DownloadProgress = DownloadProgress(0, -1)

private var lastNotifiedAt = 0L

private const val NOTIFY_INTERVAL_MS = 1_000L

private val notifications: NotificationManager
    get() = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

actual fun downloadBegan(spec: ModelSpec, progress: DownloadProgress) {
    // A retry of the same transfer must not restart the service or re-ask for the
    // permission; the first attempt already did both.
    val alreadyRunning = announced != null
    announced = spec
    latest = progress
    if (alreadyRunning) return

    ensureChannel()
    requestNotificationPermission()
    val intent = Intent(appContext, ModelDownloadService::class.java)
    runCatching { appContext.startForegroundService(intent) }
}

actual fun downloadProgressed(progress: DownloadProgress) {
    latest = progress
    if (announced == null) return
    // The download reports every 4 MB, which on a fast connection is several times
    // a second; the system drops updates that come faster than it likes anyway.
    val now = SystemClock.elapsedRealtime()
    if (now - lastNotifiedAt < NOTIFY_INTERVAL_MS) return
    lastNotifiedAt = now
    runCatching { notifications.notify(NOTIFICATION_ID, buildDownloadNotification()) }
}

actual fun downloadEnded() {
    if (announced == null) return
    announced = null
    lastNotifiedAt = 0L
    runCatching { appContext.stopService(Intent(appContext, ModelDownloadService::class.java)) }
}

private fun ensureChannel() {
    val channel = NotificationChannel(
        CHANNEL_ID,
        "getting a translator",
        // Default rather than low, which is the counter-intuitive part: from Android
        // 12 a silent notification is kept out of the status bar entirely, and
        // IMPORTANCE_LOW is silent. The icon in the status bar is most of the point
        // — it is what says «this is still going» when the app is not on screen — so
        // the channel has to be default, and the quiet comes from having no sound
        // and no vibration instead. Default does not peek; only high does that.
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply {
        description = "progress while a translator downloads"
        setShowBadge(false)
        setSound(null, null)
        enableVibration(false)
    }
    runCatching {
        notifications.deleteNotificationChannel(LEGACY_CHANNEL_ID)
        notifications.createNotificationChannel(channel)
    }
}

private fun buildDownloadNotification(): Notification {
    val progress = latest
    val total = announced?.sizeBytes ?: progress.total
    val percent = if (total > 0) ((progress.downloaded * 100) / total).toInt() else 0
    val text = if (total > 0) {
        "$percent% · ${formatGiB(progress.downloaded)} / ${gibLabel(total)}"
    } else {
        gibLabel(progress.downloaded)
    }

    val open = PendingIntent.getActivity(
        appContext,
        0,
        Intent(appContext, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    return Notification.Builder(appContext, CHANNEL_ID)
        // The mark alone, laid out for 24 dp — not the launcher's monochrome layer,
        // which carries adaptive-icon padding and would draw a speck.
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("getting the translator")
        .setContentText(text)
        // Collapsed, the bar takes the text line's place, so the figure would only
        // be there for whoever thinks to expand it. The header carries it instead.
        .setSubText(if (total > 0) "$percent%" else null)
        .setProgress(100, percent, total <= 0)
        .setOngoing(true)
        // Otherwise every update re-announces itself on the lock screen.
        .setOnlyAlertOnce(true)
        .setContentIntent(open)
        .build()
}

/**
 * Asks for POST_NOTIFICATIONS at the moment a download starts.
 *
 * Not at launch: the app has nothing to notify about until someone has decided to
 * spend a gigabyte, and asking before there is anything to show is how permission
 * prompts get denied out of hand. Refusing it costs only the progress bar — the
 * service still runs and the download still finishes.
 */
private fun requestNotificationPermission() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val granted = appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    if (granted) return
    val activity = currentActivity ?: return
    runCatching { activity.requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1) }
}
