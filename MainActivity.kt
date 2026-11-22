package com.lontum.screenprice

import android.app.Application
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.applyTheme(this)
    }
}

class MainActivity : AppCompatActivity() {

    companion object {
        const val NAV_DASH = 1
        const val NAV_SEARCH = 2
        const val NAV_ADD = 3
        const val NAV_INV = 4
        const val NAV_SALES = 5
        const val NAV_SUP = 6
        const val NAV_REP = 7
        const val NAV_SET = 8
    }

    private lateinit var drawer: DrawerLayout
    private lateinit var nav: NavigationView
    private lateinit var toolbar: MaterialToolbar
    private lateinit var toggle: ActionBarDrawerToggle
    private var currentRoot = NAV_DASH

    /** A model just created from a form, so the form can select it. */
    var pendingModelId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Start fresh each time (no half-restored screens after a theme change).
        super.onCreate(null)

        val content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL

        toolbar = MaterialToolbar(ContextThemeWrapper(this, com.google.android.material.R.style.ThemeOverlay_MaterialComponents_Dark_ActionBar))
        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.c_bar))
        content.addView(toolbar, LinearLayout.LayoutParams(MATCH, WRAP))

        val frame = FrameLayout(this)
        frame.id = R.id.content_frame
        content.addView(frame, LinearLayout.LayoutParams(MATCH, 0, 1f))

        drawer = DrawerLayout(this)
        drawer.addView(content, DrawerLayout.LayoutParams(MATCH, MATCH))

        nav = NavigationView(this)
        nav.addHeaderView(header())
        val items = listOf(
            Triple(NAV_DASH, "Dashboard", android.R.drawable.ic_menu_view),
            Triple(NAV_SEARCH, "Search models", android.R.drawable.ic_menu_search),
            Triple(NAV_ADD, "Add purchase", android.R.drawable.ic_menu_add),
            Triple(NAV_INV, "Inventory", android.R.drawable.ic_menu_agenda),
            Triple(NAV_SALES, "Sales & profits", android.R.drawable.ic_menu_my_calendar),
            Triple(NAV_SUP, "Suppliers", android.R.drawable.ic_menu_call),
            Triple(NAV_REP, "Reports", android.R.drawable.ic_menu_sort_by_size),
            Triple(NAV_SET, "Settings", android.R.drawable.ic_menu_preferences)
        )
        items.forEachIndexed { i, (id, label, icon) ->
            val item = nav.menu.add(Menu.NONE, id, i, label)
            item.setIcon(icon)
            item.isCheckable = true
        }
        nav.setNavigationItemSelectedListener { item ->
            drawer.closeDrawer(GravityCompat.START)
            openRoot(item.itemId)
            true
        }
        drawer.addView(nav, DrawerLayout.LayoutParams(dp(290), MATCH, Gravity.START))

        setContentView(drawer)
        setSupportActionBar(toolbar)

        toggle = ActionBarDrawerToggle(this, drawer, toolbar, R.string.drawer_open, R.string.drawer_close)
        toggle.drawerArrowDrawable.color = Color.WHITE
        drawer.addDrawerListener(toggle)
        toggle.syncState()
        toggle.setToolbarNavigationClickListener { onBackPressedDispatcher.onBackPressed() }

        supportFragmentManager.addOnBackStackChangedListener { updateNavIcon() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBack()
            }
        })

        openRoot(if (Prefs.consumeReopenSettings(this)) NAV_SET else NAV_DASH)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun header(): LinearLayout {
        val h = LinearLayout(this)
        h.orientation = LinearLayout.VERTICAL
        h.setBackgroundColor(ContextCompat.getColor(this, R.color.c_bar))
        h.setPadding(dp(20), dp(36), dp(20), dp(20))
        val t = TextView(this)
        t.text = getString(R.string.app_name)
        t.textSize = 20f
        t.setTextColor(Color.WHITE)
        t.setTypeface(t.typeface, Typeface.BOLD)
        val s = TextView(this)
        s.text = getString(R.string.shop_name)
        s.textSize = 13f
        s.setTextColor(Color.parseColor("#CFE0F0"))
        h.addView(t)
        h.addView(s)
        return h
    }

    private fun handleBack() {
        when {
            drawer.isDrawerOpen(GravityCompat.START) -> drawer.closeDrawer(GravityCompat.START)
            supportFragmentManager.backStackEntryCount > 0 -> supportFragmentManager.popBackStack()
            currentRoot != NAV_DASH -> openRoot(NAV_DASH)
            else -> finish()
        }
    }

    private fun updateNavIcon() {
        val inner = supportFragmentManager.backStackEntryCount > 0
        toggle.isDrawerIndicatorEnabled = !inner
        supportActionBar?.setDisplayHomeAsUpEnabled(inner)
        if (inner) toolbar.setNavigationIconTint(Color.WHITE) else toggle.syncState()
        drawer.setDrawerLockMode(if (inner) DrawerLayout.LOCK_MODE_LOCKED_CLOSED else DrawerLayout.LOCK_MODE_UNLOCKED)
    }

    /** Opens one of the 8 main sections from the menu. */
    fun openRoot(id: Int) {
        currentRoot = id
        pendingModelId = null
        val f: Fragment = when (id) {
            NAV_SEARCH -> SearchScreen()
            NAV_ADD -> PurchaseFormScreen.of()
            NAV_INV -> InventoryScreen()
            NAV_SALES -> SalesScreen()
            NAV_SUP -> SuppliersScreen()
            NAV_REP -> ReportsScreen()
            NAV_SET -> SettingsScreen()
            else -> DashboardScreen()
        }
        supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction().replace(R.id.content_frame, f).commit()
        nav.setCheckedItem(id)
        updateNavIcon()
    }

    /** Opens a screen on top; the screen below keeps what was typed in it. */
    fun push(f: Fragment) {
        val fm = supportFragmentManager
        val t = fm.beginTransaction().setReorderingAllowed(true)
        fm.findFragmentById(R.id.content_frame)?.let { t.hide(it) }
        t.add(R.id.content_frame, f).addToBackStack(null).commit()
    }

    fun back() {
        supportFragmentManager.popBackStack()
    }

    /** Closes the current screen and opens another in its place. */
    fun replaceTop(f: Fragment) {
        supportFragmentManager.popBackStackImmediate()
        push(f)
    }

    fun setScreenTitle(t: String) {
        supportActionBar?.title = t
    }
}
