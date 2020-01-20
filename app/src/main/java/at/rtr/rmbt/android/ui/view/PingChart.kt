package at.rtr.rmbt.android.ui.view

import android.content.Context
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Canvas
import android.util.AttributeSet
import at.rtr.rmbt.android.R
import at.specure.data.entity.GraphItemRecord
import at.specure.data.entity.TestResultGraphItemRecord
import timber.log.Timber
import kotlin.math.*

class PingChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : PingChartView(context, attrs) {


    private var paintFill: Paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var maxValue: Int? = 0
    private var graphItems: List<TestResultGraphItemRecord>? = null

    init {

        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.PingChart)


        paintFill.color = typedArray.getColor(
            R.styleable.PingChart_bar_color,
            context.getColor(R.color.ping_bar_color)
        )
        paintFill.style = Paint.Style.FILL
        paintFill.isAntiAlias = true


        typedArray.recycle()
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)



        graphItems?.let { items ->

            maxValue?.let {

                val barWidth = getChartWidth() / items.size
                val padding = barWidth / 4.0f
                for (index in items.indices) {

                    //float left, float top, float right, float bottom
                    val left = padding + (barWidth*index)
                    val right = left + (barWidth/2)
                    val top = getChartHeight() * (1.0f - (items[index].value.toFloat()/it.toFloat()))
                    val bottom = getChartHeight()

                    canvas?.drawRect(left, top, right,bottom, paintFill);
                }
            }
        }
    }

    /**
     * This function is use for calculate path
     */
    fun addGraphItems(graphItems: List<TestResultGraphItemRecord>?) {

        graphItems?.let {
            this.graphItems = it
            setYLabels(getYLabels(it))
        }
        invalidate()
    }

    private fun getYLabels(graphItems: List<TestResultGraphItemRecord>):Array<Int> {

        val gap = graphItems.let {
            it.maxBy { it.value }?.let { item ->
                (ceil(item.value * 5 / 100.0) * 5).toInt()
            }
        }
        val gapList = Array(5) { i -> if (gap!=null) (i * gap) else 0 }
        maxValue = gapList.maxBy { it }
        return gapList
    }
    fun reset() {
        invalidate()
    }

    override fun onDetachedFromWindow() {
        reset()
        super.onDetachedFromWindow()
    }
}