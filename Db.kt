package com.lontum.screenprice

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local offline database. Nothing is ever replaced when a new price is entered:
 * every purchase is its own row, so the full price history is kept.
 */
class Db private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    companion object {
        const val NAME = "screenprices.db"
        private const val VERSION = 1

        @Volatile private var instance: Db? = null

        fun get(context: Context): Db =
            instance ?: synchronized(this) { instance ?: Db(context).also { instance = it } }

        /** Closes the database so its file can be copied or replaced. */
        fun closeAll() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE suppliers (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
                "phone TEXT NOT NULL DEFAULT '', location TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL(
            "CREATE TABLE models (id INTEGER PRIMARY KEY AUTOINCREMENT, brand TEXT NOT NULL, model TEXT NOT NULL, " +
                "variant TEXT NOT NULL DEFAULT '', screen_type TEXT NOT NULL DEFAULT '', fingerprint TEXT NOT NULL DEFAULT '', " +
                "frame TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '', updated INTEGER NOT NULL DEFAULT 0)"
        )
        db.execSQL(
            "CREATE TABLE purchases (id INTEGER PRIMARY KEY AUTOINCREMENT, model_id INTEGER NOT NULL, quality TEXT NOT NULL, " +
                "supplier_id INTEGER, supplier_name TEXT NOT NULL DEFAULT '', price INTEGER NOT NULL, qty INTEGER NOT NULL, " +
                "date INTEGER NOT NULL, notes TEXT NOT NULL DEFAULT '', created INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE sales (id INTEGER PRIMARY KEY AUTOINCREMENT, model_id INTEGER NOT NULL, quality TEXT NOT NULL, " +
                "cost INTEGER NOT NULL, price INTEGER NOT NULL, qty INTEGER NOT NULL, date INTEGER NOT NULL, " +
                "notes TEXT NOT NULL DEFAULT '', created INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE TABLE adjustments (id INTEGER PRIMARY KEY AUTOINCREMENT, model_id INTEGER NOT NULL, quality TEXT NOT NULL, " +
                "kind TEXT NOT NULL, qty INTEGER NOT NULL, date INTEGER NOT NULL, notes TEXT NOT NULL DEFAULT '')"
        )
        db.execSQL(
            "CREATE TABLE custom_prices (model_id INTEGER NOT NULL, quality TEXT NOT NULL, price INTEGER NOT NULL, " +
                "PRIMARY KEY (model_id, quality))"
        )
        db.execSQL("CREATE INDEX idx_purchases_model ON purchases(model_id)")
        db.execSQL("CREATE INDEX idx_sales_model ON sales(model_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future versions add columns here without deleting any records.
    }

    // ---------- cursor helpers ----------

    private fun Cursor.s(c: String): String = getString(getColumnIndexOrThrow(c)) ?: ""
    private fun Cursor.l(c: String): Long = getLong(getColumnIndexOrThrow(c))
    private fun Cursor.i(c: String): Int = getInt(getColumnIndexOrThrow(c))
    private fun Cursor.lOrNull(c: String): Long? {
        val k = getColumnIndexOrThrow(c)
        return if (isNull(k)) null else getLong(k)
    }

    private fun <T> list(sql: String, args: Array<String>?, map: (Cursor) -> T): List<T> {
        val out = ArrayList<T>()
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) out.add(map(c))
        }
        return out
    }

    private fun count(table: String): Long =
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { c ->
            if (c.moveToFirst()) c.getLong(0) else 0L
        }

    // ---------- suppliers ----------

    private fun toSupplier(c: Cursor) = Supplier(c.l("id"), c.s("name"), c.s("phone"), c.s("location"), c.s("notes"))

    fun suppliers(): List<Supplier> =
        list("SELECT * FROM suppliers ORDER BY name COLLATE NOCASE", null) { toSupplier(it) }

    fun supplier(id: Long): Supplier? =
        list("SELECT * FROM suppliers WHERE id=?", arrayOf(id.toString())) { toSupplier(it) }.firstOrNull()

    fun supplierByName(name: String): Supplier? =
        list("SELECT * FROM suppliers WHERE name=? COLLATE NOCASE", arrayOf(name.trim())) { toSupplier(it) }.firstOrNull()

    fun saveSupplier(s: Supplier): Long {
        val v = ContentValues().apply {
            put("name", s.name.trim())
            put("phone", s.phone.trim())
            put("location", s.location.trim())
            put("notes", s.notes.trim())
        }
        if (s.id == 0L) {
            s.id = writableDatabase.insert("suppliers", null, v)
        } else {
            writableDatabase.update("suppliers", v, "id=?", arrayOf(s.id.toString()))
            val pv = ContentValues().apply { put("supplier_name", s.name.trim()) }
            writableDatabase.update("purchases", pv, "supplier_id=?", arrayOf(s.id.toString()))
        }
        return s.id
    }

    /** Deletes the supplier but keeps every purchase (with the supplier's name). */
    fun deleteSupplier(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL("UPDATE purchases SET supplier_id=NULL WHERE supplier_id=?", arrayOf<Any>(id))
            db.delete("suppliers", "id=?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun findOrCreateSupplier(name: String): Long? {
        val n = name.trim()
        if (n.isEmpty()) return null
        supplierByName(n)?.let { return it.id }
        return saveSupplier(Supplier(name = n))
    }

    // ---------- phone models ----------

    private fun toModel(c: Cursor) = PhoneModel(
        c.l("id"), c.s("brand"), c.s("model"), c.s("variant"), c.s("screen_type"),
        c.s("fingerprint"), c.s("frame"), c.s("notes"), c.l("updated")
    )

    fun models(): List<PhoneModel> = list(
        "SELECT * FROM models ORDER BY brand COLLATE NOCASE, model COLLATE NOCASE, variant COLLATE NOCASE", null
    ) { toModel(it) }

    fun model(id: Long): PhoneModel? =
        list("SELECT * FROM models WHERE id=?", arrayOf(id.toString())) { toModel(it) }.firstOrNull()

    fun findDuplicate(brand: String, model: String, variant: String, excludeId: Long): PhoneModel? = list(
        "SELECT * FROM models WHERE brand=? COLLATE NOCASE AND model=? COLLATE NOCASE AND variant=? COLLATE NOCASE AND id<>?",
        arrayOf(brand.trim(), model.trim(), variant.trim(), excludeId.toString())
    ) { toModel(it) }.firstOrNull()

    fun saveModel(m: PhoneModel): Long {
        val v = ContentValues().apply {
            put("brand", m.brand.trim())
            put("model", m.model.trim())
            put("variant", m.variant.trim())
            put("screen_type", m.screenType.trim())
            put("fingerprint", m.fingerprint.trim())
            put("frame", m.frame.trim())
            put("notes", m.notes.trim())
            put("updated", System.currentTimeMillis())
        }
        if (m.id == 0L) m.id = writableDatabase.insert("models", null, v)
        else writableDatabase.update("models", v, "id=?", arrayOf(m.id.toString()))
        return m.id
    }

    fun touchModel(id: Long) {
        writableDatabase.execSQL("UPDATE models SET updated=? WHERE id=?", arrayOf<Any>(System.currentTimeMillis(), id))
    }

    /** Deletes a model and everything recorded for it (asked for explicitly by the user). */
    fun deleteModel(id: Long) {
        val db = writableDatabase
        val a = arrayOf(id.toString())
        db.beginTransaction()
        try {
            db.delete("purchases", "model_id=?", a)
            db.delete("sales", "model_id=?", a)
            db.delete("adjustments", "model_id=?", a)
            db.delete("custom_prices", "model_id=?", a)
            db.delete("models", "id=?", a)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun modelCount(): Long = count("models")

    // ---------- purchases ----------

    private fun toPurchase(c: Cursor) = Purchase(
        c.l("id"), c.l("model_id"), c.s("quality"), c.lOrNull("supplier_id"), c.s("supplier_name"),
        c.l("price"), c.i("qty"), c.l("date"), c.s("notes"), c.l("created")
    )

    private val purchaseOrder = " ORDER BY date DESC, created DESC, id DESC"

    fun purchases(): List<Purchase> = list("SELECT * FROM purchases$purchaseOrder", null) { toPurchase(it) }

    fun purchasesForModel(modelId: Long): List<Purchase> =
        list("SELECT * FROM purchases WHERE model_id=?$purchaseOrder", arrayOf(modelId.toString())) { toPurchase(it) }

    fun purchasesForSupplier(supplierId: Long): List<Purchase> =
        list("SELECT * FROM purchases WHERE supplier_id=?$purchaseOrder", arrayOf(supplierId.toString())) { toPurchase(it) }

    fun purchase(id: Long): Purchase? =
        list("SELECT * FROM purchases WHERE id=?", arrayOf(id.toString())) { toPurchase(it) }.firstOrNull()

    fun savePurchase(p: Purchase): Long {
        val sid = p.supplierId
        val v = ContentValues().apply {
            put("model_id", p.modelId)
            put("quality", p.quality)
            if (sid == null) putNull("supplier_id") else put("supplier_id", sid)
            put("supplier_name", p.supplierName)
            put("price", p.price)
            put("qty", p.qty)
            put("date", p.date)
            put("notes", p.notes)
            put("created", if (p.created == 0L) System.currentTimeMillis() else p.created)
        }
        if (p.id == 0L) p.id = writableDatabase.insert("purchases", null, v)
        else writableDatabase.update("purchases", v, "id=?", arrayOf(p.id.toString()))
        return p.id
    }

    fun deletePurchase(id: Long) {
        writableDatabase.delete("purchases", "id=?", arrayOf(id.toString()))
    }

    fun purchaseCount(): Long = count("purchases")

    // ---------- sales ----------

    private fun toSale(c: Cursor) = Sale(
        c.l("id"), c.l("model_id"), c.s("quality"), c.l("cost"), c.l("price"),
        c.i("qty"), c.l("date"), c.s("notes"), c.l("created")
    )

    fun sales(): List<Sale> =
        list("SELECT * FROM sales ORDER BY date DESC, created DESC, id DESC", null) { toSale(it) }

    fun sale(id: Long): Sale? =
        list("SELECT * FROM sales WHERE id=?", arrayOf(id.toString())) { toSale(it) }.firstOrNull()

    fun saveSale(s: Sale): Long {
        val v = ContentValues().apply {
            put("model_id", s.modelId)
            put("quality", s.quality)
            put("cost", s.cost)
            put("price", s.price)
            put("qty", s.qty)
            put("date", s.date)
            put("notes", s.notes)
            put("created", if (s.created == 0L) System.currentTimeMillis() else s.created)
        }
        if (s.id == 0L) s.id = writableDatabase.insert("sales", null, v)
        else writableDatabase.update("sales", v, "id=?", arrayOf(s.id.toString()))
        return s.id
    }

    fun deleteSale(id: Long) {
        writableDatabase.delete("sales", "id=?", arrayOf(id.toString()))
    }

    // ---------- stock adjustments ----------

    private fun toAdj(c: Cursor) = Adjustment(
        c.l("id"), c.l("model_id"), c.s("quality"), c.s("kind"), c.i("qty"), c.l("date"), c.s("notes")
    )

    fun adjustments(): List<Adjustment> =
        list("SELECT * FROM adjustments ORDER BY date DESC, id DESC", null) { toAdj(it) }

    fun adjustmentsForModel(modelId: Long): List<Adjustment> =
        list("SELECT * FROM adjustments WHERE model_id=? ORDER BY date DESC, id DESC", arrayOf(modelId.toString())) { toAdj(it) }

    fun saveAdjustment(a: Adjustment): Long {
        val v = ContentValues().apply {
            put("model_id", a.modelId)
            put("quality", a.quality)
            put("kind", a.kind)
            put("qty", a.qty)
            put("date", a.date)
            put("notes", a.notes)
        }
        a.id = writableDatabase.insert("adjustments", null, v)
        return a.id
    }

    fun deleteAdjustment(id: Long) {
        writableDatabase.delete("adjustments", "id=?", arrayOf(id.toString()))
    }

    // ---------- your own customer prices ----------

    fun customPrices(): Map<ItemKey, Long> {
        val m = HashMap<ItemKey, Long>()
        readableDatabase.rawQuery("SELECT * FROM custom_prices", null).use { c ->
            while (c.moveToNext()) m[ItemKey(c.l("model_id"), c.s("quality"))] = c.l("price")
        }
        return m
    }

    fun customPrice(modelId: Long, quality: String): Long? = list(
        "SELECT price FROM custom_prices WHERE model_id=? AND quality=?", arrayOf(modelId.toString(), quality)
    ) { it.getLong(0) }.firstOrNull()

    /** Sets your own selling price. Null or 0 removes it (back to the recommended price). */
    fun setCustomPrice(modelId: Long, quality: String, price: Long?) {
        if (price == null || price <= 0L) {
            writableDatabase.delete("custom_prices", "model_id=? AND quality=?", arrayOf(modelId.toString(), quality))
        } else {
            val v = ContentValues().apply {
                put("model_id", modelId)
                put("quality", quality)
                put("price", price)
            }
            writableDatabase.insertWithOnConflict("custom_prices", null, v, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    // ---------- everything together ----------

    fun allStats(): LinkedHashMap<ItemKey, ItemStats> =
        Stats.build(purchases(), sales(), adjustments(), customPrices())
}
