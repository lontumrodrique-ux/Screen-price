package com.lontum.screenprice

import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import java.util.Locale

class InventoryScreen : Screen() {
    private var filter = 0 // 0 = all, 1 = low stock, 2 = in stock

    override fun title() = "Inventory"

    override fun build(root: LinearLayout) {
        val ctx = requireContext()
        if (!Prefs.stockOn(ctx)) {
            root.addView(ui.card {
                addView(ui.text("Stock tracking is off", 16f, true))
                addView(ui.muted("Turn it on to count screens in stock, sold, damaged or returned."))
                addView(ui.button("Turn on stock tracking") {
                    Prefs.setStockOn(ctx, true)
                    render()
                })
            })
            return
        }

        val models = db.models().associateBy { it.id }
        val low = Prefs.lowStock(ctx)
        val items = db.allStats().values
            .filter { models.containsKey(it.modelId) && (it.boughtQty > 0 || it.soldQty > 0 || it.adjDelta != 0) }
            .sortedBy { (models[it.modelId]?.title ?: "").lowercase(Locale.ROOT) + " " + it.quality }

        val inStock = items.sumOf { maxOf(it.available, 0) }
        val value = items.sumOf { if (it.available > 0) it.available.toLong() * (it.average ?: 0L) else 0L }
        val lowItems = items.filter { it.available <= low }
        root.addView(ui.grid(listOf(
            ui.tile("Screens in stock", inStock.toString()),
            ui.tile("Stock value", Fmt.money(value), "at average cost"),
            ui.tile("Low stock", lowItems.size.toString(), "$low or fewer left", if (lowItems.isEmpty()) R.color.c_text else R.color.c_bad)
        )))

        val labels = listOf("All", "Low stock", "In stock")
        val buttons: List<MaterialButton> = labels.mapIndexed { i, l ->
            if (i == filter) ui.button(l) {} else ui.outlined(l) {
                filter = i
                render()
            }
        }
        root.addView(ui.buttonRow(*buttons.toTypedArray()))

        val shown = when (filter) {
            1 -> lowItems
            2 -> items.filter { it.available > 0 }
            else -> items
        }
        if (items.isEmpty()) note(root, "No screens yet. Stock is counted from your purchases and sales.")
        else if (shown.isEmpty()) note(root, "Nothing to show for this filter.")

        for (st in shown) {
            val m = models[st.modelId] ?: continue
            root.addView(ui.card({ itemActions(m, st) }) {
                val r = ui.hbox()
                r.addView(ui.text(m.title, 15f, true), ui.lp(0, WRAP, 1f, end = 8))
                r.addView(ui.badge(st.quality))
                addView(r)
                val a = st.available
                addView(ui.text("In stock: $a", 15f, true, if (a <= low) R.color.c_bad else R.color.c_good))
                addView(ui.muted("Bought ${st.boughtQty} · Sold ${st.soldQty} · Damaged/returned ${st.lostQty}"))
                if (a > 0) addView(ui.muted("Value: ${Fmt.money(a.toLong() * (st.average ?: 0L))}"))
            })
        }

        // Recent stock changes, so mistakes can be undone
        val adjs = db.adjustments().take(20)
        if (adjs.isNotEmpty()) {
            root.addView(ui.section("Recent stock changes"))
            for (a in adjs) {
                val name = models[a.modelId]?.title ?: "(deleted model)"
                val sign = if (a.delta > 0) "+${a.delta}" else a.delta.toString()
                root.addView(ui.card({
                    confirm("Remove this stock change?", "${Adj.label(a.kind)} $sign · $name (${a.quality})", "Remove") {
                        db.deleteAdjustment(a.id)
                        render()
                    }
                }) {
                    addView(ui.row("$name (${a.quality})", "${Adj.label(a.kind)} $sign", R.color.c_text, false))
                    addView(ui.muted(Fmt.date(a.date) + if (a.notes.isNotBlank()) " · ${a.notes}" else ""))
                })
            }
        }
    }

    private fun itemActions(m: PhoneModel, st: ItemStats) {
        choose("${m.title} (${st.quality})", listOf(
            "Record damaged screen",
            "Returned to supplier",
            "Add screens back to stock",
            "Correct the stock count",
            "Open model details"
        )) { w ->
            when (w) {
                0 -> askQtyNote("Damaged screens") { q, n -> addAdj(st, Adj.DAMAGED, q, n) }
                1 -> askQtyNote("Returned to supplier") { q, n -> addAdj(st, Adj.RETURNED, q, n) }
                2 -> askQtyNote("Add back to stock") { q, n -> addAdj(st, Adj.ADDED, q, n) }
                3 -> askNumber("Actual number in stock", "Count the screens you have", maxOf(st.available, 0).toLong(), null) { v ->
                    if (v != null) {
                        val delta = (v - st.available).toInt()
                        if (delta != 0) addAdj(st, Adj.CORRECTION, delta, "Stock count corrected")
                    }
                }
                else -> openModel(m.id)
            }
        }
    }

    private fun addAdj(st: ItemStats, kind: String, qty: Int, note: String) {
        db.saveAdjustment(Adjustment(modelId = st.modelId, quality = st.quality, kind = kind, qty = qty, notes = note))
        ui.toast("Stock updated")
        render()
    }
}
