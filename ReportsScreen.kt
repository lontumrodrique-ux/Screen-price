package com.lontum.screenprice

import android.widget.LinearLayout
import kotlin.math.abs

class ReportsScreen : Screen() {
    private var period = 0

    override fun title() = "Reports"

    override fun build(root: LinearLayout) {
        val ctx = requireContext()
        val labels = listOf("This month", "Last 30 days", "This year", "All time")
        val buttons = labels.mapIndexed { i, l ->
            if (i == period) ui.button(l) {} else ui.outlined(l) {
                period = i
                render()
            }
        }
        root.addView(ui.buttonRow(buttons[0], buttons[1]))
        root.addView(ui.buttonRow(buttons[2], buttons[3]))

        val from = when (period) {
            0 -> Fmt.startOfMonth(0)
            1 -> Fmt.daysAgo(29)
            2 -> Fmt.startOfYear()
            else -> 0L
        }
        val allSales = db.sales()
        val sales = allSales.filter { it.date >= from }
        val purchases = db.purchases().filter { it.date >= from }
        val models = db.models().associateBy { it.id }

        val revenue = sales.sumOf { it.revenue }
        val cogs = sales.sumOf { it.costTotal }
        val profit = revenue - cogs
        root.addView(ui.section("Totals · ${labels[period]}"))
        root.addView(ui.card {
            addView(ui.row("Total sales revenue", Fmt.money(revenue)))
            addView(ui.row("Cost of screens sold", Fmt.money(cogs)))
            addView(ui.row("Total profit", Fmt.money(profit), if (profit >= 0) R.color.c_good else R.color.c_bad))
            addView(ui.row("Profit margin", Fmt.marginPct(profit, revenue)))
            addView(ui.row("Screens sold", sales.sumOf { it.qty }.toString()))
            addView(ui.divider())
            addView(ui.row("Spent on screen purchases", Fmt.money(purchases.sumOf { it.price * it.qty })))
            addView(ui.row("Screens bought", purchases.sumOf { it.qty }.toString()))
        })

        // Profit by month (last 6 months)
        root.addView(ui.section("Profit by month"))
        val months = (5 downTo 0).map { i ->
            val start = Fmt.startOfMonth(i)
            val end = if (i == 0) Long.MAX_VALUE else Fmt.startOfMonth(i - 1)
            Pair(Fmt.monthLabel(start), allSales.filter { it.date in start until end }.sumOf { it.profit })
        }
        val maxAbs = maxOf(months.maxOf { abs(it.second) }, 1L)
        root.addView(ui.card {
            for ((label, value) in months) {
                addView(ui.row(label, Fmt.money(value), if (value >= 0) R.color.c_text else R.color.c_bad))
                val b = ui.bar(abs(value).toFloat() / maxAbs.toFloat(), if (value >= 0) R.color.c_good else R.color.c_bad)
                b.setPadding(0, 0, 0, ui.dp(6))
                addView(b)
            }
        })

        // Rankings for the chosen period
        val grouped = sales.groupBy { ItemKey(it.modelId, it.quality) }
        fun name(k: ItemKey) = "${models[k.modelId]?.title ?: "(deleted model)"} (${k.quality})"

        root.addView(ui.section("Most profitable screens"))
        val byProfit = grouped.map { (k, l) -> k to l.sumOf { it.profit } }.sortedByDescending { it.second }.take(8)
        if (byProfit.isEmpty()) root.addView(ui.muted("No sales in this period."))
        else root.addView(ui.card {
            byProfit.forEachIndexed { i, (k, v) -> addView(ui.row("${i + 1}. ${name(k)}", Fmt.money(v), if (v >= 0) R.color.c_good else R.color.c_bad)) }
        })

        root.addView(ui.section("Most frequently sold screens"))
        val byQty = grouped.map { (k, l) -> k to l.sumOf { it.qty } }.sortedByDescending { it.second }.take(8)
        if (byQty.isEmpty()) root.addView(ui.muted("No sales in this period."))
        else root.addView(ui.card {
            byQty.forEachIndexed { i, (k, v) -> addView(ui.row("${i + 1}. ${name(k)}", "$v sold")) }
        })

        root.addView(ui.space(8))
        root.addView(ui.outlined("Export all data to Excel (CSV)") { DataIO.shareAll(ctx) })
    }
}
