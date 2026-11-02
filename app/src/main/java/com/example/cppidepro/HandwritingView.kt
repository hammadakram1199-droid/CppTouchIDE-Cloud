package com.example.cppidepro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.google.mlkit.vision.digitalink.Ink
import kotlin.math.ceil

class HandwritingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private class InkPoint(val x: Float, val y: Float, val t: Long)
    private class StrokeData { val path = Path(); val points = ArrayList<InkPoint>() }
    
    var fingerDrawingEnabled: Boolean = true
    private val dp = resources.displayMetrics.density
    private val lineSpacing = 50f * dp // Slightly taller for easier writing
    private val gutterWidth = 60f * dp // Wider gutter for line numbers
    
    private val strokes = ArrayList<StrokeData>()
    private var current: StrokeData? = null
    private var lastX = 0f
    private var lastY = 0f

    private val linePaint = Paint().apply { color = Color.parseColor("#3C3C3C"); strokeWidth = 1f * dp; style = Paint.Style.STROKE }
    private val dividerPaint = Paint().apply { color = Color.parseColor("#6A6A6A"); strokeWidth = 1.5f * dp }
    private val gutterPaint = Paint().apply { color = Color.parseColor("#252526") }
    
    // Made the line numbers larger and white for high visibility
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF") 
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, resources.displayMetrics)
    }
    
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F2F2F2"); style = Paint.Style.STROKE
        strokeWidth = 3f * dp; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#1E1E1E"))
        canvas.drawRect(0f, 0f, gutterWidth, height.toFloat(), gutterPaint)
        
        val lineCount = ceil(height / lineSpacing).toInt()
        for (i in 1..lineCount) {
            val y = i * lineSpacing
            // Draw the horizontal line
            canvas.drawLine(gutterWidth, y, width.toFloat(), y, linePaint)
            
            // Draw the line number (centered vertically between the lines)
            val centerY = y - (lineSpacing / 2f) + (numberPaint.textSize / 3f)
            canvas.drawText(i.toString(), gutterWidth / 2f, centerY, numberPaint)
        }
        canvas.drawLine(gutterWidth, 0f, gutterWidth, height.toFloat(), dividerPaint)
        
        for (stroke in strokes) canvas.drawPath(stroke.path, inkPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
                if (!fingerDrawingEnabled && !isStylus) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                startStroke(event.x, event.y, event.eventTime)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val stroke = current ?: return false
                parent?.requestDisallowInterceptTouchEvent(true)
                for (i in 0 until event.historySize) extendStroke(stroke, event.getHistoricalX(i), event.getHistoricalY(i), event.getHistoricalEventTime(i))
                extendStroke(stroke, event.x, event.y, event.eventTime)
                invalidate(); return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val stroke = current ?: return false
                if (event.actionMasked == MotionEvent.ACTION_UP) extendStroke(stroke, event.x, event.y, event.eventTime)
                stroke.path.lineTo(lastX, lastY)
                if (stroke.points.size == 1) stroke.path.lineTo(lastX + 0.1f, lastY)
                current = null
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return current != null
    }

    private fun startStroke(x: Float, y: Float, time: Long) {
        val stroke = StrokeData()
        stroke.path.moveTo(x, y)
        stroke.points.add(InkPoint(x, y, time))
        strokes.add(stroke); current = stroke; lastX = x; lastY = y; invalidate()
    }

    private fun extendStroke(stroke: StrokeData, x: Float, y: Float, time: Long) {
        stroke.path.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f)
        stroke.points.add(InkPoint(x, y, time)); lastX = x; lastY = y
    }

    fun hasInk(): Boolean = strokes.isNotEmpty()
    fun clear() { strokes.clear(); current = null; invalidate() }

    fun buildInkPerLine(): List<Ink> {
        val byLine = sortedMapOf<Int, MutableList<StrokeData>>()
        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            val avgY = stroke.points.map { it.y }.average().toFloat()
            val lineIndex = (avgY / lineSpacing).toInt()
            byLine.getOrPut(lineIndex) { mutableListOf() }.add(stroke)
        }
        return byLine.values.map { lineStrokes ->
            val inkBuilder = Ink.builder()
            for (s in lineStrokes) {
                val strokeBuilder = Ink.Stroke.builder()
                for (p in s.points) strokeBuilder.addPoint(Ink.Point.create(p.x, p.y, p.t))
                inkBuilder.addStroke(strokeBuilder.build())
            }
            inkBuilder.build()
        }
    }
}
