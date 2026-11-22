package com.lontum.screenprice

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Export to Excel (CSV), backup and restore (also used to move to a new phone). */
object DataIO {

    private fun cell(s: String): String = "\"" + s.replace("\"", "\"\"") + "\""

    private fun line(vararg v: Any?): String =
        v.joinToString(",") { x ->
            when (x) {
                null -> ""
                is Number -> x.toString()
                else -> cell(x.toString())
            }
        } + "\r\n"

    private fun write(ctx: Context, name: String, content: String): File {
        val dir = File(ctx.cacheDir, "exports")
        dir.mkdirs()
        val f = File(dir, name)
        // The BOM makes Excel read accents and the FCFA text correctly.
        f.writeText("﻿" + content, Charsets.UTF_8)
        return f
    }

    private fun uriFor(ctx: Context, f: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)

    // ---------- CSV builders ----------

    fun priceListCsv(ctx: Context): String {
        val db = Db.get(ctx)
        val models = db.models().associateBy { it.id }
        val sb = StringBuilder()
        sb.append(line("Brand", "Model", "Variant", "Screen type", "Quality", "Latest price (FCFA)", "Latest supplier",
            "Latest date", "Lowest price (FCFA)", "Average price (FCFA)", "Recommended price (FCFA)", "Your price (FCFA)",
            "Expected profit (FCFA)", "In stock"))
        val rows = db.allStats().values.filter { it.latest != null && models.containsKey(it.modelId) }
            .sortedBy { (models[it.modelId]?.title ?: "") + " " + it.quality }
        for (st in rows) {
            val m = models[st.modelId] ?: continue
            val p = st.latest ?: continue
            val rec = Pricing.recommended(ctx, p.price, st.quality)
            val sell = st.customPrice ?: rec
            sb.append(line(m.brand, m.model, m.variant, m.screenType, st.quality, p.price, p.supplierName, Fmt.date(p.date),
                st.lowest?.price, st.average, rec, st.customPrice, sell - p.price, st.available))
        }
        return sb.toString()
    }

    private fun modelsCsv(db: Db): String {
        val sb = StringBuilder()
        sb.append(line("Brand", "Model", "Variant", "Screen type", "Fingerprint", "Frame", "Notes"))
        for (m in db.models()) sb.append(line(m.brand, m.model, m.variant, m.screenType, m.fingerprint, m.frame, m.notes))
        return sb.toString()
    }

    private fun purchasesCsv(db: Db): String {
        val models = db.models().associateBy { it.id }
        val sb = StringBuilder()
        sb.append(line("Date", "Brand", "Model", "Variant", "Quality", "Supplier", "Price per screen (FCFA)", "Quantity", "Total (FCFA)", "Notes"))
        for (p in db.purchases()) {
            val m = models[p.modelId]
            sb.append(line(Fmt.date(p.date), m?.brand ?: "", m?.model ?: "", m?.variant ?: "", p.quality, p.supplierName,
                p.price, p.qty, p.price * p.qty, p.notes))
        }
        return sb.toString()
    }

    private fun salesCsv(db: Db): String {
        val models = db.models().associateBy { it.id }
        val sb = StringBuilder()
        sb.append(line("Date", "Brand", "Model", "Variant", "Quality", "Cost per screen (FCFA)", "Price per screen (FCFA)",
            "Quantity", "Revenue (FCFA)", "Profit (FCFA)", "Notes"))
        for (s in db.sales()) {
            val m = models[s.modelId]
            sb.append(line(Fmt.date(s.date), m?.brand ?: "", m?.model ?: "", m?.variant ?: "", s.quality, s.cost, s.price,
                s.qty, s.revenue, s.profit, s.notes))
        }
        return sb.toString()
    }

    private fun suppliersCsv(db: Db): String {
        val sb = StringBuilder()
        sb.append(line("Name", "Telephone", "Location", "Notes"))
        for (s in db.suppliers()) sb.append(line(s.name, s.phone, s.location, s.notes))
        return sb.toString()
    }

    private fun stockCsv(db: Db): String {
        val models = db.models().associateBy { it.id }
        val sb = StringBuilder()
        sb.append(line("Date", "Model", "Quality", "Change", "Quantity", "Notes"))
        for (a in db.adjustments()) {
            sb.append(line(Fmt.date(a.date), models[a.modelId]?.title ?: "", a.quality, Adj.label(a.kind), a.delta, a.notes))
        }
        return sb.toString()
    }

    // ---------- sharing ----------

    fun sharePriceList(ctx: Context) {
        val f = write(ctx, "screen-price-list-${Fmt.stamp()}.csv", priceListCsv(ctx))
        val i = Intent(Intent.ACTION_SEND)
        i.type = "text/csv"
        i.putExtra(Intent.EXTRA_STREAM, uriFor(ctx, f))
        i.putExtra(Intent.EXTRA_SUBJECT, "Screen price list")
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Send price list"))
    }

    /** Sends one CSV file per table. Each opens in Excel, WPS Office or Google Sheets. */
    fun shareAll(ctx: Context) {
        val db = Db.get(ctx)
        val s = Fmt.stamp()
        val files = listOf(
            write(ctx, "price-list-$s.csv", priceListCsv(ctx)),
            write(ctx, "phone-models-$s.csv", modelsCsv(db)),
            write(ctx, "purchases-$s.csv", purchasesCsv(db)),
            write(ctx, "sales-$s.csv", salesCsv(db)),
            write(ctx, "suppliers-$s.csv", suppliersCsv(db)),
            write(ctx, "stock-changes-$s.csv", stockCsv(db))
        )
        val uris = ArrayList<Uri>()
        for (f in files) uris.add(uriFor(ctx, f))
        val i = Intent(Intent.ACTION_SEND_MULTIPLE)
        i.type = "text/csv"
        i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        i.putExtra(Intent.EXTRA_SUBJECT, "Screen price data")
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Send data files"))
    }

    // ---------- backup / restore ----------

    /** Saves the whole database, including your pricing settings, to a file you choose. */
    fun backup(ctx: Context, target: Uri) {
        val db = Db.get(ctx).writableDatabase
        db.execSQL("CREATE TABLE IF NOT EXISTS app_settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.delete("app_settings", null, null)
        val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        for ((k, v) in sp.all) {
            if (k == "reopen_settings" || v == null) continue
            val stored = when (v) {
                is Boolean -> "b:$v"
                is Int -> "i:$v"
                is Long -> "l:$v"
                is Float -> "f:$v"
                else -> "s:$v"
            }
            val cv = ContentValues()
            cv.put("key", k)
            cv.put("value", stored)
            db.insert("app_settings", null, cv)
        }
        Db.closeAll()
        val file = ctx.getDatabasePath(Db.NAME)
        ctx.contentResolver.openOutputStream(target)?.use { out ->
            file.inputStream().use { it.copyTo(out) }
        } ?: throw IllegalStateException("Could not write the backup file")
    }

    /** Replaces everything in the app with the data in a backup file. */
    fun restore(ctx: Context, source: Uri) {
        val tmp = File(ctx.cacheDir, "restore.db")
        ctx.contentResolver.openInputStream(source)?.use { input ->
            tmp.outputStream().use { input.copyTo(it) }
        } ?: throw IllegalStateException("Could not read the backup file")

        val header = tmp.inputStream().use { s ->
            val b = ByteArray(16)
            val n = s.read(b)
            if (n < 15) "" else String(b, 0, 15, Charsets.US_ASCII)
        }
        if (header != "SQLite format 3") {
            tmp.delete()
            throw IllegalArgumentException("This is not a Screen Price Manager backup file")
        }
        val valid = SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY).use { d ->
            d.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN ('models','purchases','sales')", null).use { c ->
                c.moveToFirst() && c.getInt(0) == 3
            }
        }
        if (!valid) {
            tmp.delete()
            throw IllegalArgumentException("This backup file is not from Screen Price Manager")
        }

        Db.closeAll()
        val file = ctx.getDatabasePath(Db.NAME)
        file.parentFile?.mkdirs()
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
        File(file.path + "-journal").delete()
        tmp.copyTo(file, overwrite = true)
        tmp.delete()

        restoreSettings(ctx)
    }

    private fun restoreSettings(ctx: Context) {
        val db = Db.get(ctx).readableDatabase
        val exists = db.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='app_settings'", null).use { c ->
            c.moveToFirst() && c.getInt(0) == 1
        }
        if (!exists) return
        val e = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
        e.clear()
        db.rawQuery("SELECT key, value FROM app_settings", null).use { c ->
            while (c.moveToNext()) {
                val k = c.getString(0) ?: continue
                val raw = c.getString(1) ?: continue
                if (raw.length < 2) continue
                val v = raw.substring(2)
                when (raw.substring(0, 2)) {
                    "b:" -> e.putBoolean(k, v == "true")
                    "i:" -> v.toIntOrNull()?.let { e.putInt(k, it) }
                    "l:" -> v.toLongOrNull()?.let { e.putLong(k, it) }
                    "f:" -> v.toFloatOrNull()?.let { e.putFloat(k, it) }
                    else -> e.putString(k, v)
                }
            }
        }
        e.apply()
    }
}
