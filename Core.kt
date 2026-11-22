package com.lontum.screenprice

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ======================= DATA =======================

data class Supplier(
    var id: Long = 0,
    var name: String = "",
    var phone: String = "",
    var location: String = "",
    var notes: String = ""
)

data class PhoneModel(
    var id: Long = 0,
    var brand: String = "",
    var model: String = "",
    var variant: String = "",
    var screenType: String = "",
    var fingerprint: String = "",
    var frame: String = "",
    var notes: String = "",
    var updated: Long = 0
) {
    val title: String
        get() = listOf(brand, model, variant).map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
}

data class Purchase(
    var id: Long = 0,
    var modelId: Long = 0,
    var quality: String = "",
    var supplierId: Long? = null,
    var supplierName: String = "",
    var price: Long = 0,
    var qty: Int = 1,
    var date: Long = Fmt.today(),
    var notes: String = "",
    var created: Long = 0
)

data class Sale(
    var id: Long = 0,
    var modelId: Long = 0,
    var quality: String = "",
    var cost: Long = 0,
    var price: Long = 0,
    var qty: Int = 1,
    var date: Long = Fmt.today(),
    var notes: String = "",
    var created: Long = 0
) {
    val revenue: Long get() = price * qty
    val costTotal: Long get() = cost * qty
    val profit: Long get() = revenue - costTotal
}

object Adj {
    const val DAMAGED = "DAMAGED"
    const val RETURNED = "RETURNED"
    const val ADDED = "ADDED"
    const val CORRECTION = "CORRECTION"

    fun label(kind: String): String = when (kind) {
        DAMAGED -> "Damaged"
        RETURNED -> "Returned to supplier"
        ADDED -> "Added back"
        else -> "Count correction"
    }
}

data class Adjustment(
    var id: Long = 0,
    var modelId: Long = 0,
    var quality: String = "",
    var kind: String = Adj.DAMAGED,
    var qty: Int = 0,
    var date: Long = Fmt.today(),
    var notes: String = ""
) {
    /** Effect on stock. A correction stores a signed number. */
    val delta: Int
        get() = when (kind) {
            Adj.DAMAGED, Adj.RETURNED -> -qty
            Adj.ADDED -> qty
            else -> qty
        }
}

/** One screen = one phone model in one quality (e.g. Camon 20 · Incell). */
data class ItemKey(val modelId: Long, val quality: String)

class ItemStats(val modelId: Long, val quality: String) {
    /** Newest first. */
    val purchases = ArrayList<Purchase>()
    var soldQty = 0
    var lostQty = 0
    var adjDelta = 0
    var customPrice: Long? = null

    val latest: Purchase? get() = purchases.firstOrNull()
    val lowest: Purchase? get() = purchases.minByOrNull { it.price }
    val boughtQty: Int get() = purchases.sumOf { it.qty }

    /** Average price per screen, weighted by quantity. */
    val average: Long?
        get() {
            val q = boughtQty
            if (q <= 0) return null
            val total = purchases.sumOf { it.price * it.qty }
            return Math.round(total.toDouble() / q)
        }

    val available: Int get() = boughtQty - soldQty + adjDelta
}

object Stats {
    val newestFirst: Comparator<Purchase> =
        compareByDescending<Purchase> { it.date }.thenByDescending { it.created }.thenByDescending { it.id }

    fun build(
        purchases: List<Purchase>,
        sales: List<Sale>,
        adjustments: List<Adjustment>,
        customs: Map<ItemKey, Long>
    ): LinkedHashMap<ItemKey, ItemStats> {
        val map = LinkedHashMap<ItemKey, ItemStats>()
        fun get(m: Long, q: String): ItemStats = map.getOrPut(ItemKey(m, q)) { ItemStats(m, q) }
        for (p in purchases.sortedWith(newestFirst)) get(p.modelId, p.quality).purchases.add(p)
        for (s in sales) get(s.modelId, s.quality).soldQty += s.qty
        for (a in adjustments) {
            val st = get(a.modelId, a.quality)
            st.adjDelta += a.delta
            if (a.kind == Adj.DAMAGED || a.kind == Adj.RETURNED) st.lostQty += a.qty
        }
        for ((k, v) in customs) get(k.modelId, k.quality).customPrice = v
        return map
    }
}

// ======================= SETTINGS =======================

object Prefs {
    const val PERCENT = "percent"
    const val FIXED = "fixed"

    val QUALITIES = listOf("Original", "OEM", "Incell", "Hard OLED", "Soft OLED", "Copy", "Other")
    val BRANDS = listOf(
        "Tecno", "Infinix", "itel", "Samsung", "iPhone", "Xiaomi", "Redmi", "Oppo", "Vivo",
        "Realme", "Huawei", "Honor", "Nokia", "Google Pixel", "Motorola", "OnePlus", "Other"
    )
    val SCREEN_TYPES = listOf("LCD", "IPS LCD", "TFT", "AMOLED", "Super AMOLED", "OLED", "Other")
    val FINGERPRINT = listOf(
        "In-display fingerprint works",
        "In-display fingerprint does not work",
        "Fingerprint on side or back (not affected)",
        "No fingerprint",
        "Unknown"
    )
    val FRAME = listOf("Available with frame", "Screen only (no frame)", "Both available", "Unknown")
    val ROUND_LABELS = listOf("No rounding", "100 FCFA", "500 FCFA", "1,000 FCFA")
    val ROUND_STEPS = listOf(1, 100, 500, 1000)
    val THEME_LABELS = listOf("Follow phone setting", "Light", "Dark")

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun canonicalQuality(q: String): String {
        val t = q.trim()
        return QUALITIES.firstOrNull { it.equals(t, ignoreCase = true) } ?: t
    }

    fun method(ctx: Context): String = sp(ctx).getString("method", PERCENT) ?: PERCENT
    fun setMethod(ctx: Context, v: String) = sp(ctx).edit().putString("method", v).apply()

    fun defaultPct(ctx: Context): Double = sp(ctx).getString("pct", "30")?.toDoubleOrNull() ?: 30.0
    fun setDefaultPct(ctx: Context, v: Double) = sp(ctx).edit().putString("pct", v.toString()).apply()

    fun defaultFixed(ctx: Context): Long = sp(ctx).getString("fixed", "5000")?.toLongOrNull() ?: 5000L
    fun setDefaultFixed(ctx: Context, v: Long) = sp(ctx).edit().putString("fixed", v.toString()).apply()

    fun qualityPct(ctx: Context, q: String): Double? = sp(ctx).getString("qpct_$q", null)?.toDoubleOrNull()
    fun setQualityPct(ctx: Context, q: String, v: Double?) {
        val e = sp(ctx).edit()
        if (v == null) e.remove("qpct_$q") else e.putString("qpct_$q", v.toString())
        e.apply()
    }

    fun qualityFixed(ctx: Context, q: String): Long? = sp(ctx).getString("qfix_$q", null)?.toLongOrNull()
    fun setQualityFixed(ctx: Context, q: String, v: Long?) {
        val e = sp(ctx).edit()
        if (v == null) e.remove("qfix_$q") else e.putString("qfix_$q", v.toString())
        e.apply()
    }

    fun pctFor(ctx: Context, q: String): Double = qualityPct(ctx, q) ?: defaultPct(ctx)
    fun fixedFor(ctx: Context, q: String): Long = qualityFixed(ctx, q) ?: defaultFixed(ctx)

    fun roundStep(ctx: Context): Int = sp(ctx).getInt("round_step", 500)
    fun setRoundStep(ctx: Context, v: Int) = sp(ctx).edit().putInt("round_step", v).apply()

    fun roundUp(ctx: Context): Boolean = sp(ctx).getBoolean("round_up", false)
    fun setRoundUp(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("round_up", v).apply()

    fun stockOn(ctx: Context): Boolean = sp(ctx).getBoolean("stock_on", true)
    fun setStockOn(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("stock_on", v).apply()

    fun lowStock(ctx: Context): Int = sp(ctx).getInt("low_stock", 1)
    fun setLowStock(ctx: Context, v: Int) = sp(ctx).edit().putInt("low_stock", v).apply()

    fun theme(ctx: Context): String = sp(ctx).getString("theme", "system") ?: "system"
    fun setTheme(ctx: Context, v: String) = sp(ctx).edit().putString("theme", v).apply()

    fun applyTheme(ctx: Context) {
        AppCompatDelegate.setDefaultNightMode(
            when (theme(ctx)) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
        )
    }

    fun setReopenSettings(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("reopen_settings", v).apply()
    fun consumeReopenSettings(ctx: Context): Boolean {
        val v = sp(ctx).getBoolean("reopen_settings", false)
        if (v) setReopenSettings(ctx, false)
        return v
    }
}

// ======================= PRICING =======================

object Pricing {
    /** Recommended customer price for a screen bought at [cost]. Exact decimal maths. */
    fun recommended(ctx: Context, cost: Long, quality: String): Long {
        if (cost <= 0L) return 0L
        val raw: BigDecimal = if (Prefs.method(ctx) == Prefs.PERCENT) {
            BigDecimal.valueOf(cost)
                .multiply(BigDecimal.valueOf(100.0 + Prefs.pctFor(ctx, quality)))
                .divide(BigDecimal.valueOf(100L))
        } else {
            BigDecimal.valueOf(cost + Prefs.fixedFor(ctx, quality))
        }
        return round(raw, Prefs.roundStep(ctx), Prefs.roundUp(ctx))
    }

    fun round(v: BigDecimal, step: Int, up: Boolean): Long {
        val mode = if (up) RoundingMode.CEILING else RoundingMode.HALF_UP
        if (step <= 1) return v.setScale(0, mode).toLong()
        val s = BigDecimal.valueOf(step.toLong())
        return v.divide(s, 0, mode).multiply(s).toLong()
    }

    fun methodLabel(ctx: Context, quality: String): String {
        val base = if (Prefs.method(ctx) == Prefs.PERCENT) {
            "${Fmt.pct(Prefs.pctFor(ctx, quality))} markup"
        } else {
            "+${Fmt.money(Prefs.fixedFor(ctx, quality))} profit"
        }
        val step = Prefs.roundStep(ctx)
        val r = if (step <= 1) "" else if (Prefs.roundUp(ctx)) ", rounded up to ${Fmt.num(step.toLong())}" else ", rounded to nearest ${Fmt.num(step.toLong())}"
        return "Based on $base$r"
    }

    /** The price you charge: your own price if set, otherwise the recommended price. */
    fun sellPrice(ctx: Context, st: ItemStats): Long? =
        st.customPrice ?: st.latest?.let { recommended(ctx, it.price, st.quality) }
}

// ======================= FORMATTING =======================

object Fmt {
    private val grouping: NumberFormat = NumberFormat.getIntegerInstance(Locale.US)
    private const val DAY = 24L * 60L * 60L * 1000L

    fun num(v: Long): String = grouping.format(v)
    fun money(v: Long?): String = if (v == null) "—" else grouping.format(v) + " FCFA"

    fun plain(d: Double): String =
        if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString()

    fun pct(d: Double): String =
        if (d == Math.floor(d) && !d.isInfinite()) "${d.toLong()}%" else String.format(Locale.US, "%.1f%%", d)

    fun marginPct(profit: Long, sell: Long): String =
        if (sell <= 0L) "—" else pct(Math.round(profit * 1000.0 / sell) / 10.0)

    fun date(ms: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date(ms))
    fun monthLabel(ms: Long): String = SimpleDateFormat("MMM yyyy", Locale.US).format(Date(ms))
    fun stamp(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun startOfDay(ms: Long): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = ms
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    fun today(): Long = startOfDay(System.currentTimeMillis())

    fun daysAgo(n: Int): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = today()
        c.add(Calendar.DAY_OF_MONTH, -n)
        return c.timeInMillis
    }

    /** Monday of this week. */
    fun startOfWeek(): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = today()
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) c.add(Calendar.DAY_OF_MONTH, -1)
        return c.timeInMillis
    }

    fun startOfMonth(monthsBack: Int): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = today()
        c.set(Calendar.DAY_OF_MONTH, 1)
        c.add(Calendar.MONTH, -monthsBack)
        return c.timeInMillis
    }

    fun startOfYear(): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = today()
        c.set(Calendar.DAY_OF_YEAR, 1)
        return c.timeInMillis
    }

    fun isToday(ms: Long): Boolean = ms >= today() && ms < today() + DAY

    fun parseMoney(s: String): Long? {
        val d = s.filter { it.isDigit() }
        return if (d.isEmpty()) null else d.toLongOrNull()
    }

    fun parseInt(s: String): Int? {
        val d = s.filter { it.isDigit() }
        return if (d.isEmpty()) null else d.toIntOrNull()
    }

    fun parseDecimal(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()
}

// ======================= SEARCH =======================

object Search {
    /** Common spelling mistakes and other names people type. */
    private val aliases = mapOf(
        "techno" to "tecno", "teckno" to "tecno", "tekno" to "tecno",
        "infinx" to "infinix", "infnix" to "infinix", "infinex" to "infinix", "infinik" to "infinix",
        "samsun" to "samsung", "sumsung" to "samsung", "samsumg" to "samsung", "samung" to "samsung",
        "xiomi" to "xiaomi", "xaomi" to "xiaomi", "xiami" to "xiaomi",
        "readmi" to "redmi", "redme" to "redmi",
        "apple" to "iphone", "iphon" to "iphone", "ifone" to "iphone",
        "itell" to "itel", "huwei" to "huawei", "huawai" to "huawei",
        "galaxy" to ""
    )

    fun norm(s: String): String =
        s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()

    fun tokens(s: String): List<String> =
        norm(s).split(" ").filter { it.isNotEmpty() }.map { aliases[it] ?: it }.filter { it.isNotEmpty() }

    /** True if the saved model matches what was typed (partial names, brands, small typos). */
    fun matches(m: PhoneModel, query: String): Boolean {
        val q = tokens(query)
        if (q.isEmpty()) return true
        val words = ArrayList(tokens(m.brand + " " + m.model + " " + m.variant))
        if (words.any { it == "redmi" || it == "poco" }) words.add("xiaomi")
        val joined = words.joinToString("")
        return q.all { t ->
            joined.contains(t) || (t.length >= 4 && words.any { w -> w.length >= 3 && distance(w, t) <= 1 })
        }
    }

    fun distance(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            for (j in 0..b.length) prev[j] = cur[j]
        }
        return prev[b.length]
    }
}
