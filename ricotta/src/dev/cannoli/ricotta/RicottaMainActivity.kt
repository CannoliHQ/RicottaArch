package dev.cannoli.ricotta

import android.app.AlertDialog
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.preference.PreferenceActivity
import android.preference.PreferenceManager
import android.provider.Settings
import android.util.Log
import com.retroarch.BuildConfig
import com.retroarch.browser.mainmenu.MainMenuActivity
import com.retroarch.browser.preferences.util.UserPreferences
import com.retroarch.browser.retroactivity.RetroActivityFuture

/**
 * Ricotta launcher activity. Replaces upstream MainMenuActivity so we can use
 * MANAGE_EXTERNAL_STORAGE (one-time "All Files Access" grant) instead of the
 * legacy READ/WRITE_EXTERNAL_STORAGE loop, which Android auto-denies on API 30+
 * and therefore prompts on every launch.
 */
class RicottaMainActivity : PreferenceActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MainMenuActivity.PACKAGE_NAME = packageName
        setVolumeControlStream(AudioManager.STREAM_MUSIC)
        UserPreferences.updateConfigFile(this)

        when {
            BuildConfig.PLAY_STORE_BUILD -> finalStartup()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> checkAllFilesAccess()
            else -> finalStartup() // API 28-29: app-scoped storage is sufficient
        }
    }

    /** API 30+: request MANAGE_EXTERNAL_STORAGE via system settings, once. */
    private fun checkAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            finalStartup()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Storage access")
            .setMessage(
                "RicottaArch needs \"All Files Access\" to read your games. " +
                "Tap OK to grant it once in system settings — you won't be asked again."
            )
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                try {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            .setData(Uri.parse("package:$packageName"))
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "App-specific settings unavailable, falling back", e)
                    startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
            .setNegativeButton("Skip") { _, _ -> finalStartup() }
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Re-check after returning from Settings. If granted, start; otherwise let the
        // dialog above remain / user can back out.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            && Environment.isExternalStorageManager()
            && !RetroActivityFuture.isRunning
            && !BuildConfig.PLAY_STORE_BUILD) {
            finalStartup()
        }
    }

    private fun finalStartup() {
        val retro = Intent(this, RetroActivityFuture::class.java)

        if (RetroActivityFuture.isRunning) {
            retro.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        } else {
            retro.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            MainMenuActivity.startRetroActivity(
                retro,
                null,
                prefs.getString("libretro_path", "${applicationInfo.dataDir}/cores/"),
                UserPreferences.getDefaultConfigPath(this),
                Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD),
                applicationInfo.dataDir,
                applicationInfo.sourceDir
            )
        }
        startActivity(retro)
        finish()
    }

    companion object {
        private const val TAG = "RicottaMainActivity"
    }
}
