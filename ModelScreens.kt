package com.lontum.screenprice

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.LinearLayout

// ======================= MODEL DETAILS =======================

class ModelDetailScreen : Screen() {
    companion object {
        fun of(id: Long) = ModelDetailScreen().apply { arguments = Bundle().apply { putLong("id", id) } }
    }

    private val modelId: Long get() = args().getLong("id")

    override fun title() = "Model details"

    override fun build(root: LinearLayout) {
        val ctx = requireContext()
        val m = db.model(modelId)
        if (m == null) {
            note(root, "This model is no longer in your database.")
            return
        }
        val stockOn = Prefs.stockOn(ctx)
        val low = Prefs.lowStock(ctx)
        val items = db.allStats().values.filter { it.modelId == m.id }.sortedWith(
            compareByDescending<ItemStats> { it.latest?.date ?: 0L }.thenByDescending { it.latest?.created ?: 0L }
        )

        // Phone information
        root.addView(ui.card {
            addView(ui.heading(m.title))
            if (m.screenType.isNotBlank()) addView(ui.row("Screen type", m.screenType))
            if (m.fingerprint.isNotBlank()) addView(ui.row("Fingerprint", m.fingerprint))
            if (m.frame.isNotBlank()) addView(ui.row("Frame", m.frame))
            if (m.notes.isNotBlank()) {
                val n = ui.muted(m.notes, 14f)
                n.setPadding(0, ui.dp(6), 0, 0)
                addView(n)
            }
        })
        root.addView(ui.buttonRow(
            ui.button("Add purchase") { open(PurchaseFormScreen.of(m.id, "")) },
            ui.outlined("Record sale") { open(SaleFormScreen.of(m.id, "")) }
        ))
        root.addView(ui.buttonRow(
            ui.outlined("Edit model") { open(ModelEditScreen.of(m.id, "", false)) },
            ui.outlined("Delete") { deleteModel(m) }
        ))

        // One card per screen quality
        root.addView(ui.section("Screen prices"))
        if (items.isEmpty()) note(root, "No screen prices yet. Tap “Add purchase” to record what you paid.")
        for (st in items) {
            root.addView(ui.card {
                val head = ui.hbox()
                head.addView(ui.badge(st.quality))
                head.addView(View(ctx), ui.lp(0, 1, 1f))
                if (stockOn) {
                    val a = st.available
                    head.addView(ui.text("In stock: $a", 14f, true, if (a <= low) R.color.c_bad else R.color.c_good))
                }
                addView(head)

                val latest = st.latest
                if (latest != null) {
                    addView(ui.space(6))
                    addView(ui.row("Latest price", Fmt.money(latest.price)))
                    addView(ui.muted(listOf(latest.supplierName.ifBlank { "No supplier" }, Fmt.date(latest.date)).joinToString(" · ")))
                    val lowest = st.lowest
                    if (lowest != null) {
                        addView(ui.row("Lowest price", Fmt.money(lowest.price) + if (lowest.supplierName.isNotBlank()) " · ${lowest.supplierName}" else ""))
                    }
                    addView(ui.row("Average price", Fmt.money(st.average)))
                    addView(ui.divider())

                    val rec = Pricing.recommended(ctx, latest.price, st.quality)
                    addView(ui.row("Recommended price", Fmt.money(rec), R.color.c_primary_text))
                    addView(ui.muted(Pricing.methodLabel(ctx, st.quality), 12.5f))
                    val custom = st.customPrice
                    addView(ui.row(
                        "Your price",
                        if (custom != null) Fmt.money(custom) else "Not set (uses recommended)",
                        if (custom != null) R.color.c_text else R.color.c_muted,
                        custom != null
                    ))
                    val sell = custom ?: rec
                    val profit = sell - latest.price
                    addView(ui.row(
                        "Expected profit",
                        Fmt.money(profit) + "  (" + Fmt.marginPct(profit, sell) + ")",
                        if (profit >= 0) R.color.c_good else R.color.c_bad
                    ))

                    // Supplier comparison for this quality
                    val bySupplier = st.purchases.groupBy { it.supplierName.ifBlank { "No supplier" } }
                    if (bySupplier.size > 1) {
                        val h = ui.text("Compare suppliers (latest price)", 13f, true, R.color.c_muted)
                        h.setPadding(0, ui.dp(10), 0, ui.dp(2))
                        addView(h)
                        val rows = bySupplier.map { (name, list) -> Triple(name, list.first().price, list.minOf { it.price }) }
                            .sortedBy { it.second }
                        rows.forEachIndexed { i, (name, last, lowP) ->
                            val extra = if (lowP < last) "  (once ${Fmt.num(lowP)})" else ""
                            addView(ui.row(
                                (if (i == 0) "★ " else "") + name,
                                Fmt.money(last) + extra,
                                if (i == 0) R.color.c_good else R.color.c_text,
                                i == 0
                            ))
                        }
                    }
                } else {
                    addView(ui.muted("No purchase recorded for this quality yet."))
                }
                if (stockOn && (st.soldQty > 0 || st.lostQty > 0 || st.boughtQty > 0)) {
                    addView(ui.muted("Bought ${st.boughtQty} · Sold ${st.soldQty} · Damaged/returned ${st.lostQty}", 12.5f))
                }
                addView(ui.buttonRow(
                    ui.outlined("Set your price") {
                        askNumber("Your customer price (${st.quality})", "Price in FCFA", st.customPrice, "Use recommended") { v ->
                            db.setCustomPrice(m.id, st.quality, v)
                            render()
                        }
                    },
                    ui.outlined("Add purchase") { open(PurchaseFormScreen.of(m.id, st.quality)) }
                ))
            })
        }

        // Full purchase history (never overwritten)
        root.addView(ui.section("Purchase history"))
        val hist = db.purchasesForModel(m.id)
        if (hist.isEmpty()) root.addView(ui.muted("No purchases yet."))
        for (p in hist) {
            root.addView(ui.card({ purchaseActions(p) }) {
                val r = ui.hbox()
                r.addView(ui.text("${Fmt.date(p.date)} · ${p.quality}", 14.5f, true), ui.lp(0, WRAP, 1f, end = 8))
                r.addView(ui.text(Fmt.money(p.price), 14.5f, true))
                addView(r)
                val extra = if (p.notes.isNotBlank()) " · ${p.notes}" else ""
                addView(ui.muted("${p.supplierName.ifBlank { "No supplier" }} · qty ${p.qty}$extra"))
            })
        }

        // Stock changes
        val adjs = db.adjustmentsForModel(m.id)
        if (stockOn && adjs.isNotEmpty()) {
            root.addView(ui.section("Stock changes"))
            for (a in adjs) {
                val sign = if (a.delta > 0) "+${a.delta}" else a.delta.toString()
                root.addView(ui.card {
                    addView(ui.row("${Fmt.date(a.date)} · ${a.quality}", "${Adj.label(a.kind)} $sign", R.color.c_text, false))
                    if (a.notes.isNotBlank()) addView(ui.muted(a.notes))
                })
            }
        }
    }

    private fun purchaseActions(p: Purchase) {
        choose("Purchase on ${Fmt.date(p.date)}", listOf("Edit this purchase", "Delete this purchase")) { w ->
            if (w == 0) {
                open(PurchaseFormScreen.edit(p.id))
            } else {
                confirm(
                    "Delete this purchase?",
                    "${p.qty} × ${Fmt.money(p.price)} from ${p.supplierName.ifBlank { "no supplier" }} on ${Fmt.date(p.date)}. Your other price records are kept.",
                    "Delete"
                ) {
                    db.deletePurchase(p.id)
                    render()
                }
            }
        }
    }

    private fun deleteModel(m: PhoneModel) {
        val p = db.purchasesForModel(m.id).size
        val s = db.sales().count { it.modelId == m.id }
        confirm(
            "Delete ${m.title}?",
            "This deletes the model with its $p purchase record(s), $s sale(s) and stock records. This cannot be undone.",
            "Delete"
        ) {
            db.deleteModel(m.id)
            ui.toast("Model deleted")
            main.back()
        }
    }
}

// ======================= ADD / EDIT MODEL =======================

class ModelEditScreen : Screen() {
    companion object {
        fun of(id: Long, prefill: String, openAfter: Boolean) = ModelEditScreen().apply {
            arguments = Bundle().apply {
                putLong("id", id)
                putString("prefill", prefill)
                putBoolean("openAfter", openAfter)
            }
        }
    }

    override val liveRefresh = false
    private val modelId: Long get() = args().getLong("id")

    override fun title() = if (modelId > 0) "Edit model" else "New phone model"

    override fun build(root: LinearLayout) {
        val m = (if (modelId > 0) db.model(modelId) else null)
            ?: PhoneModel().also { prefill(it, args().getString("prefill") ?: "") }

        val words = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        val brand = ui.dropdown("Brand", Prefs.BRANDS, m.brand)
        val model = ui.field("Exact model (e.g. Camon 20)", m.model, words)
        val variant = ui.field("Model number or variant (optional)", m.variant, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS)
        val screenType = ui.dropdown("Screen type", Prefs.SCREEN_TYPES, m.screenType)
        val fingerprint = ui.dropdown("Fingerprint compatibility", Prefs.FINGERPRINT, m.fingerprint)
        val frame = ui.dropdown("Frame availability", Prefs.FRAME, m.frame)
        val notes = ui.field("Notes (optional)", m.notes, lines = 3)
        for (f in listOf(brand, model, variant, screenType, fingerprint, frame, notes)) root.addView(f.layout)

        val hint = ui.muted("Screen quality (Original, Incell, OLED…) is chosen each time you record a purchase, so every quality keeps its own prices.", 13f)
        hint.setPadding(0, ui.dp(10), 0, 0)
        root.addView(hint)

        root.addView(ui.button(if (m.id > 0) "Save changes" else "Save model") {
            var ok = true
            if (brand.text.isEmpty()) { brand.error("Enter the brand"); ok = false } else brand.error(null)
            if (model.text.isEmpty()) { model.error("Enter the model"); ok = false } else model.error(null)
            if (ok) {
                val dup = db.findDuplicate(brand.text, model.text, variant.text, m.id)
                if (dup != null) {
                    confirm("Already saved", "${dup.title} is already in your database.", "Open it") {
                        main.replaceTop(ModelDetailScreen.of(dup.id))
                    }
                } else {
                    m.brand = brand.text
                    m.model = model.text
                    m.variant = variant.text
                    m.screenType = screenType.text
                    m.fingerprint = fingerprint.text
                    m.frame = frame.text
                    m.notes = notes.text
                    val isNew = m.id == 0L
                    val id = db.saveModel(m)
                    ui.toast(if (isNew) "Model saved" else "Changes saved")
                    if (args().getBoolean("openAfter")) {
                        main.replaceTop(ModelDetailScreen.of(id))
                    } else {
                        if (isNew) main.pendingModelId = id
                        main.back()
                    }
                }
            }
        })
    }

    /** "Techno spark 10" → brand Tecno, model "spark 10". */
    private fun prefill(m: PhoneModel, text: String) {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return
        val first = Search.tokens(words[0]).firstOrNull() ?: ""
        val brand = Prefs.BRANDS.firstOrNull { Search.norm(it).replace(" ", "") == first }
        if (brand != null && words.size > 1) {
            m.brand = brand
            m.model = words.drop(1).joinToString(" ") { cap(it) }
        } else if (brand != null) {
            m.brand = brand
        } else {
            m.model = words.joinToString(" ") { cap(it) }
        }
    }

    private fun cap(w: String): String = if (w.isEmpty()) w else w.substring(0, 1).uppercase() + w.substring(1)
}
