package com.maria.homelauncher

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.maria.homelauncher.ui.LauncherRoot
import com.maria.homelauncher.ui.LauncherTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repo: AppRepo
    private lateinit var prefs: Prefs
    private lateinit var styleStore: StyleStore
    private lateinit var widgets: LauncherWidgets

    private var drawerOpen by mutableStateOf(false)
    private var style by mutableStateOf(Style())

    /** Bumped on a home press so the composables can close every panel they own. */
    private var resetPanels by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Fonts.init(this)
        repo = AppRepo(this)
        prefs = Prefs(this)
        styleStore = StyleStore(this)
        widgets = LauncherWidgets(this)
        style = styleStore.load()

        repo.register()
        lifecycleScope.launch {
            repo.reload(
                style.iconShape, style.iconTint,
                style.iconColor.toInt(), style.iconBg.toInt(),
                style.iconPack
            )
        }

        Wallpaper.schedule(this)
        Updater.schedule(this)

        setContent {
            LauncherTheme(style) {
                // Settings sits on top of the drawer, so drawerOpen covers both panels.
                LaunchedEffect(drawerOpen, style.bgMode, style.blurRadius) {
                    applyBlur(drawerOpen && style.bgMode == BgMode.Blur, style.blurRadius)
                }

                LauncherRoot(
                    repo = repo,
                    prefs = prefs,
                    style = style,
                    onStyle = ::updateStyle,
                    onResetStyle = {
                        styleStore.reset()
                        updateStyle(styleStore.load())
                    },
                    drawerOpen = drawerOpen,
                    setDrawerOpen = { drawerOpen = it },
                    resetPanels = resetPanels,
                    widgets = widgets,
                    styleStore = styleStore,
                    // a restore rewrites every store underneath the running screen, and
                    // half of that is already held in composable state. Starting the
                    // activity again is the one way to be sure nothing stale survives.
                    onImported = { recreate() }
                )
            }
        }
    }

    private fun updateStyle(next: Style) {
        // Icons are baked at render time, so any of these means re-rendering them all.
        val rerender = next.iconShape != style.iconShape ||
            next.iconTint != style.iconTint ||
            next.iconColor != style.iconColor ||
            next.iconBg != style.iconBg ||
            next.iconPack != style.iconPack

        style = next
        styleStore.save(next)

        if (rerender) lifecycleScope.launch {
            repo.reload(
                next.iconShape, next.iconTint,
                next.iconColor.toInt(), next.iconBg.toInt(),
                next.iconPack
            )
        }
    }

    private fun applyBlur(on: Boolean, radius: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        setBlurBehind(on, radius)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun setBlurBehind(on: Boolean, radius: Int) {
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
        window.attributes = window.attributes.also {
            it.blurBehindRadius = if (on) radius else 0
        }
    }

    /** False when the device or the current power/accessibility state disables blur. */
    fun blurSupported(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && windowManager.isCrossWindowBlurEnabled

    override fun onStart() {
        super.onStart()
        widgets.start()
        // the rotation worker can write an accent while we were away
        val fresh = styleStore.load()
        if (fresh != style) updateStyle(fresh)
    }

    override fun onStop() {
        widgets.stop()
        super.onStop()
    }

    @Deprecated("startActivityForResult is how widget bind and configure still report back")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_WIDGET_BIND || requestCode == REQ_WIDGET_CONFIGURE) {
            widgets.onResult(requestCode, resultCode)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        drawerOpen = false
        resetPanels++
    }

    override fun onDestroy() {
        repo.unregister()
        super.onDestroy()
    }
}
