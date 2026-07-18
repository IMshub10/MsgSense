package com.summer.notifai.ui.banking.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.summer.core.data.model.CategoryTotal

class CategoryDonutView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 34f
        strokeCap = Paint.Cap.BUTT
    }
    private val colors = intArrayOf(0xff2f6fed.toInt(), 0xff7c4dff.toInt(), 0xff00a896.toInt(), 0xffff9f1c.toInt(), 0xffe8557a.toInt(), 0xff6c757d.toInt())
    private var values: List<CategoryTotal> = emptyList()

    fun submit(values: List<CategoryTotal>) {
        this.values = values.filter { it.currency == "INR" && it.amount.signum() > 0 }
        contentDescription = "Expense categories: " + this.values.joinToString { "${it.category} ${it.amount}" }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val total = values.sumOf { it.amount }.toFloat()
        if (total <= 0f) return
        val inset = paint.strokeWidth / 2 + 4
        val bounds = RectF(inset, inset, width - inset, height - inset)
        var start = -90f
        values.forEachIndexed { index, value ->
            val sweep = 360f * value.amount.toFloat() / total
            paint.color = colors[index % colors.size]
            canvas.drawArc(bounds, start, sweep, false, paint)
            start += sweep
        }
    }
}
