package com.lontum.screenprice

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Base for every screen: a scrolling page built in code and rebuilt when shown again. */
abstract class Screen : Fragment() {
    protected lateinit var ui: Ui
    protected val db: Db get() = Db.get(requireContext())
    protected val main: MainActivity get() = requireActivity() as MainActivity
    private var holder: FrameLayout? = null

    abstract fun title(): String
    abstract fun build(root: LinearLayout)

    /** Forms keep what you typed (false); lists and details refresh (true). */
    open val liveRefresh: Boolean = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        ui = Ui(requireContext())
        val scroll = ScrollView(requireContext())
        scroll.isFillViewport = true
        scroll.setBackgroundColor(ui.c(R.color.c_bg))
        val h = FrameLayout(requireContext())
        scroll.addView(h, FrameLayout.LayoutParams(MATCH, WRAP))
        holder = h
        render()
        return scroll
    }

    override fun onDestroyView() {
        super.onDestroyView()
        holder = null
    }

    fun render() {
        val h = holder ?: return
        h.removeAllViews()
        val root = ui.vbox()
        root.setPadding(ui.dp(16), ui.dp(10), ui.dp(16), ui.dp(80))
        build(root)
        h.addView(root, FrameLayout.LayoutParams(MATCH, WRAP))
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) main.setScreenTitle(title())
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            main.setScreenTitle(title())
            if (liveRefresh) render() else onShownAgain()
        }
    }

    open fun onShownAgain() {}

    protected fun args(): Bundle = arguments ?: Bundle()
    protected fun open(screen: Screen) = main.push(screen)
    protected fun openModel(id: Long) = main.push(ModelDetailScreen.of(id))

    protected fun confirm(title: String, message: String, yes: String, action: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(yes) { _, _ -> action() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    protected fun choose(title: String, options: List<String>, action: (Int) -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setItems(options.toTypedArray<CharSequence>()) { _, which -> action(which) }
            .show()
    }

    protected fun askNumber(title: String, hint: String, initial: Long?, clearLabel: String?, onResult: (Long?) -> Unit) {
        val box = ui.vbox()
        box.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0)
        val f = ui.numberField(hint, initial?.toString() ?: "")
        box.addView(f.layout)
        val b = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Save") { _, _ -> onResult(Fmt.parseMoney(f.text)) }
            .setNegativeButton("Cancel", null)
        if (clearLabel != null) b.setNeutralButton(clearLabel) { _, _ -> onResult(null) }
        b.show()
    }

    protected fun askQtyNote(title: String, onResult: (Int, String) -> Unit) {
        val box = ui.vbox()
        box.setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0)
        val q = ui.numberField("Number of screens", "1")
        val n = ui.field("Note (optional)")
        box.addView(q.layout)
        box.addView(n.layout)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val v = Fmt.parseInt(q.text) ?: 0
                if (v > 0) onResult(v, n.text) else ui.toast("Enter a number above 0")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** A tappable row for one model: title and detail on the left, a price on the right. */
    protected fun itemCard(m: PhoneModel, sub: String, right: String, rightSub: String?, onClick: (() -> Unit)? = null): View =
        ui.card(onClick ?: { openModel(m.id) }) {
            val r = ui.hbox()
            val left = ui.vbox()
            left.addView(ui.text(m.title, 15.5f, true))
            if (sub.isNotBlank()) left.addView(ui.muted(sub))
            r.addView(left, ui.lp(0, WRAP, 1f, end = 8))
            val rt = ui.vbox()
            rt.gravity = Gravity.END
            val price = ui.text(right, 15f, true)
            price.gravity = Gravity.END
            rt.addView(price)
            if (rightSub != null) {
                val s = ui.muted(rightSub, 12.5f)
                s.gravity = Gravity.END
                rt.addView(s)
            }
            r.addView(rt)
            addView(r)
        }

    /** A plain card with a message. */
    protected fun note(root: LinearLayout, message: String) {
        root.addView(ui.card { addView(ui.muted(message, 14f)) })
    }
}
