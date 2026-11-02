package com.pulse.app
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View

class GraphView(c: Context) : View(c) {
    var data: List<Float> = emptyList()
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF7C5CFF.toInt(); style = Paint.Style.STROKE; strokeWidth = 3f }
    override fun onDraw(cv: Canvas) {
        if (data.size < 2) return
        val mx = maxOf(40f, data.max()); val path = Path()
        data.forEachIndexed { i, v ->
            val x = width * i / (data.size - 1f); val y = height - (v.coerceAtMost(mx) / mx) * height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        cv.drawPath(path, p)
    }
}￼Enter
