package com.example.fileexplorer

import android.accessibilityservice.AccessibilityService
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.DecelerateInterpolator
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.hypot

/**
 * iOS-style AssistiveTouch: a draggable floating button drawn over every app.
 * Tapping it opens a panel of shortcuts (Home, Back, Recents, Notifications, ...).
 *
 * It is an accessibility service because that is the only way an app can both draw
 * over other apps without extra permissions and press Home / Back / Recents for you.
 */
class AssistiveTouchService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var button: View? = null
    private var buttonParams: WindowManager.LayoutParams? = null
    private var menuView: ComposeView? = null
    private var menuOwner: OverlayLifecycleOwner? = null

    private val fade = Runnable { button?.animate()?.alpha(IDLE_ALPHA)?.setDuration(300)?.start() }

    // ------------------------------------------------------------ lifecycle

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        showButton()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        closeMenu()
        snapToEdge(animate = false) // rotation: keep the button on screen
    }

    override fun onUnbind(intent: Intent?): Boolean {
        removeOverlays()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        removeOverlays()
        instance = null
        super.onDestroy()
    }

    private fun removeOverlays() {
        handler.removeCallbacksAndMessages(null)
        closeMenu()
        button?.let { runCatching { wm.removeView(it) } }
        button = null
    }

    // ------------------------------------------------------------ the floating button

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(m)
            m.widthPixels to m.heightPixels
        }

    private fun overlayParams(width: Int, height: Int) = WindowManager.LayoutParams(
        width, height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showButton() {
        if (button != null) return
        val size = dp(BUTTON_DP)
        val (w, h) = screenSize()
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val params = overlayParams(size, size).apply {
            x = prefs.getInt(KEY_X, w - size - dp(4f))
            y = prefs.getInt(KEY_Y, h / 3)
        }
        val view = AssistiveButtonView(this).apply {
            contentDescription = "Assistive Touch"
            alpha = IDLE_ALPHA
        }
        val slop = ViewConfiguration.get(this).scaledTouchSlop

        view.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f
            var downY = 0f
            var startX = 0
            var startY = 0
            var dragging = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX; downY = e.rawY
                        startX = params.x; startY = params.y
                        dragging = false
                        handler.removeCallbacks(fade)
                        v.animate().cancel()
                        v.alpha = 1f
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = e.rawX - downX
                        val dy = e.rawY - downY
                        if (!dragging && hypot(dx, dy) > slop) dragging = true
                        if (dragging) {
                            params.x = startX + dx.toInt()
                            params.y = startY + dy.toInt()
                            safeUpdate(v, params)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (dragging) snapToEdge(animate = true) else {
                            v.performClick()
                            openMenu()
                        }
                        scheduleFade()
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        if (dragging) snapToEdge(animate = true)
                        scheduleFade()
                    }
                }
                return true
            }
        })

        wm.addView(view, params)
        button = view
        buttonParams = params
        snapToEdge(animate = false)
    }

    private fun scheduleFade() {
        handler.removeCallbacks(fade)
        handler.postDelayed(fade, FADE_DELAY_MS)
    }

    private fun safeUpdate(v: View, p: WindowManager.LayoutParams) {
        if (v.isAttachedToWindow) runCatching { wm.updateViewLayout(v, p) }
    }

    /** Like iOS, the button always comes to rest against the left or right edge. */
    private fun snapToEdge(animate: Boolean) {
        val v = button ?: return
        val p = buttonParams ?: return
        val (w, h) = screenSize()
        val margin = dp(4f)
        val targetX = if (p.x + p.width / 2 < w / 2) margin else w - p.width - margin
        p.y = p.y.coerceIn(margin, (h - p.height - margin).coerceAtLeast(margin))

        if (!animate) {
            p.x = targetX
            safeUpdate(v, p)
            savePosition()
            return
        }
        ValueAnimator.ofInt(p.x, targetX).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                p.x = it.animatedValue as Int
                safeUpdate(v, p)
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = savePosition()
            })
            start()
        }
    }

    private fun savePosition() {
        val p = buttonParams ?: return
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_X, p.x).putInt(KEY_Y, p.y).apply()
    }

    // ------------------------------------------------------------ the menu panel

    private fun openMenu() {
        if (menuView != null) return
        button?.visibility = View.INVISIBLE

        val owner = OverlayLifecycleOwner().also { it.start() }
        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { AssistiveMenu(actions = menuActions(), onDismiss = ::closeMenu) }
        }
        wm.addView(
            view,
            overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT),
        )
        menuView = view
        menuOwner = owner
    }

    private fun closeMenu() {
        menuView?.let { runCatching { wm.removeView(it) } }
        menuOwner?.destroy()
        menuView = null
        menuOwner = null
        button?.visibility = View.VISIBLE
        scheduleFade()
    }

    /** Close the panel first so it isn't in the way (or in the screenshot), then act. */
    private fun afterClose(action: () -> Unit): () -> Unit = {
        closeMenu()
        handler.postDelayed({ action() }, 250)
    }

    private fun global(action: Int): () -> Unit = afterClose { performGlobalAction(action) }

    private fun volume(direction: Int): () -> Unit = afterClose {
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        audio.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
    }

    private fun menuActions(): List<AssistiveAction> = buildList {
        add(AssistiveAction("Notifications", AssistiveIcon.NOTIFICATIONS, global(GLOBAL_ACTION_NOTIFICATIONS)))
        add(AssistiveAction("Control Center", AssistiveIcon.CONTROL_CENTER, global(GLOBAL_ACTION_QUICK_SETTINGS)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add(AssistiveAction("Screenshot", AssistiveIcon.SCREENSHOT, global(GLOBAL_ACTION_TAKE_SCREENSHOT)))
        } else {
            add(AssistiveAction("Power", AssistiveIcon.POWER, global(GLOBAL_ACTION_POWER_DIALOG)))
        }
        add(AssistiveAction("Back", AssistiveIcon.BACK, global(GLOBAL_ACTION_BACK)))
        add(AssistiveAction("Home", AssistiveIcon.HOME, global(GLOBAL_ACTION_HOME)))
        add(AssistiveAction("Recents", AssistiveIcon.RECENTS, global(GLOBAL_ACTION_RECENTS)))
        add(AssistiveAction("Volume Down", AssistiveIcon.VOLUME_DOWN, volume(AudioManager.ADJUST_LOWER)))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            add(AssistiveAction("Lock Screen", AssistiveIcon.LOCK, global(GLOBAL_ACTION_LOCK_SCREEN)))
        } else {
            add(AssistiveAction("Power", AssistiveIcon.POWER, global(GLOBAL_ACTION_POWER_DIALOG)))
        }
        add(AssistiveAction("Volume Up", AssistiveIcon.VOLUME_UP, volume(AudioManager.ADJUST_RAISE)))
    }

    companion object {
        private const val PREFS = "assistive_touch"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"
        private const val BUTTON_DP = 58f
        private const val IDLE_ALPHA = 0.45f
        private const val FADE_DELAY_MS = 3000L

        /** Set while the service is running, so the app can turn it off. */
        var instance: AssistiveTouchService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, AssistiveTouchService::class.java).flattenToString()
            val enabled = Settings.Secure.getString(
                context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ).orEmpty()
            return enabled.split(':').any { it.equals(me, ignoreCase = true) }
        }

        fun openSettings(context: Context) {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }

        fun turnOff() {
            instance?.disableSelf()
        }
    }
}

/** The iOS look: a dark translucent rounded square with three white rings. */
private class AssistiveButtonView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2
        val cy = h / 2
        val r = minOf(w, h) / 2

        rect.set(0f, 0f, w, h)
        paint.color = 0xCC1C1C1E.toInt()
        canvas.drawRoundRect(rect, r * 0.5f, r * 0.5f, paint)

        paint.color = 0x40FFFFFF
        canvas.drawCircle(cx, cy, r * 0.70f, paint)
        paint.color = 0x73FFFFFF
        canvas.drawCircle(cx, cy, r * 0.54f, paint)
        paint.color = 0xF2FFFFFF.toInt()
        canvas.drawCircle(cx, cy, r * 0.38f, paint)
    }
}

/** Minimal lifecycle so a ComposeView can live in a service-owned overlay window. */
private class OverlayLifecycleOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    fun start() {
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
