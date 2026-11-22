package com.lontum.screenprice

import android.widget.LinearLayout

class SearchScreen : Screen() {
    private var query = ""
    private var results: LinearLayout? = null
    private var models: List<PhoneModel> = emptyList()
    private var stats: Map<Long, List<ItemStats>> = emptyMap()

    override fun title() = "Search models"

    override fun build(root: LinearLayout) {
        models = db.models()
        stats = db.allStats().values.groupBy { it.modelId }

        val f = ui.field("Type a model, brand or part of the name", query)
        f.edit.setSelection(f.edit.text?.length ?: 0)
        f.onChange {
            query = f.text
            show()
        }
        root.addView(f.layout)
        root.addView(ui.outlined("+  Add a new phone model") { open(ModelEditScreen.of(0, query, true)) })

        val r = ui.vbox()
        results = r
        root.addView(r)
        show()
    }

    private fun show() {
        val r = results ?: return
        r.removeAllViews()
        val q = query.trim()

        if (models.isEmpty()) {
            note(r, "Your database is empty. Tap “Add a new phone model” to start.")
            return
        }

        val found = models.filter { Search.matches(it, q) }
        if (found.isEmpty()) {
            r.addView(ui.card {
                addView(ui.text("“$q” is not in your database yet.", 15f, true))
                addView(ui.muted("Only models you have saved are shown here."))
                addView(ui.button("Add “$q” as a new model") { open(ModelEditScreen.of(0, q, true)) })
            })
            return
        }

        val label = if (q.isEmpty()) "${found.size} saved model${if (found.size == 1) "" else "s"}"
        else "${found.size} match${if (found.size == 1) "" else "es"}"
        val lt = ui.muted(label)
        lt.setPadding(ui.dp(2), ui.dp(10), 0, ui.dp(2))
        r.addView(lt)

        for (m in found.take(150)) {
            val sts = stats[m.id] ?: emptyList()
            val qualities = sts.filter { it.latest != null }.map { it.quality }
            val sub = listOf(m.screenType, if (qualities.isEmpty()) "No price yet" else qualities.joinToString(", "))
                .filter { it.isNotBlank() }.joinToString(" · ")
            val minLatest = sts.mapNotNull { it.latest?.price }.minOrNull()
            val rightSub = when {
                minLatest == null -> null
                qualities.size > 1 -> "latest, cheapest quality"
                else -> "latest price"
            }
            r.addView(itemCard(m, sub, if (minLatest == null) "—" else Fmt.money(minLatest), rightSub))
        }
        if (found.size > 150) r.addView(ui.muted("Showing the first 150. Type more to narrow the list."))
    }
}
