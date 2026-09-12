package com.summer.notifai.ui.banking.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.summer.core.data.model.MonthlyCashFlow
import kotlin.math.max

class CashFlowBarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val debitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffd95555.toInt() }
    private val creditPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff2e8b57.toInt() }
    private var values: List<MonthlyCashFlow> = emptyList()

    fun submit(values: List<MonthlyCashFlow>) {
        this.values = values.filter { it.currency == "INR" }.takeLast(6)
        contentDescription = "Cash flow chart showing ${this.values.size} months"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.isEmpty()) return
        val highest = values.maxOf { max(it.debit.toFloat(), it.credit.toFloat()) }.coerceAtLeast(1f)
        val groupWidth = width.toFloat() / values.size
        val barWidth = groupWidth * .22f
        values.forEachIndexed { index, value ->
            val center = groupWidth * index + groupWidth / 2
            val debitHeight = height * .88f * value.debit.toFloat() / highest
            val creditHeight = height * .88f * value.credit.toFloat() / highest
            canvas.drawRoundRect(center - barWidth - 3, height - debitHeight, center - 3, height.toFloat(), 8f, 8f, debitPaint)
            canvas.drawRoundRect(center + 3, height - creditHeight, center + barWidth + 3, height.toFloat(), 8f, 8f, creditPaint)
        }
    }
}
