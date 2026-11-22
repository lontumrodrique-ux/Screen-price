package com.lontum.screenprice

import android.widget.ImageView
import android.widget.LinearLayout

class DashboardScreen : Screen() {

    override fun title() = getString(R.string.app_name)

    override fun build(root: LinearLayout) {
        val ctx = requireContext()
        val models = db.models()
        val byId = models.associateBy { it.id }
        val stats = db.allStats().values.filter { byId.containsKey(it.modelId) }
        val sales = db.sales()
        val stockOn = Prefs.stockOn(ctx)

        // Search bar
        root.addView(ui.card({ main.openRoot(MainActivity.NAV_SEARCH) }) {
            val r = ui.hbox()
            val icon = ImageView(ctx)
            icon.setImageResource(android.R.drawable.ic_menu_search)
            icon.setColorFilter(ui.c(R.color.c_muted))
            r.addView(icon, LinearLayout.LayoutParams(ui.dp(26), ui.dp(26)))
            val hint = ui.muted("Search a phone model, e.g. Camon 20", 15f)
            val p = ui.lp(0, WRAP, 1f)
            p.marginStart = ui.dp(10)
            r.addView(hint, p)
            addView(r)
        })

        root.addView(ui.button("+  Add screen price") { open(PurchaseFormScreen.of()) })
        root.addView(ui.outlined("Record a sale") { open(SaleFormScreen.of()) })

        if (models.isEmpty()) {
            root.addView(ui.card {
                addView(ui.heading("Welcome"))
                addView(ui.muted("Your database is empty. Add your first phone model, then record what you paid for its screen. The app works out your customer price.", 14f))
                addView(ui.button("Add my first phone model") { open(ModelEditScreen.of(0, "", true)) })
            })
            return
        }

        // Overview tiles
        val monthStart = Fmt.startOfMonth(0)
        val monthSales = sales.filter { it.date >= monthStart }
        val monthProfit = monthSales.sumOf { it.profit }
        val screensBought = stats.sumOf { it.boughtQty }
        val tiles = ArrayList<android.view.View>()
        tiles.add(ui.tile("Phone models saved", Fmt.num(models.size.toLong()), null, R.color.c_text) { main.openRoot(MainActivity.NAV_SEARCH) })
        tiles.add(ui.tile("Purchases recorded", Fmt.num(db.purchaseCount()), "$screensBought screens bought"))
        tiles.add(ui.tile("Profit this month", Fmt.money(monthProfit), "${monthSales.size} sales", if (monthProfit >= 0) R.color.c_good else R.color.c_bad) { main.openRoot(MainActivity.NAV_SALES) })
        if (stockOn) {
            val inStock = stats.sumOf { maxOf(it.available, 0) }
            val value = stats.sumOf { if (it.available > 0) it.available.toLong() * (it.average ?: 0L) else 0L }
            tiles.add(ui.tile("Stock value", Fmt.money(value), "$inStock screens in stock") { main.openRoot(MainActivity.NAV_INV) })
        }
        root.addView(ui.section("Overview"))
        root.addView(ui.grid(tiles))

        // Low stock warning
        if (stockOn) {
            val low = Prefs.lowStock(ctx)
            val lowItems = stats.filter { it.boughtQty > 0 && it.available <= low }
            if (lowItems.isNotEmpty()) {
                root.addView(ui.card({ main.openRoot(MainActivity.NAV_INV) }) {
                    addView(ui.text("${lowItems.size} screen${if (lowItems.size == 1) " is" else "s are"} low on stock", 15f, true, R.color.c_bad))
                    addView(ui.muted("Tap to see them in Inventory."))
                })
            }
        }

        // Profit margin summary
        val priced = stats.filter { it.latest != null }
        root.addView(ui.section("Estimated profit margins"))
        if (priced.isEmpty()) {
            note(root, "Add a purchase to see your expected margins.")
        } else {
            var sumProfit = 0L
            var sumMargin = 0.0
            var marginCount = 0
            var worst: ItemStats? = null
            var worstMargin = Double.MAX_VALUE
            for (st in priced) {
                val cost = st.latest!!.price
                val sell = Pricing.sellPrice(ctx, st) ?: cost
                val profit = sell - cost
                sumProfit += profit
                if (sell > 0) {
                    val mg = profit * 100.0 / sell
                    sumMargin += mg
                    marginCount++
                    if (mg < worstMargin) {
                        worstMargin = mg
                        worst = st
                    }
                }
            }
            val avgProfit = Math.round(sumProfit.toDouble() / priced.size)
            val avgMargin = if (marginCount == 0) 0.0 else Math.round(sumMargin / marginCount * 10) / 10.0
            val w = worst
            root.addView(ui.card {
                addView(ui.row("Average profit per screen", Fmt.money(avgProfit), if (avgProfit >= 0) R.color.c_good else R.color.c_bad))
                addView(ui.row("Average margin", Fmt.pct(avgMargin)))
                if (w != null) {
                    val name = byId[w.modelId]?.title ?: ""
                    addView(ui.row("Lowest margin", "$name (${w.quality}) · ${Fmt.pct(Math.round(worstMargin * 10) / 10.0)}"))
                }
                addView(ui.muted("Based on the latest purchase price of ${priced.size} screen${if (priced.size == 1) "" else "s"}, using your price where set, otherwise the recommended price."))
            })
        }

        // Recently updated prices
        val recent = priced.sortedWith(
            compareByDescending<ItemStats> { it.latest?.date ?: 0L }.thenByDescending { it.latest?.created ?: 0L }
        ).take(6)
        if (recent.isNotEmpty()) {
            root.addView(ui.section("Recently updated prices"))
            for (st in recent) {
                val m = byId[st.modelId] ?: continue
                val p = st.latest!!
                val sell = Pricing.sellPrice(ctx, st)
                val sub = listOf(st.quality, p.supplierName, Fmt.date(p.date)).filter { it.isNotBlank() }.joinToString(" · ")
                root.addView(itemCard(m, sub, Fmt.money(p.price), "sell ${Fmt.money(sell)}"))
            }
        }

        // Most purchased screens
        val top = stats.filter { it.boughtQty > 0 }.sortedByDescending { it.boughtQty }.take(5)
        if (top.isNotEmpty()) {
            root.addView(ui.section("Most purchased screens"))
            for (st in top) {
                val m = byId[st.modelId] ?: continue
                root.addView(itemCard(m, "${st.quality} · ${st.boughtQty} screens in ${st.purchases.size} purchase${if (st.purchases.size == 1) "" else "s"}", Fmt.money(st.latest?.price), "latest price"))
            }
        }
    }
}
