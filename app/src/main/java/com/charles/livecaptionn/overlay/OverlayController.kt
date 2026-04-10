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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class OverlayController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val onPauseResume: () -> Unit,
    private val onClose: () -> Unit,
    private val onToggleMinimize: () -> Unit
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

        pauseButton = makeButton(android.R.drawable.ic_media_pause) { onPauseResume() }
        val minButton = makeButton(android.R.drawable.arrow_down_float) { onToggleMinimize() }
        val closeButton = makeButton(android.R.drawable.ic_menu_close_clear_cancel) { onClose() }

        header.addView(statusText)
        header.addView(pauseButton)
        header.addView(minButton)
        header.addView(closeButton)

        // Scrollable body for transcript text. Use top-gravity + WRAP_CONTENT so the single
        // transcript TextView anchors predictably; fillViewport+BOTTOM was causing the body
        // to render as empty in some window sizes.
        transcriptText = TextView(context).apply {
            setTextColor(Color.WHITE)
            text = ""
            setLineSpacing(dp(3).toFloat(), 1.0f)
            setPadding(0, dp(4), 0, dp(4))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        // Kept for compatibility with OverlayUiState fields; these are hidden but still
        // referenced by update() so we instantiate empty stubs.
        originalText = TextView(context)
        translatedText = TextView(context)

        body = ScrollView(context).apply {
            addView(transcriptText)
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
        wm.addView(frame, p)
    }

    fun update(ui: OverlayUiState) {
        val frame = root ?: return
        val container = frame.getChildAt(0) as? LinearLayout ?: return
        (container.background as? GradientDrawable)?.setColor(
            Color.argb((ui.opacity * 255).roundToInt(), 17, 17, 17)
        )
        statusText?.text = buildString {
            append("Status: ${ui.status.name.lowercase().replaceFirstChar { it.uppercase() }}")
            val detail = ui.statusDetail?.trim().orEmpty()
            if (detail.isNotEmpty()) {
                append("\n")
                append(detail)
            }
        }
        transcriptText?.text = ui.transcriptText.ifBlank { "…" }
        transcriptText?.textSize = ui.textSizeSp
        transcriptText?.visibility = View.VISIBLE
        body?.visibility = if (ui.minimized) View.GONE else View.VISIBLE
        pauseButton?.setImageResource(
            if (ui.status.name == "PAUSED") android.R.drawable.ic_media_play
            else android.R.drawable.ic_media_pause
        )
        // Auto-scroll to bottom on new text
        body?.post { body?.fullScroll(View.FOCUS_DOWN) }
    }

    fun hide() {
        root?.let { wm.removeView(it) }
        root = null
    }

    // ── Helpers ──

    private fun dp(value: Int): Int = (value * density).roundToInt()

    private fun makeButton(resId: Int, onClick: () -> Unit) = ImageButton(context).apply {
        setImageResource(resId)
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
                    lp.x = startX + (event.rawX - touchX).roundToInt()
                    lp.y = startY + (event.rawY - touchY).roundToInt()
                    wm.updateViewLayout(root, lp)
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
                    val minW = (CaptionSettings.MIN_OVERLAY_WIDTH_DP * density).roundToInt()
                    val minH = (CaptionSettings.MIN_OVERLAY_HEIGHT_DP * density).roundToInt()
                    lp.width = (startW + (event.rawX - touchX).roundToInt()).coerceAtLeast(minW)
                    lp.height = (startH + (event.rawY - touchY).roundToInt()).coerceAtLeast(minH)
                    wm.updateViewLayout(root, lp)
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
