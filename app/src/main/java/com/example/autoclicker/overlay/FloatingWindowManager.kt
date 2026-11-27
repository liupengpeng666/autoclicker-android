package com.example.autoclicker.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.example.autoclicker.R
import com.example.autoclicker.core.AppContainer
import com.example.autoclicker.databinding.OverlayControlBinding
import com.example.autoclicker.databinding.OverlayMarkerBinding
import com.example.autoclicker.model.ClickPoint

/** 悬浮窗：控制面板 + 可拖动的十字标记，支持在标记处取点 */
class FloatingWindowManager(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var controlView: View? = null
    private var markerView: View? = null
    private var binding: OverlayControlBinding? = null
    private var markerBinding: OverlayMarkerBinding? = null

    var isPicking = false
    var onPointPicked: ((Int, Int) -> Unit)? = null
    var onStart: (() -> Unit)? = null
    var onStop: (() -> Unit)? = null
    var onClose: (() -> Unit)? = null

    fun canDraw(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    fun show() {
        if (!canDraw()) {
            Toast.makeText(context, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            return
        }
        if (controlView != null) return

        val infl = LayoutInflater.from(context)
        binding = OverlayControlBinding.inflate(infl, null, false).also { b ->
            controlView = b.root
            b.btnStartStop.setOnClickListener {
                if (AppContainer.scheduler.running.value) onStop?.invoke() else onStart?.invoke()
            }
            b.btnPick.setOnClickListener {
                isPicking = !isPicking
                b.btnPick.isSelected = isPicking
                b.btnPick.text = context.getString(
                    if (isPicking) R.string.picking_on else R.string.pick_point
                )
                Toast.makeText(
                    context,
                    if (isPicking) "取点模式：拖动十字标记到目标位置后点击它" else "已退出取点模式",
                    Toast.LENGTH_SHORT
                ).show()
            }
            b.btnClose.setOnClickListener { hide() }
            attachDraggable(b.root)
            wm.addView(b.root, controlParams())
        }

        markerBinding = OverlayMarkerBinding.inflate(infl, null, false).also { mb ->
            markerView = mb.root
            mb.root.setOnTouchListener { v, event ->
                val params = v.layoutParams as WindowManager.LayoutParams
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        v.tag = intArrayOf(params.x, params.y, event.rawX.toInt(), event.rawY.toInt())
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        (v.tag as? IntArray)?.let { start ->
                            params.x = start[0] + (event.rawX.toInt() - start[2])
                            params.y = start[1] + (event.rawY.toInt() - start[3])
                            wm.updateViewLayout(v, params)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        (v.tag as? IntArray)?.let { start ->
                            val moved = kotlin.math.abs(event.rawX.toInt() - start[2]) +
                                    kotlin.math.abs(event.rawY.toInt() - start[3])
                            if (moved < 8 && isPicking) {
                                // 取标记中心对应的屏幕坐标
                                val loc = IntArray(2)
                                v.getLocationOnScreen(loc)
                                val cx = loc[0] + v.width / 2
                                val cy = loc[1] + v.height / 2
                                onPointPicked?.invoke(cx, cy)
                                isPicking = false
                                binding?.btnPick?.isSelected = false
                                binding?.btnPick?.text = context.getString(R.string.pick_point)
                            }
                        }
                        true
                    }
                    else -> false
                }
            }
            wm.addView(mb.root, markerParams())
        }
    }

    /** 把标记移动到指定点位（编辑已有坐标时用） */
    fun moveMarkerTo(point: ClickPoint) {
        val mb = markerBinding ?: return
        val p = mb.root.layoutParams as WindowManager.LayoutParams
        p.x = point.x - dp(20)
        p.y = point.y - dp(20)
        wm.updateViewLayout(mb.root, p)
    }

    fun updateState(running: Boolean, progress: String) {
        binding?.btnStartStop?.text = context.getString(
            if (running) R.string.stop else R.string.start
        )
        binding?.tvStatus?.text = progress
    }

    fun hide() {
        controlView?.let { runCatching { wm.removeView(it) } }
        markerView?.let { runCatching { wm.removeView(it) } }
        controlView = null
        markerView = null
        binding = null
        markerBinding = null
        isPicking = false
        onClose?.invoke()
    }

    private fun attachDraggable(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        view.setOnTouchListener { v, event ->
            val params = v.layoutParams as WindowManager.LayoutParams
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    wm.updateViewLayout(v, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE

    private fun controlParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40; y = 200
        }

    private fun markerParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            dp(40), dp(40), overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 200; y = 500
        }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
