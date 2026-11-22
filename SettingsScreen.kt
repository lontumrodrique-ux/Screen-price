package com.lontum.screenprice

import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SwitchCompat

class SettingsScreen : Screen() {
    override val liveRefresh = false
    override fun title() = "Settings"

    private var testCost: Field? = null
    private var testQuality: Field? = null
    private var testResult: TextView? = null

    private fun toast(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()

    private val backupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) {
            val ctx = requireContext()
            try {
                DataIO.backup(ctx, uri)
                toast(ctx, "Backup saved")
            } catch (e: Exception) {
                toast(ctx, "Backup failed: ${e.message}")
            }
        }
    }

    private val restoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ctx = requireContext()
            try {
                DataIO.restore(ctx, uri)
                toast(ctx, "Your data has been restored")
                Prefs.applyTheme(ctx)
                (activity as? MainActivity)?.openRoot(MainActivity.NAV_SET)
            } catch (e: Exception) {
                toast(ctx, "Restore failed: ${e.message}")
            }
        }
    }

    override fun build(root: LinearLayout) {
        val ctx = requireContext()

        // ---------- Pricing method ----------
        root.addView(ui.section("Pricing method"))
        val group = RadioGroup(ctx)
        val rbPct = RadioButton(ctx)
        rbPct.id = View.generateViewId()
        rbPct.text = "Percentage markup (purchase price + %)"
        rbPct.setTextColor(ui.c(R.color.c_text))
        val rbFix = RadioButton(ctx)
        rbFix.id = View.generateViewId()
        rbFix.text = "Fixed profit (purchase price + amount)"
        rbFix.setTextColor(ui.c(R.color.c_text))
        group.addView(rbPct)
        group.addView(rbFix)
        group.check(if (Prefs.method(ctx) == Prefs.PERCENT) rbPct.id else rbFix.id)
        group.setOnCheckedChangeListener { _, checked ->
            Prefs.setMethod(ctx, if (checked == rbPct.id) Prefs.PERCENT else Prefs.FIXED)
            updateTest()
        }
        root.addView(ui.card { addView(group) })

        val pctF = ui.field("Default markup (%)", Fmt.plain(Prefs.defaultPct(ctx)), InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        pctF.onChange {
            val v = Fmt.parseDecimal(pctF.text)
            if (v != null && v >= 0) {
                Prefs.setDefaultPct(ctx, v)
                pctF.error(null)
            } else pctF.error("Enter a number, e.g. 30")
            updateTest()
        }
        val fixF = ui.numberField("Default fixed profit (FCFA)", Prefs.defaultFixed(ctx).toString())
        fixF.onChange {
            val v = Fmt.parseMoney(fixF.text)
            if (v != null) {
                Prefs.setDefaultFixed(ctx, v)
                fixF.error(null)
            } else fixF.error("Enter an amount")
            updateTest()
        }
        val stepIndex = Prefs.ROUND_STEPS.indexOf(Prefs.roundStep(ctx)).coerceAtLeast(0)
        val roundF = ui.dropdown("Rounding", Prefs.ROUND_LABELS, Prefs.ROUND_LABELS[stepIndex], editable = false)
        roundF.onChange {
            val i = Prefs.ROUND_LABELS.indexOf(roundF.text)
            if (i >= 0) Prefs.setRoundStep(ctx, Prefs.ROUND_STEPS[i])
            updateTest()
        }
        val dirLabels = listOf("To the nearest amount", "Always round up")
        val dirF = ui.dropdown("Rounding direction", dirLabels, if (Prefs.roundUp(ctx)) dirLabels[1] else dirLabels[0], editable = false)
        dirF.onChange {
            Prefs.setRoundUp(ctx, dirF.text == dirLabels[1])
            updateTest()
        }
        for (f in listOf(pctF, fixF, roundF, dirF)) root.addView(f.layout)

        // ---------- Test calculator ----------
        root.addView(ui.section("Test your settings"))
        val tc = ui.numberField("Type any purchase price to test")
        val tq = ui.dropdown("Screen quality", Prefs.QUALITIES, "")
        val tr = ui.text("", 15f, true, R.color.c_primary_text)
        tr.setPadding(ui.dp(4), ui.dp(10), 0, ui.dp(4))
        testCost = tc
        testQuality = tq
        testResult = tr
        tc.onChange { updateTest() }
        tq.onChange { updateTest() }
        root.addView(ui.card {
            addView(tc.layout)
            addView(tq.layout)
            addView(tr)
        })
        updateTest()

        // ---------- Margins per quality ----------
        root.addView(ui.section("Different margins per screen quality"))
        root.addView(ui.muted("Leave a box empty to use the default above. Example: a smaller markup on Original screens and a bigger one on Copy screens.", 13f))
        for (q in Prefs.QUALITIES) {
            root.addView(ui.card {
                addView(ui.text(q, 15f, true))
                val r = LinearLayout(ctx)
                r.orientation = LinearLayout.HORIZONTAL
                val p = ui.field("Markup %", Prefs.qualityPct(ctx, q)?.let { Fmt.plain(it) } ?: "", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
                val f = ui.numberField("Fixed profit", Prefs.qualityFixed(ctx, q)?.toString() ?: "")
                p.onChange {
                    Prefs.setQualityPct(ctx, q, if (p.text.isEmpty()) null else Fmt.parseDecimal(p.text))
                    updateTest()
                }
                f.onChange {
                    Prefs.setQualityFixed(ctx, q, if (f.text.isEmpty()) null else Fmt.parseMoney(f.text))
                    updateTest()
                }
                r.addView(p.layout, ui.lp(0, WRAP, 1f, end = 8))
                r.addView(f.layout, ui.lp(0, WRAP, 1f))
                addView(r)
            })
        }

        // ---------- Stock ----------
        root.addView(ui.section("Stock"))
        val sw = SwitchCompat(ctx)
        sw.text = "Track stock (inventory)"
        sw.setTextColor(ui.c(R.color.c_text))
        sw.isChecked = Prefs.stockOn(ctx)
        sw.setOnCheckedChangeListener { _, b -> Prefs.setStockOn(ctx, b) }
        val lowF = ui.numberField("Low-stock warning at (number of screens)", Prefs.lowStock(ctx).toString())
        lowF.onChange { Fmt.parseInt(lowF.text)?.let { Prefs.setLowStock(ctx, it) } }
        root.addView(ui.card {
            addView(sw)
            addView(lowF.layout)
        })

        // ---------- Appearance ----------
        root.addView(ui.section("Appearance"))
        val themeIndex = when (Prefs.theme(ctx)) {
            "light" -> 1
            "dark" -> 2
            else -> 0
        }
        val themeF = ui.dropdown("Theme", Prefs.THEME_LABELS, Prefs.THEME_LABELS[themeIndex], editable = false)
        themeF.onChange {
            val v = when (themeF.text) {
                Prefs.THEME_LABELS[1] -> "light"
                Prefs.THEME_LABELS[2] -> "dark"
                else -> "system"
            }
            if (v != Prefs.theme(ctx)) {
                Prefs.setTheme(ctx, v)
                Prefs.setReopenSettings(ctx, true)
                Prefs.applyTheme(ctx)
                // If the screen did not need to restart, clear the "reopen settings" note.
                view?.postDelayed({ Prefs.consumeReopenSettings(ctx) }, 1500)
            }
        }
        root.addView(themeF.layout)

        // ---------- Data ----------
        root.addView(ui.section("Your data"))
        root.addView(ui.outlined("Export all data to Excel (CSV)") { DataIO.shareAll(ctx) })
        root.addView(ui.outlined("Export price list (CSV)") { DataIO.sharePriceList(ctx) })
        root.addView(ui.outlined("Backup to a file") { backupLauncher.launch("screen-prices-backup-${Fmt.stamp()}.db") })
        root.addView(ui.outlined("Restore from a backup file") {
            confirm("Restore from backup?", "Everything now in the app will be replaced by the data in the backup file.", "Choose file") {
                restoreLauncher.launch(arrayOf("*/*"))
            }
        })
        root.addView(ui.card {
            addView(ui.text("Moving to a new phone", 14.5f, true))
            addView(ui.muted("1. Tap “Backup to a file” and save it to Google Drive, or send it to yourself on WhatsApp.\n2. Install this app on the new phone.\n3. Tap “Restore from a backup file” and pick that file.", 13.5f))
            val n = ui.muted("Everything is saved on this phone and works without internet. If Google backup is on in your phone settings, Android also backs up this app to your Google account.", 12.5f)
            n.setPadding(0, ui.dp(8), 0, 0)
            addView(n)
        })

        // ---------- About ----------
        val version = try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
        val about = ui.muted("${getString(R.string.app_name)} $version · ${getString(R.string.shop_name)}", 12.5f)
        about.setPadding(ui.dp(2), ui.dp(20), 0, 0)
        root.addView(about)
    }

    private fun updateTest() {
        val tr = testResult ?: return
        val ctx = context ?: return
        val cost = Fmt.parseMoney(testCost?.text ?: "") ?: 0L
        if (cost <= 0L) {
            tr.text = "Type a price above to see what you would charge."
            return
        }
        val q = Prefs.canonicalQuality(testQuality?.text ?: "")
        val rec = Pricing.recommended(ctx, cost, q)
        tr.text = "Customer price: ${Fmt.money(rec)}\nProfit: ${Fmt.money(rec - cost)} (${Fmt.marginPct(rec - cost, rec)})\n${Pricing.methodLabel(ctx, q)}"
    }
}
