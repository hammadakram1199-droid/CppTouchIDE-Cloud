package com.example.cpptouchide

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.mlkit.vision.digitalink.Ink

class HandwritingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.WHITE
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val linePaint = Paint().apply {
        color = 0xFF333333.toInt()
        strokeWidth = 2f
    }

    private val path = Path()
    private var lastX = 0f
    private var lastY = 0f
    
    // DOUBLED the line height from 60f to 120f
    private val lineHeight = 120f 

    private var inkBuilder = Ink.builder()
    private var strokeBuilder: Ink.Stroke.Builder? = null

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var y = lineHeight
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
            y += lineHeight
        }
        canvas.drawPath(path, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val t = System.currentTimeMillis()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(x, y); lastX = x; lastY = y
                strokeBuilder = Ink.Stroke.builder()
                strokeBuilder?.addPoint(Ink.Point.create(x, y, t))
            }
            MotionEvent.ACTION_MOVE -> {
                path.quadTo(lastX, lastY, (x + lastX) / 2, (y + lastY) / 2)
                lastX = x; lastY = y
                strokeBuilder?.addPoint(Ink.Point.create(x, y, t))
            }
            MotionEvent.ACTION_UP -> {
                path.lineTo(lastX, lastY)
                strokeBuilder?.addPoint(Ink.Point.create(x, y, t))
                strokeBuilder?.let { inkBuilder.addStroke(it.build()) }
                strokeBuilder = null
            }
        }
        invalidate()
        return true
    }

    fun clear() {
        path.reset()
        inkBuilder = Ink.builder()
        strokeBuilder = null
        invalidate()
    }

    fun getInk(): Ink = inkBuilder.build()
}
