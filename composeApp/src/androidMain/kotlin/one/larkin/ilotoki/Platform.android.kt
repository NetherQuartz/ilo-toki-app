package one.larkin.ilotoki

import android.app.Application
import android.content.Context
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

class IloTokiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        // ggml picks the CPU backend matching this device; it has to know where the
        // backend libraries were unpacked before any model is loaded.
        initLlmBackends(this)
    }
}

// filesDir is app-private and, unlike external storage, needs no permission.
// android:allowBackup is off in the manifest, so the 2 GB model is never uploaded.
actual fun modelsDirectory(): String =
    File(appContext.filesDir, "models").apply { mkdirs() }.absolutePath
