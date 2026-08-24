package com.charles.livecaptionn.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.charles.livecaptionn.data.SettingsRepository
import com.charles.livecaptionn.settings.CaptionSettings
import com.charles.livecaptionn.speech.RecognitionStatus
import com.charles.livecaptionn.ui.l10n.UiStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class OverlayController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val onPauseResume: () -> Unit,
    private val onClose: () -> Unit,
    private val onToggleMinimize: () -> Unit,
    private val uiStrings: () -> UiStrings = { UiStrings.EMPTY }
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density

    private var root: FrameLayout? = null
    private var statusText: TextView? = null
    private var originalText: TextView? = null
    private var translatedText: TextView? = null
    private var transcriptText: TextView? = null
    private var body: ScrollView? = null
    private var pauseButton: ImageButton? = null
    private var minButton: ImageButton? = null
    private var closeButton: ImageButton? = null
    private var params: WindowManager.LayoutParams? = null

    fun show(initialX: Int, initialY: Int, widthDp: Int, heightDp: Int) {
        if (root != null) return
        showInternal(initialX, initialY, widthDp, heightDp)
    }

    private fun showInternal(initialX: Int, initialY: Int, widthDp: Int, heightDp: Int) {
        val widthPx = (widthDp * density).roundToInt()
        val heightPx = (heightDp * density).roundToInt()

        val p = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = initialX
            y = initialY
        }
        params = p

        // Root is a FrameLayout so we can place resize handle on top
        val frame = FrameLayout(context)

        // Inner container (vertical layout)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                cornerRadius = 28f
                setColor(Color.parseColor("#AA111111"))
            }
            background = bg
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Header: status + buttons (also the drag-to-move zone)
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setOnTouchListener(DragTouchListener())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        statusText = TextView(context).apply {
            text = "Status: Idle"
            setTextColor(Color.WHITE)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        pauseButton = makeButton(android.R.drawable.ic_media_pause, uiStrings()["Pause captioning"]) { onPauseResume() }
        minButton = makeButton(android.R.drawable.arrow_down_float, uiStrings()["Minimize overlay"]) { onToggleMinimize() }
        closeButton = makeButton(android.R.drawable.ic_menu_close_clear_cancel, uiStrings()["Close overlay"]) { onClose() }

        header.addView(statusText)
        header.addView(pauseButton)
        header.addView(minButton)
        header.addView(closeButton)

        // Original text (shown when showOriginal is enabled, italic + smaller)
        originalText = TextView(context).apply {
            setTextColor(Color.WHITE)
            text = ""
            setLineSpacing(dp(2).toFloat(), 1.0f)
            setPadding(0, dp(4), 0, dp(2))
            textSize = 14f
            // visibility managed by update()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        // Translated text / scrolling history (the primary caption body)
        translatedText = TextView(context).apply {
            setTextColor(Color.WHITE)
            text = "…"
            setLineSpacing(dp(3).toFloat(), 1.0f)
            setPadding(0, dp(2), 0, dp(4))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        transcriptText = translatedText // alias for update() compatibility

        // Container that holds both caption TextViews, inside the scroll body
        val captionContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        captionContainer.addView(originalText)
        captionContainer.addView(translatedText)

        body = ScrollView(context).apply {
            addView(captionContainer)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            isFillViewport = false
            isVerticalScrollBarEnabled = true
        }

        container.addView(header)
        container.addView(body)

        frame.addView(container)

        // Resize handle at bottom-right corner
        val handleSize = dp(22)
        val resizeHandle = View(context).apply {
            val bg = GradientDrawable().apply {
                cornerRadius = dp(4).toFloat()
                setColor(Color.parseColor("#88FFFFFF"))
            }
            background = bg
            layoutParams = FrameLayout.LayoutParams(handleSize, handleSize).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, dp(4), dp(4))
            }
            setOnTouchListener(ResizeTouchListener())
        }
        frame.addView(resizeHandle)

        root = frame
        try {
            wm.addView(frame, p)
        } catch (t: Throwable) {
            root = null
            params = null
            throw t
        }
    }

    fun update(ui: OverlayUiState) {
        val frame = root ?: return
        val container = frame.getChildAt(0) as? LinearLayout ?: return
        val theme = OverlayThemeCatalog.find(ui.themeId)
        val font = OverlayFontCatalog.find(ui.fontId)
        val s = uiStrings()
        // Enforce minimum 0.5 opacity for WCAG AA contrast (text on dark bg)
        val effectiveOpacity = ui.opacity.coerceIn(0.5f, 1.0f)
        (container.background as? GradientDrawable)?.setColor(
            Color.argb((effectiveOpacity * 255).roundToInt(), Color.red(theme.backgroundRgb), Color.green(theme.backgroundRgb), Color.blue(theme.backgroundRgb))
        )
        statusText?.setTextColor(theme.textRgb)
        statusText?.typeface = font.typeface
        statusText?.text = buildString {
            append(s["Status"])
            append(": ")
            append(s[ui.status.displayName])
            val detail = ui.statusDetail?.trim().orEmpty()
            if (detail.isNotEmpty()) {
                append("\n")
                append(detail)
            }
        }
        pauseButton?.contentDescription = if (ui.status == RecognitionStatus.PAUSED) s["Resume captioning"] else s["Pause captioning"]
        minButton?.contentDescription = s["Minimize overlay"]
        closeButton?.contentDescription = s["Close overlay"]
        originalText?.setTextColor(theme.textRgb)
        originalText?.typeface = font.typeface
        originalText?.text = ui.originalText.ifBlank { "…" }
        originalText?.visibility = if (ui.showOriginal && ui.originalText.isNotBlank()) View.VISIBLE else View.GONE
        translatedText?.setTextColor(theme.textRgb)
        translatedText?.typeface = font.typeface
        translatedText?.text = ui.transcriptText.ifBlank { "…" }
        translatedText?.textSize = ui.textSizeSp
        translatedText?.visibility = View.VISIBLE
        body?.visibility = if (ui.minimized) View.GONE else View.VISIBLE
        pauseButton?.setImageResource(
            if (ui.status == RecognitionStatus.PAUSED) android.R.drawable.ic_media_play
            else android.R.drawable.ic_media_pause
        )
        // Auto-scroll to bottom on new text
        body?.post {
            try { body?.fullScroll(View.FOCUS_DOWN) } catch (_: Throwable) { }
        }
    }

    fun hide() {
        root?.let { view ->
            try { wm.removeViewImmediate(view) } catch (_: Throwable) { }
        }
        root = null
        // Must be cleared together with root: DragTouchListener/ResizeTouchListener
        // guard on `params` being non-null before calling wm.updateViewLayout(root, ...),
        // and a touch event queued just before hide() runs would otherwise pass that
        // guard with a stale non-null params while root is already null, crashing
        // with "view must not be null" inside WindowManager.
        params = null
    }

    // ── Helpers ──

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private fun makeButton(resId: Int, contentDesc: String, onClick: () -> Unit) = ImageButton(context).apply {
        setImageResource(resId)
        contentDescription = contentDesc
        setBackgroundColor(Color.TRANSPARENT)
        setColorFilter(Color.WHITE)
        val size = dp(32)
        layoutParams = LinearLayout.LayoutParams(size, size).apply {
            setMargins(dp(2), 0, dp(2), 0)
        }
        setOnClickListener { onClick() }
    }

    // ── Drag-to-move (touch on header) ──

    private inner class DragTouchListener : View.OnTouchListener {
        private var startX = 0; private var startY = 0
        private var touchX = 0f; private var touchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val lp = params ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x; startY = lp.y
                    touchX = event.rawX; touchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val view = root ?: return false
                    lp.x = startX + (event.rawX - touchX).roundToInt()
                    lp.y = startY + (event.rawY - touchY).roundToInt()
                    try { wm.updateViewLayout(view, lp) } catch (_: IllegalArgumentException) { return false }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    scope.launch {
                        settingsRepository.update { it.copy(overlayX = lp.x, overlayY = lp.y) }
                    }
                    return true
                }
            }
            return false
        }
    }

    // ── Resize (touch on bottom-right handle) ──

    private inner class ResizeTouchListener : View.OnTouchListener {
        private var startW = 0; private var startH = 0
        private var touchX = 0f; private var touchY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val lp = params ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startW = lp.width; startH = lp.height
                    touchX = event.rawX; touchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val view = root ?: return false
                    val minW = (CaptionSettings.MIN_OVERLAY_WIDTH_DP * density).roundToInt()
                    val minH = (CaptionSettings.MIN_OVERLAY_HEIGHT_DP * density).roundToInt()
                    lp.width = (startW + (event.rawX - touchX).roundToInt()).coerceAtLeast(minW)
                    lp.height = (startH + (event.rawY - touchY).roundToInt()).coerceAtLeast(minH)
                    try { wm.updateViewLayout(view, lp) } catch (_: IllegalArgumentException) { return false }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val wDp = (lp.width / density).roundToInt()
                    val hDp = (lp.height / density).roundToInt()
                    scope.launch {
                        settingsRepository.update {
                            it.copy(overlayWidthDp = wDp, overlayHeightDp = hDp)
                        }
                    }
                    return true
                }
            }
            return false
        }
    }
}
