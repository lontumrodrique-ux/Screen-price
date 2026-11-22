package com.lontum.screenprice

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.LinearLayout

// ======================= SUPPLIERS LIST =======================

class SuppliersScreen : Screen() {
    override fun title() = "Suppliers"

    override fun build(root: LinearLayout) {
        root.addView(ui.button("+  Add supplier") { open(SupplierEditScreen.of(0)) })
        val suppliers = db.suppliers()
        val purchases = db.purchases()
        if (suppliers.isEmpty()) {
            note(root, "No suppliers yet. They are added here, or automatically when you type a new supplier's name in a purchase.")
            return
        }
        for (s in suppliers) {
            val mine = purchases.filter { it.supplierId == s.id }
            val spent = mine.sumOf { it.price * it.qty }
            root.addView(ui.card({ open(SupplierDetailScreen.of(s.id)) }) {
                addView(ui.text(s.name, 15.5f, true))
                val contact = listOf(s.phone, s.location).filter { it.isNotBlank() }.joinToString(" · ")
                if (contact.isNotEmpty()) addView(ui.muted(contact))
                addView(ui.muted("${mine.size} purchase${if (mine.size == 1) "" else "s"} · spent ${Fmt.money(spent)}"))
            })
        }
    }
}

// ======================= SUPPLIER DETAILS =======================

class SupplierDetailScreen : Screen() {
    companion object {
        fun of(id: Long) = SupplierDetailScreen().apply { arguments = Bundle().apply { putLong("id", id) } }
    }

    private val supplierId: Long get() = args().getLong("id")

    override fun title() = "Supplier"

    override fun build(root: LinearLayout) {
        val s = db.supplier(supplierId)
        if (s == null) {
            note(root, "This supplier is no longer saved.")
            return
        }
        val models = db.models().associateBy { it.id }
        val mine = db.purchasesForSupplier(s.id)

        root.addView(ui.card {
            addView(ui.heading(s.name))
            if (s.phone.isNotBlank()) addView(ui.row("Phone", s.phone))
            if (s.location.isNotBlank()) addView(ui.row("Location", s.location))
            if (s.notes.isNotBlank()) addView(ui.muted(s.notes, 14f))
            addView(ui.row("Purchases", mine.size.toString()))
            addView(ui.row("Screens bought", mine.sumOf { it.qty }.toString()))
            addView(ui.row("Total spent", Fmt.money(mine.sumOf { it.price * it.qty })))
        })
        if (s.phone.isNotBlank()) {
            root.addView(ui.buttonRow(
                ui.outlined("Call") { startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + s.phone.trim()))) },
                ui.outlined("WhatsApp") { whatsApp(s.phone) }
            ))
        }
        root.addView(ui.buttonRow(
            ui.outlined("Edit") { open(SupplierEditScreen.of(s.id)) },
            ui.outlined("Delete") {
                confirm("Delete ${s.name}?", "The supplier is removed from your list. Their purchase records and prices are kept.", "Delete") {
                    db.deleteSupplier(s.id)
                    ui.toast("Supplier deleted")
                    main.back()
                }
            }
        ))

        // Price comparison against other suppliers (latest prices, same model and quality)
        root.addView(ui.section("Price comparison"))
        val stats = db.allStats().values.filter { st -> st.purchases.any { it.supplierId == s.id } }
        if (stats.isEmpty()) note(root, "No purchases from this supplier yet.")
        for (st in stats) {
            val m = models[st.modelId] ?: continue
            val myLatest = st.purchases.first { it.supplierId == s.id }
            val bySupplier = st.purchases.groupBy { it.supplierName.ifBlank { "No supplier" } }.mapValues { it.value.first().price }
            val best = bySupplier.minByOrNull { it.value }
            root.addView(ui.card({ openModel(m.id) }) {
                val r = ui.hbox()
                r.addView(ui.text("${m.title} (${st.quality})", 14.5f, true), ui.lp(0, WRAP, 1f, end = 8))
                r.addView(ui.text(Fmt.money(myLatest.price), 14.5f, true))
                addView(r)
                when {
                    bySupplier.size < 2 || best == null -> addView(ui.muted("Only bought from this supplier"))
                    myLatest.price <= best.value -> addView(ui.text("Best price", 13.5f, true, R.color.c_good))
                    else -> addView(ui.text("${Fmt.money(myLatest.price - best.value)} more than ${best.key} (${Fmt.money(best.value)})", 13.5f, false, R.color.c_bad))
                }
            })
        }

        root.addView(ui.section("Purchases from ${s.name}"))
        if (mine.isEmpty()) root.addView(ui.muted("None yet."))
        for (p in mine) {
            val name = models[p.modelId]?.title ?: "(deleted model)"
            root.addView(ui.card {
                addView(ui.row("$name (${p.quality})", Fmt.money(p.price)))
                addView(ui.muted("${Fmt.date(p.date)} · qty ${p.qty}" + if (p.notes.isNotBlank()) " · ${p.notes}" else ""))
            })
        }
    }

    private fun whatsApp(phone: String) {
        var d = phone.filter { it.isDigit() }
        if (d.startsWith("00")) d = d.substring(2)
        if (d.length == 9) d = "237$d"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$d")))
        } catch (e: Exception) {
            ui.toast("WhatsApp could not be opened")
        }
    }
}

// ======================= ADD / EDIT SUPPLIER =======================

class SupplierEditScreen : Screen() {
    companion object {
        fun of(id: Long) = SupplierEditScreen().apply { arguments = Bundle().apply { putLong("id", id) } }
    }

    override val liveRefresh = false
    private val supplierId: Long get() = args().getLong("id")

    override fun title() = if (supplierId > 0) "Edit supplier" else "New supplier"

    override fun build(root: LinearLayout) {
        val s = (if (supplierId > 0) db.supplier(supplierId) else null) ?: Supplier()
        val name = ui.field("Supplier's name", s.name, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val phone = ui.field("Telephone number", s.phone, InputType.TYPE_CLASS_PHONE)
        val location = ui.field("Location", s.location, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        val notes = ui.field("Notes (optional)", s.notes, lines = 3)
        for (f in listOf(name, phone, location, notes)) root.addView(f.layout)
        root.addView(ui.button("Save supplier") {
            val n = name.text
            val other = db.supplierByName(n)
            when {
                n.isEmpty() -> name.error("Enter the supplier's name")
                other != null && other.id != s.id -> name.error("A supplier with this name already exists")
                else -> {
                    s.name = n
                    s.phone = phone.text
                    s.location = location.text
                    s.notes = notes.text
                    db.saveSupplier(s)
                    ui.toast("Supplier saved")
                    main.back()
                }
            }
        })
    }
}
