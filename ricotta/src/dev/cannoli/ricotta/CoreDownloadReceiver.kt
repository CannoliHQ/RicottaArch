package dev.cannoli.ricotta

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class CoreDownloadReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DOWNLOAD      = "dev.cannoli.ricotta.DOWNLOAD_CORE"
        const val ACTION_REFRESH_INFO  = "dev.cannoli.ricotta.REFRESH_CORE_INFO"
        const val ACTION_RESULT        = "dev.cannoli.ricotta.DOWNLOAD_CORE_RESULT"

        const val EXTRA_CORE        = "CORE"
        const val EXTRA_FORCE_INFO  = "FORCE_INFO_REFRESH"
        const val EXTRA_OK          = "OK"
        const val EXTRA_ERROR       = "ERROR"
        const val EXTRA_KIND        = "KIND"   // "core" | "info"

        private const val TAG = "CoreDownloadReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appCtx = context.applicationContext
        when (intent.action) {
            ACTION_DOWNLOAD -> {
                val core = intent.getStringExtra(EXTRA_CORE)
                if (core.isNullOrBlank()) {
                    Log.w(TAG, "DOWNLOAD_CORE missing EXTRA_CORE")
                    return
                }
                val force = intent.getBooleanExtra(EXTRA_FORCE_INFO, false)
                val pending = goAsync()
                Thread({
                    try {
                        val r = CoreDownloader.download(appCtx, core, force)
                        sendResult(appCtx, kind = "core", core = r.core, ok = r.ok, err = r.error)
                    } finally {
                        pending.finish()
                    }
                }, "ricotta-core-dl").start()
            }

            ACTION_REFRESH_INFO -> {
                val pending = goAsync()
                Thread({
                    var ok = true
                    var err: String? = null
                    try {
                        val dataDir = java.io.File(appCtx.applicationInfo.dataDir)
                        val infoDir = java.io.File(dataDir, "info")
                        CoreDownloader.ensureInfoFiles(infoDir, appCtx.cacheDir, force = true)
                    } catch (t: Throwable) {
                        ok = false
                        err = t.message ?: t.javaClass.simpleName
                        Log.w(TAG, "info refresh failed", t)
                    } finally {
                        sendResult(appCtx, kind = "info", core = null, ok = ok, err = err)
                        pending.finish()
                    }
                }, "ricotta-info-dl").start()
            }
        }
    }

    private fun sendResult(ctx: Context, kind: String, core: String?, ok: Boolean, err: String?) {
        val out = Intent(ACTION_RESULT).apply {
            putExtra(EXTRA_KIND, kind)
            if (core != null) putExtra(EXTRA_CORE, core)
            putExtra(EXTRA_OK, ok)
            if (err != null) putExtra(EXTRA_ERROR, err)
        }
        ctx.sendBroadcast(out)
    }
}
