package com.lontum.screenprice

import android.os.Bundle
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.Locale

/**
 * Record a screen purchase. Every save adds a new record, so the price history is never lost.
 * The recommended customer price updates live as you type the purchase price.
 */
class PurchaseFormScreen : Screen() {
    companion object {
        fun of(modelId: Long = 0, quality: String = "") = PurchaseFormScreen().apply {
            arguments = Bundle().apply {
                putLong("model", modelId)
                putString("quality", quality)
            }
        }

        fun edit(purchaseId: Long) = PurchaseFormScreen().apply {
            arguments = Bundle().apply { putLong("purchase", purchaseId) }
        }
    }

    override val liveRefresh = false
    private val purchaseId: Long get() = args().getLong("purchase")
    private val argModel: Long get() = args().getLong("model")

    private var selectedModel = 0L
    private var date = Fmt.today()
    private var titleToId = HashMap<String, Long>()
    private var lastCustomPrefill = ""

    private lateinit var modelF: Field
    private lateinit var qualityF: Field
    private lateinit var supplierF: Field
    private lateinit var priceF: Field
    private lateinit var qtyF: Field
    private lateinit var dateF: Field
    private lateinit var notesF: Field
    private lateinit var customF: Field
    private lateinit var live: LinearLayout

    override fun title() = if (purchaseId > 0) "Edit purchase" else "Add screen purchase"

    override fun build(root: LinearLayout) {
        val existing = if (purchaseId > 0) db.purchase(purchaseId) else null
        selectedModel = existing?.modelId ?: argModel
        date = existing?.date ?: Fmt.today()

        val models = db.models()
        titleToId = mapTitles(models)
        val startTitle = models.firstOrNull { it.id == selectedModel }?.title ?: ""

        modelF = ui.dropdown("Phone model (type to search your list)", models.map { it.title }, startTitle)
        val ac = modelF.edit as AutoCompleteTextView
        ac.setOnItemClickListener { parent, _, pos, _ ->
            val t = parent.getItemAtPosition(pos)?.toString() ?: ""
            selectedModel = titleToId[t.lowercase(Locale.ROOT)] ?: 0L
            afterSelection()
        }
        modelF.onChange {
            selectedModel = titleToId[modelF.text.lowercase(Locale.ROOT)] ?: 0L
            afterSelection()
        }
        root.addView(modelF.layout)
        root.addView(ui.outlined("+  New model (not in my list)") {
            open(ModelEditScreen.of(0, if (selectedModel == 0L) modelF.text else "", false))
        })

        qualityF = ui.dropdown("Screen quality", Prefs.QUALITIES, existing?.quality ?: (args().getString("quality") ?: ""))
        qualityF.onChange { afterSelection() }
        supplierF = ui.dropdown("Supplier's name", db.suppliers().map { it.name }, existing?.supplierName ?: "")
        priceF = ui.numberField("Purchase price per screen (FCFA)", existing?.price?.toString() ?: "")
        priceF.onChange { updateLive() }
        qtyF = ui.numberField("Quantity purchased", (existing?.qty ?: 1).toString())
        dateF = ui.dateField("Date of purchase", { date }) { date = it }
        notesF = ui.field("Notes (optional)", existing?.notes ?: "", lines = 2)
        for (f in listOf(qualityF, supplierF, priceF, qtyF, dateF, notesF)) root.addView(f.layout)

        root.addView(ui.section("Customer price"))
        live = ui.vbox()
        root.addView(live)
        customF = ui.numberField("Your customer price (optional)")
        customF.layout.helperText = "Leave empty to use the recommended price. Your purchase cost is not changed."
        customF.onChange { updateLive() }
        root.addView(customF.layout)

        root.addView(ui.button(if (purchaseId > 0) "Save changes" else "Save purchase") { save() })

        prefillCustom()
        updateLive()
    }

    private fun mapTitles(models: List<PhoneModel>): HashMap<String, Long> {
        val m = HashMap<String, Long>()
        for (x in models) m[x.title.lowercase(Locale.ROOT)] = x.id
        return m
    }

    private fun afterSelection() {
        prefillCustom()
        updateLive()
    }

    /** Shows your saved customer price for this model + quality, unless you typed your own. */
    private fun prefillCustom() {
        if (!::customF.isInitialized) return
        val q = Prefs.canonicalQuality(qualityF.text)
        val cur = customF.text
        if (cur.isNotEmpty() && cur != lastCustomPrefill) return
        val v = if (selectedModel > 0 && q.isNotEmpty()) db.customPrice(selectedModel, q)?.toString() ?: "" else ""
        lastCustomPrefill = v
        if (cur != v) customF.text = v
    }

    private fun supplierSuffix(p: Purchase): String = if (p.supplierName.isBlank()) "" else " · ${p.supplierName}"

    private fun updateLive() {
        if (!::live.isInitialized) return
        live.removeAllViews()
        val ctx = requireContext()
        val q = Prefs.canonicalQuality(qualityF.text)
        val cost = Fmt.parseMoney(priceF.text) ?: 0L
        live.addView(ui.card {
            if (selectedModel > 0 && q.isNotEmpty()) {
                val prev = db.purchasesForModel(selectedModel).filter { it.quality == q && it.id != purchaseId }
                if (prev.isEmpty()) {
                    addView(ui.muted("First purchase of this screen quality."))
                } else {
                    val latest = prev.first()
                    val lowest = prev.minByOrNull { it.price } ?: latest
                    val totalQty = prev.sumOf { it.qty }
                    val avg = if (totalQty > 0) Math.round(prev.sumOf { it.price * it.qty }.toDouble() / totalQty) else 0L
                    addView(ui.row("Last price", Fmt.money(latest.price) + supplierSuffix(latest)))
                    addView(ui.row("Lowest price", Fmt.money(lowest.price) + supplierSuffix(lowest)))
                    addView(ui.row("Average price", Fmt.money(avg)))
                    if (cost > 0 && cost < lowest.price) {
                        addView(ui.badge("Cheapest price so far", R.color.c_good_bg, R.color.c_good))
                    } else if (cost > latest.price) {
                        addView(ui.muted("${Fmt.money(cost - latest.price)} more than last time"))
                    }
                    addView(ui.divider())
                }
            } else {
                addView(ui.muted("Choose a model and quality to see your previous prices."))
            }

            if (cost > 0 && q.isNotEmpty()) {
                val rec = Pricing.recommended(ctx, cost, q)
                addView(ui.row("Purchase price", Fmt.money(cost)))
                addView(ui.row("Recommended customer price", Fmt.money(rec), R.color.c_primary_text))
                addView(ui.row("Expected profit", Fmt.money(rec - cost) + "  (" + Fmt.marginPct(rec - cost, rec) + ")",
                    if (rec - cost >= 0) R.color.c_good else R.color.c_bad))
                addView(ui.muted(Pricing.methodLabel(ctx, q), 12.5f))
                val custom = Fmt.parseMoney(customF.text)
                if (custom != null && custom > 0) {
                    addView(ui.divider())
                    addView(ui.row("Your price", Fmt.money(custom)))
                    addView(ui.row("Profit at your price", Fmt.money(custom - cost) + "  (" + Fmt.marginPct(custom - cost, custom) + ")",
                        if (custom - cost >= 0) R.color.c_good else R.color.c_bad))
                }
            } else {
                addView(ui.muted("Enter the purchase price to see the recommended customer price."))
            }
        })
    }

    private fun save() {
        val q = Prefs.canonicalQuality(qualityF.text)
        val price = Fmt.parseMoney(priceF.text) ?: 0L
        val qty = Fmt.parseInt(qtyF.text) ?: 0
        var ok = true
        if (selectedModel <= 0L) { modelF.error("Pick a model from your list, or tap “New model”"); ok = false } else modelF.error(null)
        if (q.isEmpty()) { qualityF.error("Choose the screen quality"); ok = false } else qualityF.error(null)
        if (price <= 0L) { priceF.error("Enter the price you paid"); ok = false } else priceF.error(null)
        if (qty <= 0) { qtyF.error("At least 1"); ok = false } else qtyF.error(null)
        if (!ok) return

        val existing = if (purchaseId > 0) db.purchase(purchaseId) else null
        val p = existing ?: Purchase()
        val supplierName = supplierF.text
        p.modelId = selectedModel
        p.quality = q
        p.supplierId = db.findOrCreateSupplier(supplierName)
        p.supplierName = supplierName
        p.price = price
        p.qty = qty
        p.date = date
        p.notes = notesF.text
        db.savePurchase(p)
        db.touchModel(selectedModel)

        val oldCustom = db.customPrice(selectedModel, q)
        val custom = Fmt.parseMoney(customF.text)
        if (custom != null && custom > 0L) db.setCustomPrice(selectedModel, q, custom)
        else if (oldCustom != null && customF.text.isEmpty()) db.setCustomPrice(selectedModel, q, null)

        val rec = Pricing.recommended(requireContext(), price, q)
        val title = db.model(selectedModel)?.title ?: ""
        val savedModel = selectedModel
        when {
            purchaseId > 0 -> {
                ui.toast("Purchase updated")
                main.back()
            }
            argModel > 0 -> {
                ui.toast("Saved · recommended price ${Fmt.money(rec)}")
                main.back()
            }
            else -> {
                val yours = if (custom != null && custom > 0) "\nYour price: ${Fmt.money(custom)}" else ""
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Purchase saved")
                    .setMessage("$title ($q)\nBought at ${Fmt.money(price)} × $qty\n\nRecommended customer price: ${Fmt.money(rec)}$yours")
                    .setPositiveButton("View model") { _, _ ->
                        resetForm()
                        openModel(savedModel)
                    }
                    .setNegativeButton("Add another") { _, _ -> resetForm() }
                    .show()
            }
        }
    }

    /** Clears the form for the next purchase (keeps supplier and date for batch entry). */
    private fun resetForm() {
        selectedModel = 0L
        modelF.text = ""
        qualityF.text = ""
        priceF.text = ""
        qtyF.text = "1"
        notesF.text = ""
        lastCustomPrefill = ""
        customF.text = ""
        listOf(modelF, qualityF, priceF, qtyF).forEach { it.error(null) }
        ui.setOptions(supplierF, db.suppliers().map { it.name })
        updateLive()
    }

    /** Back from "New model": select the model that was just created. */
    override fun onShownAgain() {
        val id = main.pendingModelId ?: return
        main.pendingModelId = null
        val models = db.models()
        titleToId = mapTitles(models)
        ui.setOptions(modelF, models.map { it.title })
        modelF.text = models.firstOrNull { it.id == id }?.title ?: ""
        selectedModel = id
        afterSelection()
    }
}
