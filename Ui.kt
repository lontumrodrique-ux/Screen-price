package com.lontum.screenprice

import android.app.DatePickerDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** A labelled input box. */
class Field(val layout: TextInputLayout, val edit: EditText) {
    var text: String
        get() = edit.text?.toString()?.trim() ?: ""
        set(v) {
            val e = edit
            if (e is AutoCompleteTextView) e.setText(v, false) else e.setText(v)
        }

    fun onChange(f: () -> Unit) {
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                f()
            }
        })
    }

    fun error(msg: String?) {
        layout.error = msg
    }
}

/** Small toolkit that builds every screen in code, so light and dark mode always match. */
class Ui(val ctx: Context) {
    private val density = ctx.resources.displayMetrics.density

    fun dp(v: Int): Int = (v * density + 0.5f).toInt()
    fun c(res: Int): Int = ContextCompat.getColor(ctx, res)

    fun lp(w: Int = MATCH, h: Int = WRAP, weight: Float = 0f, top: Int = 0, bottom: Int = 0, end: Int = 0): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h, weight)
        p.topMargin = dp(top)
        p.bottomMargin = dp(bottom)
        p.marginEnd = dp(end)
        return p
    }

    fun vbox(): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.VERTICAL
        return l
    }

    fun hbox(): LinearLayout {
        val l = LinearLayout(ctx)
        l.orientation = LinearLayout.HORIZONTAL
        l.gravity = Gravity.CENTER_VERTICAL
        return l
    }

    fun text(s: CharSequence, size: Float = 15f, bold: Boolean = false, color: Int = R.color.c_text): TextView {
        val t = TextView(ctx)
        t.text = s
        t.textSize = size
        t.setTextColor(c(color))
        if (bold) t.setTypeface(t.typeface, Typeface.BOLD)
        return t
    }

    fun heading(s: CharSequence): TextView = text(s, 21f, true)

    fun muted(s: CharSequence, size: Float = 13.5f): TextView = text(s, size, false, R.color.c_muted)

    fun section(s: String): TextView {
        val t = text(s.uppercase(), 12.5f, true, R.color.c_primary_text)
        t.letterSpacing = 0.06f
        t.setPadding(dp(2), dp(18), 0, dp(4))
        return t
    }

    fun card(onClick: (() -> Unit)? = null, block: LinearLayout.() -> Unit): MaterialCardView {
        val card = MaterialCardView(ctx)
        card.radius = dp(12).toFloat()
        card.cardElevation = 0f
        card.setCardBackgroundColor(c(R.color.c_card))
        card.strokeColor = c(R.color.c_line)
        card.strokeWidth = dp(1)
        val inner = vbox()
        inner.setPadding(dp(14), dp(12), dp(14), dp(12))
        inner.block()
        card.addView(inner, FrameLayout.LayoutParams(MATCH, WRAP))
        if (onClick != null) {
            card.isClickable = true
            card.isFocusable = true
            card.setOnClickListener { onClick() }
        }
        card.layoutParams = lp(top = 5, bottom = 5)
        return card
    }

    fun row(label: String, value: String, valueColor: Int = R.color.c_text, bold: Boolean = true): LinearLayout {
        val r = hbox()
        r.setPadding(0, dp(3), 0, dp(3))
        r.addView(text(label, 14f, false, R.color.c_muted), lp(0, WRAP, 1f, end = 10))
        val v = text(value, 14.5f, bold, valueColor)
        v.gravity = Gravity.END
        r.addView(v)
        return r
    }

    fun button(label: String, onClick: () -> Unit): MaterialButton {
        val b = MaterialButton(ctx)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        b.layoutParams = lp(top = 4)
        return b
    }

    fun outlined(label: String, onClick: () -> Unit): MaterialButton {
        val b = MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        b.layoutParams = lp(top = 4)
        return b
    }

    fun buttonRow(vararg buttons: MaterialButton): LinearLayout {
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        buttons.forEachIndexed { i, b ->
            r.addView(b, lp(0, WRAP, 1f, end = if (i < buttons.size - 1) 8 else 0))
        }
        r.layoutParams = lp(top = 4)
        return r
    }

    fun divider(): View {
        val v = View(ctx)
        v.setBackgroundColor(c(R.color.c_line))
        v.layoutParams = lp(MATCH, dp(1), top = 8, bottom = 8)
        return v
    }

    fun space(h: Int): View {
        val v = View(ctx)
        v.layoutParams = lp(MATCH, dp(h))
        return v
    }

    fun badge(s: String, bg: Int = R.color.c_chip, fg: Int = R.color.c_primary_text): TextView {
        val t = text(s, 12.5f, true, fg)
        t.setPadding(dp(10), dp(3), dp(10), dp(3))
        val d = GradientDrawable()
        d.cornerRadius = dp(20).toFloat()
        d.setColor(c(bg))
        t.background = d
        return t
    }

    fun tile(label: String, value: String, sub: String? = null, valueColor: Int = R.color.c_text, onClick: (() -> Unit)? = null): MaterialCardView =
        card(onClick) {
            addView(text(label, 12.5f, false, R.color.c_muted))
            addView(text(value, 18f, true, valueColor))
            if (sub != null) addView(text(sub, 12f, false, R.color.c_muted))
        }

    fun grid(views: List<View>, cols: Int = 2): LinearLayout {
        val box = vbox()
        views.chunked(cols).forEach { rowViews ->
            val r = LinearLayout(ctx)
            r.orientation = LinearLayout.HORIZONTAL
            rowViews.forEachIndexed { i, v ->
                r.addView(v, lp(0, WRAP, 1f, top = 4, bottom = 4, end = if (i < cols - 1) 8 else 0))
            }
            repeat(cols - rowViews.size) { r.addView(View(ctx), lp(0, WRAP, 1f)) }
            box.addView(r)
        }
        return box
    }

    fun bar(fraction: Float, colorRes: Int): LinearLayout {
        val f = fraction.coerceIn(0f, 1f)
        val r = LinearLayout(ctx)
        r.orientation = LinearLayout.HORIZONTAL
        val fill = View(ctx)
        val d = GradientDrawable()
        d.cornerRadius = dp(4).toFloat()
        d.setColor(c(colorRes))
        fill.background = d
        r.addView(fill, LinearLayout.LayoutParams(0, dp(10), if (f <= 0f) 0.0001f else f))
        r.addView(View(ctx), LinearLayout.LayoutParams(0, dp(10), 1f - f + 0.0001f))
        return r
    }

    fun field(
        hint: String,
        value: String = "",
        type: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES,
        lines: Int = 1
    ): Field {
        val til = TextInputLayout(ctx)
        til.hint = hint
        val et = TextInputEditText(til.context)
        if (lines > 1) {
            et.inputType = type or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            et.minLines = lines
            et.gravity = Gravity.TOP or Gravity.START
        } else {
            et.inputType = type
            et.maxLines = 1
        }
        et.setText(value)
        til.addView(et, LinearLayout.LayoutParams(MATCH, WRAP))
        til.layoutParams = lp(top = 6)
        return Field(til, et)
    }

    fun numberField(hint: String, value: String = ""): Field = field(hint, value, InputType.TYPE_CLASS_NUMBER)

    /** A box with a list to pick from. When [editable] is true you can also type your own value. */
    fun dropdown(hint: String, options: List<String>, value: String = "", editable: Boolean = true): Field {
        val til = TextInputLayout(ctx)
        til.hint = hint
        val ac = MaterialAutoCompleteTextView(til.context)
        ac.inputType = if (editable) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS else InputType.TYPE_NULL
        ac.threshold = 1
        ac.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, options))
        ac.setText(value, false)
        ac.setOnClickListener { ac.showDropDown() }
        ac.setOnFocusChangeListener { _, has -> if (has && ac.windowToken != null) ac.showDropDown() }
        til.addView(ac, LinearLayout.LayoutParams(MATCH, WRAP))
        til.endIconMode = TextInputLayout.END_ICON_DROPDOWN_MENU
        til.layoutParams = lp(top = 6)
        return Field(til, ac)
    }

    fun setOptions(f: Field, options: List<String>) {
        val e = f.edit
        if (e is AutoCompleteTextView) e.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, options))
    }

    /** A date box that opens a calendar when tapped. */
    fun dateField(hint: String, current: () -> Long, onPick: (Long) -> Unit): Field {
        val f = field(hint, Fmt.date(current()), InputType.TYPE_NULL)
        f.edit.isFocusable = false
        f.edit.isClickable = true
        f.edit.keyListener = null
        f.edit.setOnClickListener {
            val cal = Calendar.getInstance()
            cal.timeInMillis = current()
            DatePickerDialog(ctx, { _, y, m, d ->
                val r = Calendar.getInstance()
                r.clear()
                r.set(y, m, d)
                val ms = Fmt.startOfDay(r.timeInMillis)
                f.text = Fmt.date(ms)
                onPick(ms)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        return f
    }

    fun toast(s: String) {
        Toast.makeText(ctx, s, Toast.LENGTH_SHORT).show()
    }
}
