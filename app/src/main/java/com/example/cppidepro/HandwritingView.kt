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
        var color: Int = Color.WHITE
        var thickness: Float = 3f
        
        constructor(parcel: Parcel) : this() {
            parcel.readTypedList(points, InkPoint.CREATOR)
            color = parcel.readInt()
            thickness = parcel.readFloat()
            if (points.isNotEmpty()) {
                path.moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) { path.lineTo(points[i].x, points[i].y) }
            }
        }
        override fun writeToParcel(parcel: Parcel, flags: Int) { 
            parcel.writeTypedList(points) 
            parcel.writeInt(color)
            parcel.writeFloat(thickness)
        }
        override fun describeContents(): Int = 0
        companion object CREATOR : Parcelable.Creator<StrokeData> {
            override fun createFromParcel(parcel: Parcel): StrokeData = StrokeData(parcel)
            override fun newArray(size: Int): Array<StrokeData?> = arrayOfNulls(size)
        }
    }
    
    var isScrollMode = false 
    var isEraseMode = false 
    var isVirtualPenEnabled = false // NEW: Virtual Pen Mode

    var currentPenColor = Color.WHITE
    var currentPenThickness = 3f
    var currentEraserSize = 40f 
    
    // Offset for the virtual pen (draws 120dp above and 30dp to the left of the finger)
    private val virtualPenOffsetX = -30f * resources.displayMetrics.density
    private val virtualPenOffsetY = -120f * resources.displayMetrics.density
    
    var soundManager: SoundManager? = null
    private var lastSoundTime = 0L

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

    // For drawing the virtual pen cursor
    private var lastVirtualX = 0f
    private var lastVirtualY = 0f
    private var isDrawingVirtual = false

    private val linePaint = Paint().apply { color = Color.parseColor("#444444"); strokeWidth = 1.5f * dp; style = Paint.Style.STROKE }
    private val gutterPaint = Paint().apply { color = Color.parseColor("#1A1A1A") }
    
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF88") 
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }
    
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val scrollBarPaint = Paint().apply { color = Color.parseColor("#666666") }

    // Paint for the Virtual Pen cursor
    private val virtualCursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFEB3B") // Yellow
        style = Paint.Style.STROKE
        strokeWidth = 2f * dp
    }
    private val virtualCursorFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#44FFEB3B") // Semi-transparent yellow
        style = Paint.Style.FILL
    }

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
        
        for (stroke in strokes) {
            inkPaint.color = stroke.color
            inkPaint.strokeWidth = stroke.thickness * dp
            canvas.drawPath(stroke.path, inkPaint)
        }
        
        // Draw the Virtual Pen cursor if active
        if (isVirtualPenEnabled && isDrawingVirtual) {
            canvas.drawCircle(lastVirtualX, lastVirtualY, currentPenThickness * dp * 2f, virtualCursorFill)
            canvas.drawCircle(lastVirtualX, lastVirtualY, currentPenThickness * dp * 2f, virtualCursorPaint)
            // Draw a small crosshair
            canvas.drawLine(lastVirtualX - 10f * dp, lastVirtualY, lastVirtualX + 10f * dp, lastVirtualY, virtualCursorPaint)
            canvas.drawLine(lastVirtualX, lastVirtualY - 10f * dp, lastVirtualX, lastVirtualY + 10f * dp, virtualCursorPaint)
        }
        
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
            if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE) {
                val touchX = if (isVirtualPenEnabled) event.x + virtualPenOffsetX else event.x
                val touchY = if (isVirtualPenEnabled) event.y + scrollOffsetY + virtualPenOffsetY else event.y + scrollOffsetY
                
                if (isVirtualPenEnabled) {
                    lastVirtualX = touchX
                    lastVirtualY = touchY
                    isDrawingVirtual = true
                    invalidate()
                }

                val eraseRadius = currentEraserSize * dp 
                var erased = false
                
                val iterator = strokes.iterator()
                while (iterator.hasNext()) {
                    val stroke = iterator.next()
                    for (point in stroke.points) {
                        val dx = point.x - touchX
                        val dy = point.y - touchY
                        if (sqrt(dx * dx + dy * dy) < eraseRadius) {
                            iterator.remove()
                            erased = true
                            break
                        }
                    }
                }
                
                if (erased) {
                    playSound(false) 
                    invalidate()
                }
                return true
            }
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                isDrawingVirtual = false
                invalidate()
            }
            return false
        } else {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val drawX = if (isVirtualPenEnabled) event.x + virtualPenOffsetX else event.x
                    val drawY = if (isVirtualPenEnabled) event.y + scrollOffsetY + virtualPenOffsetY else event.y + scrollOffsetY
                    
                    if (isVirtualPenEnabled) {
                        lastVirtualX = drawX
                        lastVirtualY = drawY
                        isDrawingVirtual = true
                    }
                    
                    startStroke(drawX, drawY, event.eventTime)
                    playSound(true) 
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val stroke = current ?: return false
                    val drawX = if (isVirtualPenEnabled) event.x + virtualPenOffsetX else event.x
                    val drawY = if (isVirtualPenEnabled) event.y + scrollOffsetY + virtualPenOffsetY else event.y + scrollOffsetY
                    
                    if (isVirtualPenEnabled) {
                        lastVirtualX = drawX
                        lastVirtualY = drawY
                    }
                    
                    for (i in 0 until event.historySize) {
                        val histX = if (isVirtualPenEnabled) event.getHistoricalX(i) + virtualPenOffsetX else event.getHistoricalX(i)
                        val histY = if (isVirtualPenEnabled) event.getHistoricalY(i) + scrollOffsetY + virtualPenOffsetY else event.getHistoricalY(i) + scrollOffsetY
                        extendStroke(stroke, histX, histY, event.getHistoricalEventTime(i))
                    }
                    extendStroke(stroke, drawX, drawY, event.eventTime)
                    
                    playSound(true) 
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val stroke = current ?: return false
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        val drawX = if (isVirtualPenEnabled) event.x + virtualPenOffsetX else event.x
                        val drawY = if (isVirtualPenEnabled) event.y + scrollOffsetY + virtualPenOffsetY else event.y + scrollOffsetY
                        extendStroke(stroke, drawX, drawY, event.eventTime)
                    }
                    current = null
                    isDrawingVirtual = false
                    invalidate()
                    return true
                }
            }
        }
        return current != null
    }

    private fun playSound(isDrawing: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastSoundTime > 40) { 
            if (isDrawing) soundManager?.playDrawSound() else soundManager?.playEraseSound()
            lastSoundTime = now
        }
    }

    fun scrollByLines(lines: Int) { updateScroll(scrollOffsetY + (lines * lineSpacing)) }

    private fun updateScroll(newY: Float) {
        val maxScroll = max(0f, totalContentHeight - height)
        scrollOffsetY = newY.coerceIn(0f, maxScroll)
        invalidate()
    }

    private fun startStroke(x: Float, y: Float, time: Long) {
        val stroke = StrokeData()
        stroke.color = currentPenColor
        stroke.thickness = currentPenThickness
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

    fun undo() { if (strokes.isNotEmpty()) { undoStack.add(strokes.removeAt(strokes.size - 1)); invalidate() } }
    fun redo() { if (undoStack.isNotEmpty()) { strokes.add(undoStack.removeAt(undoStack.size - 1)); invalidate() } }
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
