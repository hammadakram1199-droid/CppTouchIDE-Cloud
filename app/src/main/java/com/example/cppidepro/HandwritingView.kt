package com.example.cppidepro

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import com.google.mlkit.vision.digitalink.Ink
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

class HandwritingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    class InkPoint(val x: Float, val y: Float, val t: Long) : Parcelable {
        constructor(parcel: Parcel) : this(parcel.readFloat(), parcel.readFloat(), parcel.readLong())
        override fun writeToParcel(parcel: Parcel, flags: Int) { parcel.writeFloat(x); parcel.writeFloat(y); parcel.writeLong(t) }
        override fun describeContents(): Int = 0
        companion object CREATOR : Parcelable.Creator<InkPoint> {
            override fun createFromParcel(parcel: Parcel): InkPoint = InkPoint(parcel)
            override fun newArray(size: Int): Array<InkPoint?> = arrayOfNulls(size)
        }
    }

    class StrokeData() : Parcelable {
        val path = Path()
        val points = ArrayList<InkPoint>()
        constructor(parcel: Parcel) : this() {
            parcel.readTypedList(points, InkPoint.CREATOR)
            if (points.isNotEmpty()) {
                path.moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) { path.lineTo(points[i].x, points[i].y) }
            }
        }
        override fun writeToParcel(parcel: Parcel, flags: Int) { parcel.writeTypedList(points) }
        override fun describeContents(): Int = 0
        companion object CREATOR : Parcelable.Creator<StrokeData> {
            override fun createFromParcel(parcel: Parcel): StrokeData = StrokeData(parcel)
            override fun newArray(size: Int): Array<StrokeData?> = arrayOfNulls(size)
        }
    }
    
    var isScrollMode = false 
    var isEraseMode = false // NEW ERASER MODE

    private val dp = resources.displayMetrics.density
    val lineSpacing = 42f * dp 
    private val gutterWidth = 42f * dp 
    private val totalContentHeight = 20000f 
    
    private val strokes = ArrayList<StrokeData>()
    private val undoStack = ArrayList<StrokeData>()
    private var current: StrokeData? = null
    private var lastX = 0f
    private var lastY = 0f
    
    private var scrollOffsetY = 0f
    private var lastTouchY = 0f

    private val linePaint = Paint().apply { color = Color.parseColor("#444444"); strokeWidth = 1.5f * dp; style = Paint.Style.STROKE }
    private val gutterPaint = Paint().apply { color = Color.parseColor("#1A1A1A") }
    
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF88") 
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }
    
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF"); style = Paint.Style.STROKE
        strokeWidth = 3f * dp; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    private val scrollBarPaint = Paint().apply { color = Color.parseColor("#666666") }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#1E1E1E"))
        canvas.save()
        canvas.translate(0f, -scrollOffsetY)
        
        canvas.drawRect(0f, 0f, gutterWidth, totalContentHeight, gutterPaint)
        
        val startLine = max(1, (scrollOffsetY / lineSpacing).toInt())
        val endLine = minOf(((scrollOffsetY + height) / lineSpacing).toInt() + 1, (totalContentHeight / lineSpacing).toInt())
        
        for (i in startLine..endLine) {
            val y = i * lineSpacing
            canvas.drawLine(gutterWidth, y, width.toFloat(), y, linePaint)
            val centerY = y - (lineSpacing / 2f) + (numberPaint.textSize / 3f)
            canvas.drawText(i.toString(), gutterWidth / 2f, centerY, numberPaint)
        }
        
        for (stroke in strokes) canvas.drawPath(stroke.path, inkPaint)
        canvas.restore()

        val scrollBarWidth = 8f * dp
        val scrollBarX = width - scrollBarWidth
        val thumbHeight = (height / totalContentHeight) * height
        val maxScroll = totalContentHeight - height
        if (maxScroll > 0) {
            val thumbY = (scrollOffsetY / maxScroll) * (height - thumbHeight)
            canvas.drawRoundRect(scrollBarX, thumbY, width.toFloat(), thumbY + thumbHeight, 10f, 10f, scrollBarPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isScrollMode) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { lastTouchY = event.y; return true }
                MotionEvent.ACTION_MOVE -> {
                    updateScroll(scrollOffsetY + (lastTouchY - event.y))
                    lastTouchY = event.y
                    return true
                }
            }
            return false
        } else if (isEraseMode) {
            // ERASER LOGIC
            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                val touchX = event.x
                val touchY = event.y + scrollOffsetY
                val eraseRadius = 40f * dp
                val iterator = strokes.iterator()
                while (iterator.hasNext()) {
                    val stroke = iterator.next()
                    for (point in stroke.points) {
                        val dx = point.x - touchX
                        val dy = point.y - touchY
                        if (sqrt(dx * dx + dy * dy) < eraseRadius) {
                            iterator.remove()
                            invalidate()
                            break
                        }
                    }
                }
                return true
            }
            return false
        } else {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startStroke(event.x, event.y + scrollOffsetY, event.eventTime)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val stroke = current ?: return false
                    for (i in 0 until event.historySize) {
                        extendStroke(stroke, event.getHistoricalX(i), event.getHistoricalY(i) + scrollOffsetY, event.getHistoricalEventTime(i))
                    }
                    extendStroke(stroke, event.x, event.y + scrollOffsetY, event.eventTime)
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val stroke = current ?: return false
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        extendStroke(stroke, event.x, event.y + scrollOffsetY, event.eventTime)
                    }
                    current = null
                    invalidate()
                    return true
                }
            }
        }
        return current != null
    }

    fun scrollByLines(lines: Int) {
        updateScroll(scrollOffsetY + (lines * lineSpacing))
    }

    private fun updateScroll(newY: Float) {
        val maxScroll = max(0f, totalContentHeight - height)
        scrollOffsetY = newY.coerceIn(0f, maxScroll)
        invalidate()
    }

    private fun startStroke(x: Float, y: Float, time: Long) {
        val stroke = StrokeData()
        stroke.path.moveTo(x, y)
        stroke.points.add(InkPoint(x, y, time))
        strokes.add(stroke)
        undoStack.clear() 
        current = stroke
        lastX = x
        lastY = y
        invalidate()
    }

    private fun extendStroke(stroke: StrokeData, x: Float, y: Float, time: Long) {
        stroke.path.quadTo(lastX, lastY, (x + lastX) / 2f, (y + lastY) / 2f)
        stroke.points.add(InkPoint(x, y, time))
        lastX = x
        lastY = y
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            val last = strokes.removeAt(strokes.size - 1)
            undoStack.add(last)
            invalidate()
        }
    }

    fun redo() {
        if (undoStack.isNotEmpty()) {
            val last = undoStack.removeAt(undoStack.size - 1)
            strokes.add(last)
            invalidate()
        }
    }

    fun hasInk(): Boolean = strokes.isNotEmpty()
    fun clear() { strokes.clear(); undoStack.clear(); current = null; invalidate() }

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

    override fun onSaveInstanceState(): Parcelable {
        val bundle = android.os.Bundle()
        bundle.putParcelableArrayList("strokes", strokes)
        bundle.putFloat("scrollOffsetY", scrollOffsetY)
        bundle.putParcelable("superState", super.onSaveInstanceState())
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is android.os.Bundle) {
            val savedStrokes = state.getParcelableArrayList<StrokeData>("strokes")
            if (savedStrokes != null) {
                strokes.clear()
                strokes.addAll(savedStrokes)
            }
            scrollOffsetY = state.getFloat("scrollOffsetY", 0f)
            invalidate()
            super.onRestoreInstanceState(state.getParcelable("superState"))
        } else {
            super.onRestoreInstanceState(state)
        }
    }
}
