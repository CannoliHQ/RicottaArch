package com.retroarch.browser.retroactivity

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.cannoli.igm.IGMController
import dev.cannoli.igm.IGMScreen
import dev.cannoli.igm.InGameMenu
import dev.cannoli.igm.InGameMenuOptions
import dev.cannoli.igm.ui.theme.CannoliColors
import dev.cannoli.igm.ui.theme.CannoliTheme
import dev.cannoli.igm.ui.theme.LocalCannoliColors
import dev.cannoli.igm.ui.theme.hexToColor
import dev.cannoli.igm.ui.theme.initFonts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import dev.cannoli.ricotta.RicottaArchBridge

/**
 * Custom lifecycle owner for hosting Compose inside NativeActivity,
 * which does not implement LifecycleOwner or SavedStateRegistryOwner.
 */
private class IGMLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun performCreate(savedState: Bundle?) {
        savedStateRegistryController.performRestore(savedState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun performStart() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    fun performResume() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun performPause() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    }

    fun performStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    fun performDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

/**
 * Manages a Compose overlay on top of a NativeActivity for the In-Game Menu (IGM).
 *
 * Since NativeActivity doesn't provide the lifecycle interfaces that Compose requires,
 * this class manually sets up a LifecycleOwner and SavedStateRegistryOwner and attaches
 * them to the ComposeView's view tree.
 */
class IGMOverlay(
    private val activity: Activity,
    private val bridge: RicottaArchBridge,
    gameTitle: String,
    private val cannoliRoot: String = "",
    private val platformTag: String = "",
    private val colorHighlight: String? = null,
    private val colorText: String? = null,
    private val colorHighlightText: String? = null,
    private val colorAccent: String? = null,
    private val colorTitle: String? = null
) {
    val controller = IGMController(bridge, gameTitle)
    private var composeView: ComposeView? = null
    private var dialog: Dialog? = null
    private val lifecycleOwner = IGMLifecycleOwner()
    private var showTimeMs = 0L

    fun onCreate(savedInstanceState: Bundle?) {
        initFonts(activity.assets)
        lifecycleOwner.performCreate(savedInstanceState)

        // Wire up menu button callback from C input handler
        bridge.onMenuButtonPressed = { show() }

        // Forward gamepad input to IGM controller when visible
        bridge.onDebugKey = { keycode ->
            controller.handleKeyDown(keycode)
        }

        // Wire up controller callbacks
        controller.onClose = { hide() }
        controller.onOpenNativeMenu = {
            hide()
            bridge.openNativeMenu()
        }

        val view = ComposeView(activity).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                IGMContent()
            }
        }
        composeView = view

        // Use a full-screen Dialog to render on top of NativeActivity's GL surface
        dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen).apply {
            setContentView(view)
            window?.apply {
                setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            }
            setCancelable(false)
            // Handle gamepad input through the Dialog when IGM is visible,
            // since NativeActivity's AInputQueue loses focus when a Dialog is showing
            setOnKeyListener { _, keyCode, event ->
                if (event.action == KeyEvent.ACTION_DOWN) {
                    // Debounce BACK/menu keys for 500ms after opening to prevent
                    // the same button press that opened the menu from closing it
                    val isMenuKey = keyCode == KeyEvent.KEYCODE_BACK
                            || keyCode == KeyEvent.KEYCODE_BUTTON_MODE
                            || keyCode == KeyEvent.KEYCODE_MENU
                    if (isMenuKey && System.currentTimeMillis() - showTimeMs < 500) {
                        // Ignore — this is the same press that opened the menu
                    } else {
                        controller.handleKeyDown(keyCode)
                    }
                }
                true // consume all events to prevent Dialog dismissal
            }
        }
    }

    fun onResume() {
        lifecycleOwner.performStart()
        lifecycleOwner.performResume()
    }

    fun onPause() {
        lifecycleOwner.performPause()
        lifecycleOwner.performStop()
    }

    fun onDestroy() {
        dialog?.dismiss()
        dialog = null
        lifecycleOwner.performDestroy()
        composeView = null
    }

    private var showing = false

    fun show() {
        if (showing) return
        showing = true
        showTimeMs = System.currentTimeMillis()
        controller.openMenu()
        bridge.setIGMVisible(true)
        dialog?.show()
    }

    fun hide() {
        if (!showing) return
        showing = false
        bridge.setIGMVisible(false)
        dialog?.dismiss()
        controller.closeMenu()
    }

    fun isVisible(): Boolean = showing

    @Composable
    private fun IGMContent() {
        val highlightColor = colorHighlight?.let { hexToColor(it) } ?: Color.White
        val colors = CannoliColors(
            highlight = highlightColor,
            text = colorText?.let { hexToColor(it) } ?: Color.White,
            highlightText = colorHighlightText?.let { hexToColor(it) } ?: Color.Black,
            accent = colorAccent?.let { hexToColor(it) } ?: Color.White,
            title = colorTitle?.let { hexToColor(it) } ?: Color.White
        )
        CannoliTheme {
        CompositionLocalProvider(LocalCannoliColors provides colors) {
            val screen = controller.currentScreen
            if (screen != null) {
                when (screen) {
                    is IGMScreen.Menu -> InGameMenu(
                        gameTitle = controller.gameTitle,
                        menuOptions = controller.buildMenuOptions(),
                        selectedIndex = screen.selectedIndex,
                        selectedSlot = controller.currentSlot,
                        slotThumbnail = controller.slotThumbnail.value,
                        slotExists = controller.slotExists.value,
                        slotOccupied = controller.slotOccupied.value,
                        undoLabel = controller.undoLabel.value
                    )
                    else -> {
                        // Other IGM screens will be implemented later
                    }
                }
            }
        }
        }
    }
}
