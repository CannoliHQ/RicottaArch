package com.retroarch.browser.retroactivity

import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.PointerIcon
import android.view.View
import android.view.WindowManager
import com.retroarch.browser.preferences.util.ConfigFile
import com.retroarch.browser.preferences.util.UserPreferences
import dev.cannoli.ricotta.RicottaArchBridge

class RetroActivityFuture : RetroActivityCamera() {

    private var quitfocus = false
    private lateinit var mDecorView: View
    private var igmOverlay: IGMOverlay? = null

    private val mHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val state = msg.arg1 == HANDLER_ARG_TRUE
            when (msg.what) {
                HANDLER_WHAT_TOGGLE_IMMERSIVE -> attemptToggleImmersiveMode(state)
                HANDLER_WHAT_TOGGLE_POINTER_CAPTURE -> attemptTogglePointerCapture(state)
                HANDLER_WHAT_TOGGLE_POINTER_NVIDIA -> attemptToggleNvidiaCursorVisibility(state)
                HANDLER_WHAT_TOGGLE_POINTER_ICON -> attemptTogglePointerIcon(state)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isRunning = true
        mDecorView = window.decorView
        quitfocus = intent.hasExtra("QUITFOCUS")

        try {
            var gameTitle = intent.getStringExtra("IGM_GAME_TITLE")
            val stateBasePath = intent.getStringExtra("IGM_STATE_BASE_PATH") ?: ""
            val cannoliRoot = intent.getStringExtra("IGM_CANNOLI_ROOT") ?: ""
            val platformTag = intent.getStringExtra("IGM_PLATFORM_TAG") ?: ""
            val colorHighlight = intent.getStringExtra("IGM_COLOR_HIGHLIGHT")
            val colorText = intent.getStringExtra("IGM_COLOR_TEXT")
            val colorHighlightText = intent.getStringExtra("IGM_COLOR_HIGHLIGHT_TEXT")
            val colorAccent = intent.getStringExtra("IGM_COLOR_ACCENT")
            val colorTitle = intent.getStringExtra("IGM_COLOR_TITLE")

            if (gameTitle.isNullOrEmpty()) {
                val romPath = intent.getStringExtra("ROM")
                gameTitle = romPath?.substringAfterLast('/')
                    ?.substringBeforeLast('.') ?: "Game"
            }

            val bridge = RicottaArchBridge(stateBasePath)
            igmOverlay = IGMOverlay(
                this, bridge, gameTitle, cannoliRoot, platformTag,
                colorHighlight, colorText, colorHighlightText, colorAccent, colorTitle
            )
            igmOverlay?.onCreate(savedInstanceState)
        } catch (e: Exception) {
            Log.e("RicottaArch", "Failed to initialize IGM overlay", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        val newRom = intent.getStringExtra("ROM")
        val newCore = intent.getStringExtra("LIBRETRO")
        val currentRom = getIntent()?.getStringExtra("ROM")
        val currentCore = getIntent()?.getStringExtra("LIBRETRO")

        if ((newRom != null && newRom != currentRom) ||
            (newCore != null && newCore != currentCore)
        ) {
            val restartIntent = Intent(intent).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(restartIntent)
            System.exit(0)
        } else {
            setIntent(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        igmOverlay?.onResume()
        setSustainedPerformanceMode(sustainedPerformanceMode)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            intent.getStringExtra("REFRESH")?.let { refresh ->
                val params = window.attributes
                params.preferredRefreshRate = refresh.toFloat()
                window.attributes = params
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val configFile = ConfigFile(UserPreferences.getDefaultConfigPath(this))
                if (configFile.getBoolean("video_notch_write_over_enable")) {
                    window.attributes.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            } catch (e: Exception) {
                Log.w("RetroActivityFuture", "Key doesn't exist yet: ${e.message}")
            }
        }
    }

    override fun onPause() {
        igmOverlay?.onPause()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (quitfocus) System.exit(0)
    }

    override fun onDestroy() {
        igmOverlay?.onDestroy()
        super.onDestroy()
        isRunning = false
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        sendUiMessage(HANDLER_WHAT_TOGGLE_IMMERSIVE, hasFocus)

        try {
            val configFile = ConfigFile(UserPreferences.getDefaultConfigPath(this))
            if (configFile.getBoolean("input_auto_mouse_grab")) {
                inputGrabMouse(hasFocus)
            }
        } catch (e: Exception) {
            Log.w("RetroActivityFuture", "[onWindowFocusChanged] exception thrown: ${e.message}")
        }
    }

    private fun sendUiMessage(what: Int, state: Boolean) {
        val msg = mHandler.obtainMessage(what, if (state) HANDLER_ARG_TRUE else HANDLER_ARG_FALSE, -1)
        mHandler.sendMessageDelayed(msg, HANDLER_MESSAGE_DELAY_DEFAULT_MS.toLong())
    }

    fun inputGrabMouse(state: Boolean) {
        sendUiMessage(HANDLER_WHAT_TOGGLE_POINTER_CAPTURE, state)
        sendUiMessage(HANDLER_WHAT_TOGGLE_POINTER_NVIDIA, state)
        sendUiMessage(HANDLER_WHAT_TOGGLE_POINTER_ICON, state)
    }

    @Suppress("DEPRECATION")
    private fun attemptToggleImmersiveMode(state: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                mDecorView.systemUiVisibility = if (state) {
                    (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LOW_PROFILE
                            or View.SYSTEM_UI_FLAG_IMMERSIVE)
                } else {
                    (View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                }
            } catch (e: Exception) {
                Log.w("RetroActivityFuture", "[attemptToggleImmersiveMode] exception: ${e.message}")
            }
        }
    }

    private fun attemptTogglePointerCapture(state: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                if (state) mDecorView.requestPointerCapture() else mDecorView.releasePointerCapture()
            } catch (e: Exception) {
                Log.w("RetroActivityFuture", "[attemptTogglePointerCapture] exception: ${e.message}")
            }
        }
    }

    private fun attemptToggleNvidiaCursorVisibility(state: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            try {
                val method = InputManager::class.java.getMethod("setCursorVisibility", Boolean::class.javaPrimitiveType)
                val inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
                method.invoke(inputManager, !state)
            } catch (_: NoSuchMethodException) {
            } catch (e: Exception) {
                Log.w("RetroActivityFuture", "[attemptToggleNvidiaCursorVisibility] exception: ${e.message}")
            }
        }
    }

    private fun attemptTogglePointerIcon(state: Boolean) {
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.N until Build.VERSION_CODES.O) {
            try {
                mDecorView.pointerIcon = if (state) {
                    PointerIcon.getSystemIcon(this, PointerIcon.TYPE_NULL)
                } else null
            } catch (e: Exception) {
                Log.w("RetroActivityFuture", "[attemptTogglePointerIcon] exception: ${e.message}")
            }
        }
    }

    companion object {
        @JvmField
        @Volatile
        var isRunning = false

        private const val HANDLER_WHAT_TOGGLE_IMMERSIVE = 1
        private const val HANDLER_WHAT_TOGGLE_POINTER_CAPTURE = 2
        private const val HANDLER_WHAT_TOGGLE_POINTER_NVIDIA = 3
        private const val HANDLER_WHAT_TOGGLE_POINTER_ICON = 4
        private const val HANDLER_ARG_TRUE = 1
        private const val HANDLER_ARG_FALSE = 0
        private const val HANDLER_MESSAGE_DELAY_DEFAULT_MS = 300
    }
}
