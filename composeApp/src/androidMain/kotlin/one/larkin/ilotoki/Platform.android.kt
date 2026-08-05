package one.larkin.ilotoki

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import one.larkin.ilotoki.llm.initLlmBackends
import java.io.File

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

/**
 * Set before anything else runs so common code can reach app-private storage
 * without every call site having to thread a Context through.
 */
internal lateinit var appContext: Context
    private set

/**
 * The activity on screen, or null while the app is in the background.
 *
 * Only a runtime permission needs it — asking for one has to go through an
 * activity, and the request is made when a download starts rather than at launch.
 * Everything else in the app gets by with [appContext].
 */
internal var currentActivity: Activity? = null
    private set

class IloTokiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        // ggml picks the CPU backend matching this device; it has to know where the
        // backend libraries were unpacked before any model is loaded.
        initLlmBackends(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                currentActivity = activity
            }

            override fun onActivityPaused(activity: Activity) {
                if (currentActivity === activity) currentActivity = null
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, out: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}

// filesDir is app-private and, unlike external storage, needs no permission.
// android:allowBackup is off in the manifest, so the 2 GB model is never uploaded.
actual fun modelsDirectory(): String =
    File(appContext.filesDir, "models").apply { mkdirs() }.absolutePath

// usableSpace, not freeSpace: it accounts for the reserve the system keeps back,
// so it is the figure that decides whether a download will actually fit.
actual fun freeDiskBytes(): Long = runCatching { File(modelsDirectory()).usableSpace }.getOrDefault(0L)

actual val appVersion: String
    get() = runCatching {
        appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull() ?: ""

actual fun openUrl(url: String) {
    // A phone with no browser at all is possible; there is nothing useful to say
    // about it from an about card, so the tap simply does nothing.
    runCatching {
        appContext.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
