package com.lontum.screenprice

import android.os.Bundle
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import java.util.Locale

// ======================= SALES & PROFITS =======================

class SalesScreen : Screen() {

    override fun title() = "Sales & profits"

    override fun build(root: LinearLayout) {
        root.addView(ui.button("+  Record a sale") { open(SaleFormScreen.of()) })
        val sales = db.sales()
        val models = db.models().associateBy { it.id }

        val periods = listOf(
            "Today" to Fmt.today(),
            "This week" to Fmt.startOfWeek(),
            "This month" to Fmt.startOfMonth(0)
        )
        root.addView(ui.section("Profit"))
        val tiles = periods.map { (label, from) ->
            val list = sales.filter { it.date >= from }
            val profit = list.sumOf { it.profit }
            ui.tile(
                label,
                Fmt.money(profit),
                "${Fmt.money(list.sumOf { it.revenue })} sales · ${list.sumOf { it.qty }} screens",
                if (profit >= 0) R.color.c_good else R.color.c_bad
            )
        }
        root.addView(ui.grid(tiles, 1))

        root.addView(ui.section("Recent sales"))
        if (sales.isEmpty()) {
            note(root, "No sales recorded yet. Tap “Record a sale” each time you fit or sell a screen.")
            return
        }
        for (s in sales.take(80)) {
            val name = models[s.modelId]?.title ?: "(deleted model)"
            root.addView(ui.card({ saleActions(s) }) {
                val r = ui.hbox()
                r.addView(ui.text("$name (${s.quality})", 14.5f, true), ui.lp(0, WRAP, 1f, end = 8))
                r.addView(ui.text(Fmt.money(s.profit), 14.5f, true, if (s.profit >= 0) R.color.c_good else R.color.c_bad))
                addView(r)
                addView(ui.muted("${Fmt.date(s.date)} · ${s.qty} × ${Fmt.money(s.price)} · cost ${Fmt.money(s.cost)}"))
            })
        }
        if (sales.size > 80) root.addView(ui.muted("Showing the latest 80 sales. See Reports for totals."))
    }

    private fun saleActions(s: Sale) {
        choose("Sale on ${Fmt.date(s.date)}", listOf("Edit this sale", "Delete this sale")) { w ->
            if (w == 0) open(SaleFormScreen.edit(s.id))
            else confirm("Delete this sale?", "${s.qty} × ${Fmt.money(s.price)} on ${Fmt.date(s.date)}.", "Delete") {
                db.deleteSale(s.id)
                render()
            }
        }
    }
}

// ======================= RECORD / EDIT A SALE =======================

class SaleFormScreen : Screen() {
    companion object {
        fun of(modelId: Long = 0, quality: String = "") = SaleFormScreen().apply {
            arguments = Bundle().apply {
                putLong("model", modelId)
                putString("quality", quality)
            }
        }

        fun edit(saleId: Long) = SaleFormScreen().apply {
            arguments = Bundle().apply { putLong("sale", saleId) }
        }
    }

    override val liveRefresh = false
    private val saleId: Long get() = args().getLong("sale")

    private var selectedModel = 0L
    private var date = Fmt.today()
    private var titleToId = HashMap<String, Long>()
    private var lastCostPrefill = ""
    private var lastPricePrefill = ""
    private var editingQty = 0

    private lateinit var modelF: Field
    private lateinit var qualityF: Field
    private lateinit var costF: Field
    private lateinit var priceF: Field
    private lateinit var qtyF: Field
    private lateinit var dateF: Field
    private lateinit var notesF: Field
    private lateinit var live: LinearLayout

    override fun title() = if (saleId > 0) "Edit sale" else "Record a sale"

    override fun build(root: LinearLayout) {
        val existing = if (saleId > 0) db.sale(saleId) else null
        selectedModel = existing?.modelId ?: args().getLong("model")
        date = existing?.date ?: Fmt.today()
        editingQty = existing?.qty ?: 0

        val models = db.models()
        titleToId = HashMap()
        for (m in models) titleToId[m.title.lowercase(Locale.ROOT)] = m.id
        val startTitle = models.firstOrNull { it.id == selectedModel }?.title ?: ""

        modelF = ui.dropdown("Phone model", models.map { it.title }, startTitle)
        (modelF.edit as AutoCompleteTextView).setOnItemClickListener { parent, _, pos, _ ->
            val t = parent.getItemAtPosition(pos)?.toString() ?: ""
            selectedModel = titleToId[t.lowercase(Locale.ROOT)] ?: 0L
            afterSelection()
        }
        modelF.onChange {
            selectedModel = titleToId[modelF.text.lowercase(Locale.ROOT)] ?: 0L
            afterSelection()
        }
        qualityF = ui.dropdown("Screen quality", Prefs.QUALITIES, existing?.quality ?: (args().getString("quality") ?: ""))
        qualityF.onChange { afterSelection() }
        costF = ui.numberField("Purchase cost per screen (FCFA)", existing?.cost?.toString() ?: "")
        costF.layout.helperText = "Filled from your latest purchase price. You can change it."
        priceF = ui.numberField("Customer selling price per screen (FCFA)", existing?.price?.toString() ?: "")
        qtyF = ui.numberField("Quantity sold", (existing?.qty ?: 1).toString())
        dateF = ui.dateField("Date of sale", { date }) { date = it }
        notesF = ui.field("Notes (optional, e.g. customer name)", existing?.notes ?: "", lines = 2)
        for (f in listOf(modelF, qualityF, costF, priceF, qtyF, dateF, notesF)) root.addView(f.layout)
        costF.onChange { updateLive() }
        priceF.onChange { updateLive() }
        qtyF.onChange { updateLive() }

        live = ui.vbox()
        root.addView(live)
        root.addView(ui.button(if (saleId > 0) "Save changes" else "Save sale") { save() })

        if (existing == null) prefill()
        updateLive()
    }

    private fun afterSelection() {
        prefill()
        updateLive()
    }

    private fun currentStats(): ItemStats? {
        val q = Prefs.canonicalQuality(qualityF.text)
        if (selectedModel <= 0 || q.isEmpty()) return null
        return db.allStats()[ItemKey(selectedModel, q)]
    }

    /** Fills cost and selling price from your saved prices, unless you typed your own. */
    private fun prefill() {
        if (!::priceF.isInitialized) return
        val st = currentStats()
        val cost = st?.latest?.price?.toString() ?: ""
        val price = st?.let { Pricing.sellPrice(requireContext(), it) }?.toString() ?: ""
        val c = costF.text
        if (c.isEmpty() || c == lastCostPrefill) {
            lastCostPrefill = cost
            if (c != cost) costF.text = cost
        }
        val p = priceF.text
        if (p.isEmpty() || p == lastPricePrefill) {
            lastPricePrefill = price
            if (p != price) priceF.text = price
        }
    }

    private fun updateLive() {
        if (!::live.isInitialized) return
        live.removeAllViews()
        val cost = Fmt.parseMoney(costF.text) ?: 0L
        val price = Fmt.parseMoney(priceF.text) ?: 0L
        val qty = Fmt.parseInt(qtyF.text) ?: 0
        live.addView(ui.card {
            if (price > 0 && qty > 0) {
                val revenue = price * qty
                val profit = (price - cost) * qty
                addView(ui.row("Total sale", Fmt.money(revenue)))
                addView(ui.row("Total cost", Fmt.money(cost * qty)))
                addView(ui.row("Profit", Fmt.money(profit) + "  (" + Fmt.marginPct(profit, revenue) + ")",
                    if (profit >= 0) R.color.c_good else R.color.c_bad))
            } else {
                addView(ui.muted("Enter the selling price and quantity to see your profit."))
            }
            val st = currentStats()
            if (Prefs.stockOn(requireContext()) && st != null) {
                val available = st.available + editingQty
                if (qty > available) addView(ui.text("Only $available in stock for this screen.", 13.5f, true, R.color.c_bad))
                else addView(ui.muted("In stock: $available"))
            }
        })
    }

    private fun save() {
        val q = Prefs.canonicalQuality(qualityF.text)
        val cost = Fmt.parseMoney(costF.text)
        val price = Fmt.parseMoney(priceF.text) ?: 0L
        val qty = Fmt.parseInt(qtyF.text) ?: 0
        var ok = true
        if (selectedModel <= 0L) { modelF.error("Pick a model from your list"); ok = false } else modelF.error(null)
        if (q.isEmpty()) { qualityF.error("Choose the screen quality"); ok = false } else qualityF.error(null)
        if (cost == null) { costF.error("Enter what the screen cost you"); ok = false } else costF.error(null)
        if (price <= 0L) { priceF.error("Enter the selling price"); ok = false } else priceF.error(null)
        if (qty <= 0) { qtyF.error("At least 1"); ok = false } else qtyF.error(null)
        if (!ok || cost == null) return

        val s = (if (saleId > 0) db.sale(saleId) else null) ?: Sale()
        s.modelId = selectedModel
        s.quality = q
        s.cost = cost
        s.price = price
        s.qty = qty
        s.date = date
        s.notes = notesF.text
        db.saveSale(s)
        ui.toast("Sale saved · profit ${Fmt.money(s.profit)}")
        main.back()
    }
}
