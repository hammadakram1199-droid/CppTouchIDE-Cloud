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

class HandwritingView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // Make InkPoint Parcelable so we can save it during rotation
    class InkPoint(val x: Float, val y: Float, val t: Long) : Parcelable {
        constructor(parcel: Parcel) : this(parcel.readFloat(), parcel.readFloat(), parcel.readLong())
        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeFloat(x); parcel.writeFloat(y); parcel.writeLong(t)
        }
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

    private val dp = resources.displayMetrics.density
    private val lineSpacing = 42f * dp 
    private val gutterWidth = 42f * dp 
    
    private val strokes = ArrayList<StrokeData>()
    private var current: StrokeData? = null
    private var lastX = 0f
    private var lastY = 0f

    private val linePaint = Paint().apply { color = Color.parseColor("#444444"); strokeWidth = 1.5f * dp; style = Paint.Style.STROKE }
    private val gutterPaint = Paint().apply { color = Color.parseColor("#1A1A1A") }
    
    private val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00FF88") 
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    }
    
    private val inkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF"); style = Paint.Style.STROKE
        strokeWidth = 2f * dp; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.parseColor("#1E1E1E"))
        canvas.drawRect(0f, 0f, gutterWidth, height.toFloat(), gutterPaint)
        
        val lineCount = ceil(height / lineSpacing).toInt()
        for (i in 1..lineCount) {
            val y = i * lineSpacing
            canvas.drawLine(gutterWidth, y, width.toFloat(), y, linePaint)
            val centerY = y - (lineSpacing / 2f) + (numberPaint.textSize / 3f)
            canvas.drawText(i.toString(), gutterWidth / 2f, centerY, numberPaint)
        }
        for (stroke in strokes) canvas.drawPath(stroke.path, inkPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isScrollMode) return false // Let ScrollView handle it
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true) 
                startStroke(event.x, event.y, event.eventTime)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val stroke = current ?: return false
                parent?.requestDisallowInterceptTouchEvent(true)
                for (i in 0 until event.historySize) {
                    extendStroke(stroke, event.getHistoricalX(i), event.getHistoricalY(i), event.getHistoricalEventTime(i))
                }
                extendStroke(stroke, event.x, event.y, event.eventTime)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val stroke = current ?: return false
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    extendStroke(stroke, event.x, event.y, event.eventTime)
                }
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
        strokes.add(stroke)
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

    // --- ROTATION STATE SAVING ---
    override fun onSaveInstanceState(): Parcelable {
        val bundle = android.os.Bundle()
        bundle.putParcelableArrayList("strokes", strokes)
        bundle.putParcelable("superState", super.onSaveInstanceState())
        return bundle
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is android.os.Bundle) {
            val savedStrokes = state.getParcelableArrayList<StrokeData>("strokes")
            if (savedStrokes != null) {
                strokes.clear()
                strokes.addAll(savedStrokes)
                invalidate()
            }
            super.onRestoreInstanceState(state.getParcelable("superState"))
        } else {
            super.onRestoreInstanceState(state)
        }
    }
}
