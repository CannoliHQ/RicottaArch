package dev.cannoli.ricotta

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import dev.cannoli.igm.EmulatorBridge
import java.io.File

class RicottaArchBridge(
    private val stateBasePath: String
) : EmulatorBridge {

    override val supportsNativeMenu = true
    override val supportsAchievements = true
    override val supportsUndo = true

    private var onMenuClosedCallback: (() -> Unit)? = null
    var onMenuButtonPressed: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        nativeInit()
    }

    fun destroy() {
        nativeDestroy()
    }

    /**
     * Called from C via JNI when RetroArch's menu closes.
     * Posts the callback to the main thread.
     */
    @Suppress("unused")
    fun onNativeMenuClosed() {
        mainHandler.post {
            onMenuClosedCallback?.invoke()
        }
    }

    /**
     * Called from C via JNI when the menu button is pressed on the gamepad.
     * NativeActivity routes input to C directly, bypassing Java dispatchKeyEvent.
     */
    @Suppress("unused")
    fun onMenuButtonPressed() {
        mainHandler.post {
            onMenuButtonPressed?.invoke()
        }
    }

    /** Debug: called from C for every key down event to show keycode via Toast */
    var onDebugKey: ((Int) -> Unit)? = null

    @Suppress("unused")
    fun onDebugKey(keycode: Int) {
        mainHandler.post {
            onDebugKey?.invoke(keycode)
        }
    }

    fun setIGMVisible(visible: Boolean) {
        nativeSetIGMVisible(visible)
    }

    // EmulatorBridge implementation

    override fun reset() = nativeReset()
    override fun quit() = nativeQuit()
    override fun pause() = nativePause()
    override fun unpause() = nativeUnpause()
    override fun isPaused() = nativeIsPaused()

    override fun saveState(slot: Int) = nativeSaveState(slot)
    override fun loadState(slot: Int) = nativeLoadState(slot)
    override fun undoSaveState() = nativeUndoSaveState()
    override fun undoLoadState() = nativeUndoLoadState()
    override fun getStateSlotCount() = 11

    override fun getStateThumbnail(slot: Int): Bitmap? {
        val path = statePathForSlot(slot) + ".png"
        val file = File(path)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(path)
    }

    override fun stateExists(slot: Int): Boolean {
        return File(statePathForSlot(slot)).exists()
    }

    override fun getDiskCount() = 0  // TODO: implement
    override fun getDiskIndex() = 0  // TODO: implement
    override fun setDiskIndex(index: Int) {}  // TODO: implement
    override fun getDiskLabel(index: Int): String? = null

    override fun openNativeMenu() = nativeMenuToggle()
    override fun openAchievementsMenu() = nativeMenuToggle()

    override fun setOnNativeMenuClosed(callback: () -> Unit) {
        onMenuClosedCallback = callback
    }

    private fun statePathForSlot(slot: Int): String {
        return if (slot == 0) "$stateBasePath.auto"
        else if (slot == 1) stateBasePath
        else "$stateBasePath${slot - 1}"
    }

    // Native methods
    private external fun nativeInit()
    private external fun nativeDestroy()
    private external fun nativeSaveState(slot: Int)
    private external fun nativeLoadState(slot: Int)
    private external fun nativeUndoSaveState()
    private external fun nativeUndoLoadState()
    private external fun nativeReset()
    private external fun nativeQuit()
    private external fun nativePause()
    private external fun nativeUnpause()
    private external fun nativeIsPaused(): Boolean
    private external fun nativeMenuToggle()
    private external fun nativeSetIGMVisible(visible: Boolean)

    companion object {
        init {
            // The native library is already loaded by RetroActivityCommon
            // System.loadLibrary("retroarch-activity")
        }
    }
}
