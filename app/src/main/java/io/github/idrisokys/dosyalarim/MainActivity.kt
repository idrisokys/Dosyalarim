package io.github.idrisokys.dosyalarim

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.view.DragEvent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaScannerConnection
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager
import java.util.concurrent.Executors
import android.webkit.MimeTypeMap
import android.widget.*
import android.graphics.drawable.Drawable
import java.io.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private val dockBg = 0xFF1A1A1A.toInt(); private val bg = 0xFF121212.toInt(); private val bar = 0xFF212121.toInt(); private val card = 0xFF1E1E1E.toInt()
    private lateinit var titleV: TextView; private lateinit var pinV: ImageView; private lateinit var titleRow: LinearLayout; private lateinit var cancelV: TextView; private lateinit var body: FrameLayout; private lateinit var crumbS: HorizontalScrollView; private lateinit var crumbL: LinearLayout; private lateinit var dock: LinearLayout
    private var cur: File? = null; private var list: List<File>? = null; private var listTitle = ""
    private var editMode = false
    private lateinit var selAllV: ImageView; private lateinit var penV: ImageView
    private var picking = false; private var slotPick = -1; private val sel = linkedSetOf<File>()
    private var clip = listOf<File>(); private var cut = false
    private var showHidden = true; private var asked = false
    private var appsMode = false
    private var pendingShare: List<Uri>? = null   // dışarıdan paylaşılan, kaydedilmeyi bekleyen dosyalar
    private var gal = false
    private var viewer: Viewer? = null
    private var editor: TextEditor? = null
    private class AppRow(val label: String, val pkg: String, val size: Long, val time: Long, val ai: ApplicationInfo)
    private var apps = listOf<AppRow>()
    private var appAdapter: BaseAdapter? = null
    private val iconCache = HashMap<String, Drawable>()
    private val root: File = Environment.getExternalStorageDirectory()
    private val prefs by lazy { getSharedPreferences("p", 0) }
    private var cats = mapOf<String, List<File>>()
    private val IMG = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg")
    private val VID = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v")
    private val AUD = setOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "opus")
    private val DOC = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "odt", "rtf", "csv", "epub")
    private val TXT = setOf("txt", "md", "log", "json", "xml", "ini", "conf", "cfg", "yml", "yaml", "properties", "js", "css", "kt", "kts", "java", "py", "sh", "gradle", "srt", "tex")
    private val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun tv(t: String, s: Float, c: Int) = TextView(this).apply { text = t; textSize = s; setTextColor(c) }
    private fun sz(b: Long): String {
        var v = b.toDouble(); var i = 0; val u = arrayOf("B", "KB", "MB", "GB")
        while (v >= 1024 && i < 3) { v /= 1024; i++ }
        return if (i == 0) "$b B" else String.format("%.1f %s", v, u[i])
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.statusBarColor = Color.BLACK; window.navigationBarColor = bg
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val tb = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(bar) }
        titleV = tv("", 21f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        crumbL = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        crumbS = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; visibility = View.GONE; addView(crumbL, -2, -1) }
        cancelV = tv("İptal", 18f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD; maxLines = 1; gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE; setPadding(dp(12), 0, 0, 0); setOnClickListener { sel.clear(); editMode = false; refresh(true) } }
        selAllV = ImageView(this).apply {
            setImageDrawable(Icons.selectAll()); setColorFilter(Color.WHITE); setPadding(dp(8), dp(8), dp(8), dp(8)); visibility = View.GONE
            contentDescription = "Tümünü seç"
            setOnClickListener {
                val all = items()
                if (all.isNotEmpty() && sel.containsAll(all)) sel.clear() else sel.addAll(all)
                selectionChanged()
            }
        }
        penV = ImageView(this).apply {
            setImageDrawable(Icons.rename()); setColorFilter(Color.WHITE); setPadding(dp(8), dp(8), dp(8), dp(8)); visibility = View.GONE
            contentDescription = "Seç"; setOnClickListener { editMode = true; refresh(true) }
            setOnLongClickListener { newFolder(); true }
        }
        pinV = ImageView(this).apply {
            setImageDrawable(Icons.pin()); setColorFilter(Color.WHITE); setPadding(dp(8), dp(8), dp(8), dp(8)); visibility = View.GONE
            contentDescription = "Ana ekrana sabitle"; setOnClickListener { pinSelected() }
        }
        titleRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(titleV, LinearLayout.LayoutParams(-2, -2)); addView(pinV, LinearLayout.LayoutParams(dp(40), dp(40)))
        }
        val center = FrameLayout(this).apply {
            addView(titleRow, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL))
            addView(crumbS, FrameLayout.LayoutParams(-1, -1))
        }
        tb.setPadding(dp(12), 0, dp(12), 0); tb.addView(center, LinearLayout.LayoutParams(0, -1, 1f))
        tb.addView(selAllV, LinearLayout.LayoutParams(dp(40), dp(40)))
        tb.addView(cancelV, LinearLayout.LayoutParams(-2, -1))
        tb.addView(penV, LinearLayout.LayoutParams(dp(40), dp(40)))
        body = FrameLayout(this)
        dock = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(dockBg) }
        col.addView(tb, -1, dp(54)); col.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(View(this).apply { setBackgroundColor(0xFF333333.toInt()) }, -1, dp(1))
        col.addView(dock, -1, dp(100))
        setContentView(col); migratePins(); updateDock(); home()
        if (s == null && (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) handleShare(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    private fun hasPerm() = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        if (hasPerm()) { if (cats.isEmpty()) scan(); if (appsMode) loadApps() }
        else if (!asked) {
            asked = true
            if (Build.VERSION.SDK_INT >= 30) startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            else requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }

    private fun scan() {
        Thread {
            val all = ArrayList<File>()
            val skip = File(root, "Android")
            root.walkTopDown().onEnter { it != skip && (it == root || !it.name.startsWith(".")) }
                .filter { it.isFile && !it.name.startsWith(".") }.forEach { all.add(it) }
            val m = HashMap<String, List<File>>()
            m["img"] = all.filter { val e = it.extension.lowercase(); (e in IMG || e in VID) && !it.name.startsWith(".trashed") && !it.name.startsWith(".pending") }
            cats = m
            runOnUiThread { if (cur == null && list == null && !picking && !appsMode) home() }
        }.start()
    }

    private fun chrome(t: String, crumbs: Boolean = false) {
        titleV.text = t
        titleRow.visibility = if (crumbs) View.GONE else View.VISIBLE
        pinV.visibility = if (sel.isNotEmpty()) View.VISIBLE else View.GONE
        crumbS.visibility = if (crumbs) View.VISIBLE else View.GONE
        if (crumbs) buildCrumbs()
        cancelV.visibility = if (editMode) View.VISIBLE else View.GONE
        selAllV.visibility = if (editMode) View.VISIBLE else View.GONE
        val canEdit = !editMode && !appsMode && pendingShare == null && !picking && (cur != null || list != null) && !(list != null && gal)
        penV.visibility = if (canEdit) View.VISIBLE else View.GONE
    }

    private fun buildCrumbs() {
        crumbL.removeAllViews()
        fun chev() = ImageView(this).apply { setImageDrawable(Icons.chevron()); setColorFilter(0xFF888888.toInt()) }
        val last = cur ?: return
        crumbL.addView(ImageView(this).apply { setImageDrawable(Icons.homeColor()); setOnClickListener { if (pendingShare != null) browse(root) else home() } }, dp(34), dp(34))
        crumbL.addView(chev(), dp(24), dp(24))
        crumbL.addView(ImageView(this).apply { setImageDrawable(Icons.folder()); setOnClickListener { browse(root) } }, dp(34), dp(34))
        val chain = ArrayList<File>(); var f: File? = last
        while (f != null && f.path != root.path) { chain.add(0, f); f = f.parentFile }
        chain.forEach { d ->
            crumbL.addView(chev(), dp(24), dp(24))
            crumbL.addView(tv(d.name, 19f, if (d == last) Color.WHITE else 0xFFBBBBBB.toInt()).apply {
                maxLines = 1; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), 0, dp(6), 0); setOnClickListener { if (d != last) browse(d) }
            }, LinearLayout.LayoutParams(-2, -1))
        }
        crumbS.post { crumbS.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun updateDock() {
        dock.removeAllViews()
        for (n in 0 until 4) {
            val p = prefs.getString("slot$n", null)
            val f = if (p == null) null else File(p)
            val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            if (f == null) {
                v.addView(ImageView(this).apply {
                    setImageDrawable(Icons.add()); setColorFilter(0xFF888888.toInt()); setPadding(dp(5), dp(5), dp(5), dp(5))
                    background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(8).toFloat(); setStroke(dp(1), 0xFF555555.toInt(), dp(4).toFloat(), dp(3).toFloat()) }
                }, dp(54), dp(54))
                v.addView(tv("Ekle", 14f, 0xFF888888.toInt()).apply { gravity = Gravity.CENTER })
                v.setOnClickListener { if (pendingShare != null) return@setOnClickListener; slotPick = n; picking = true; browse(root) }
            } else {
                v.addView(ImageView(this).apply { setImageDrawable(Icons.folder()) }, dp(54), dp(54))
                v.addView(tv(f.name, 14f, 0xFFCCCCCC.toInt()).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER; setPadding(dp(2), 0, dp(2), 0) })
                v.setOnClickListener { if (f.isDirectory) { if (pendingShare == null) { picking = false; list = null }; browse(f) } else Toast.makeText(this, "Klasör bulunamadı", Toast.LENGTH_SHORT).show() }
                v.setOnLongClickListener { if (pendingShare == null) slotMenu(n); true }
                // dosyaları bu klasöre sürükleyip bırakınca taşı
                v.setOnDragListener { view, e ->
                    when (e.action) {
                        DragEvent.ACTION_DRAG_STARTED -> e.clipDescription?.hasMimeType("text/plain") == true
                        DragEvent.ACTION_DRAG_ENTERED -> { view.setBackgroundColor(0xFF3B5568.toInt()); true }
                        DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> { view.setBackgroundColor(Color.TRANSPARENT); true }
                        DragEvent.ACTION_DROP -> {
                            val t = e.clipData?.getItemAt(0)?.text?.toString()
                            if (t != null) moveInto(f, t.split("\n").filter { it.isNotEmpty() }.map { File(it) })
                            true
                        }
                        else -> true
                    }
                }
            }
            dock.addView(v, LinearLayout.LayoutParams(0, -1, 1f))
        }
    }

    private fun moveInto(dest: File, files: List<File>) {
        if (!dest.isDirectory) { Toast.makeText(this, "Hedef klasör bulunamadı", Toast.LENGTH_SHORT).show(); return }
        Thread {
            var n = 0
            files.forEach { f ->
                if (!f.exists() || f.parentFile == dest) return@forEach
                if (f.isDirectory && (dest.path + "/").startsWith(f.path + "/")) return@forEach
                var t = File(dest, f.name); var i = 1
                while (t.exists()) { t = File(dest, "${f.nameWithoutExtension}_$i" + (if (f.extension.isEmpty() || f.isDirectory) "" else "." + f.extension)); i++ }
                try { if (!f.renameTo(t)) { f.copyRecursively(t); f.deleteRecursively() }; n++ } catch (e: Exception) {}
            }
            runOnUiThread {
                Toast.makeText(this, if (n > 0) "$n öğe ${dest.name} klasörüne taşındı" else "Taşınacak öğe yok", Toast.LENGTH_SHORT).show()
                sel.clear()
                if (list != null) list = list!!.filter { it.exists() }
                if (cur != null || list != null) refresh()
            }
            scan()
        }.start()
    }

    private fun slotMenu(n: Int) {
        val f = File(prefs.getString("slot$n", null) ?: return)
        AlertDialog.Builder(this).setTitle(f.name).setItems(arrayOf("Başka klasör seç", "Kaldır")) { _, w ->
            if (w == 0) { slotPick = n; picking = true; browse(root) } else removeSlot(n)
        }.setNegativeButton("İptal", null).show()
    }

    private fun removeSlot(n: Int) { prefs.edit().remove("slot$n").apply(); slotsChanged() }
    private fun slotsChanged() { updateDock(); if (cur == null && list == null && !picking && !appsMode) home() }

    private fun home() {
        cur = null; list = null; sel.clear(); editMode = false; picking = false; appsMode = false; pendingShare = null; body.removeAllViews()
        chrome("Dosyalarım")
        val g = GridLayout(this).apply { columnCount = 3; setPadding(dp(6), dp(16), dp(6), dp(16)) }
        fun tile(ic: String, name: String, sub: String, click: () -> Unit, long: (() -> Unit)? = null, res: Drawable? = null, dashed: Boolean = false, big: Boolean = false) {
            val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(2), dp(8), dp(2), dp(12)) }
            val i: View = when {
                res != null -> ImageView(this).apply { setImageDrawable(res); if (!big) setPadding(dp(2), dp(2), dp(2), dp(2)) }
                dashed -> ImageView(this).apply {
                    setImageDrawable(Icons.add()); setColorFilter(0xFF888888.toInt()); setPadding(dp(30), dp(30), dp(30), dp(30))
                    background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(26).toFloat(); setStroke(dp(2), 0xFF555555.toInt(), dp(6).toFloat(), dp(4).toFloat()) }
                }
                else -> tv(ic, 36f, Color.WHITE).apply {
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply { setColor(card); cornerRadius = dp(20).toFloat(); setStroke(dp(1), 0xFF3A3A3A.toInt()) }
                }
            }
            val isz = if (big) 100 else 84
            val slot = FrameLayout(this).apply { addView(i, FrameLayout.LayoutParams(dp(isz), dp(isz), Gravity.CENTER)) }
            t.addView(slot, dp(100), dp(100))
            t.addView(tv(name, 17f, if (dashed) 0xFF888888.toInt() else 0xFFEEEEEE.toInt()).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
            t.setOnClickListener { click() }
            if (long != null) t.setOnLongClickListener { long(); true }
            g.addView(t, GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply { width = 0 })
        }
        fun cat(k: String, ic: String, n: String, res: Drawable? = null, big: Boolean = false) {
            tile(ic, n, "", { openList(n, cats[k] ?: emptyList(), true) }, null, res, false, big)
        }
        tile("", "Ana bellek", "", { browse(root) }, null, Icons.homeColor())
        tile("📥", "İndirilenler", "", { browse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)) }, null, Icons.folder())
        cat("img", "🖼", "Görüntüler", Icons.gallery(), true)
        tile("", "Uygulamalar", "", { openApps() }, null, Icons.androidApp(), false, true)
        tile("", "Metin düzenleyici", "", { openEditor(null) }, null, Icons.textEditor())
        pinList().filter { it.exists() }.forEach { f ->
            tile("", f.name, "", {
                if (f.isDirectory) browse(f) else openAny(f)
            }, {
                savePins(pinList().filter { it.path != f.path })
                Toast.makeText(this, "Sabitleme kaldırıldı: ${f.name}", Toast.LENGTH_SHORT).show(); home()
            }, if (f.isDirectory) Icons.folder() else Icons.file())
        }
        body.addView(ScrollView(this).apply { addView(g) })
    }


    @Suppress("DEPRECATION")
    private fun loadApps() {
        Thread {
            val pm = packageManager
            val l = pm.getInstalledPackages(0).mapNotNull { pi ->
                val ai = pi.applicationInfo ?: return@mapNotNull null
                var size = try { File(ai.sourceDir).length() } catch (e: Exception) { 0L }
                ai.splitSourceDirs?.forEach { size += File(it).length() }
                AppRow(ai.loadLabel(pm).toString(), pi.packageName, size, pi.firstInstallTime, ai)
            }.sortedByDescending { it.time }
            runOnUiThread { if (appsMode) { apps = l; appAdapter?.notifyDataSetChanged() } }
        }.start()
    }

    private fun openApps() {
        cur = null; list = null; sel.clear(); editMode = false; picking = false; appsMode = true
        chrome("Uygulamalar")
        apps = emptyList()
        val lv = ListView(this).apply { divider = ColorDrawable(0xFF2E2E2E.toInt()); dividerHeight = dp(1); setBackgroundColor(bg) }
        val ad = object : BaseAdapter() {
            override fun getCount() = apps.size
            override fun getItem(p: Int) = apps[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, v: View?, parent: android.view.ViewGroup?): View {
                val a = apps[p]
                val row = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(6), dp(12), dp(6)) }
                val ic = iconCache.getOrPut(a.pkg) { a.ai.loadIcon(packageManager) }
                row.addView(ImageView(this@MainActivity).apply { setImageDrawable(ic) }, dp(50), dp(50))
                val c = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
                c.addView(tv(a.label, 19f, Color.WHITE).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                c.addView(tv("${a.pkg} • ${sz(a.size)}", 14f, 0xFF999999.toInt()).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                row.addView(c, LinearLayout.LayoutParams(0, -2, 1f))
                return row
            }
        }
        appAdapter = ad
        lv.adapter = ad
        lv.setOnItemClickListener { _, _, p, _ ->
            try { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${apps[p].pkg}"))) }
            catch (e: Exception) { Toast.makeText(this, "Uygulama bilgisi açılamadı", Toast.LENGTH_SHORT).show() }
        }
        lv.setOnItemLongClickListener { _, _, p, _ ->
            try { startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${apps[p].pkg}"))) }
            catch (e: Exception) { Toast.makeText(this, "Kaldırılamadı", Toast.LENGTH_SHORT).show() }
            true
        }
        body.removeAllViews(); body.addView(lv)
        loadApps()
    }

    private fun pinList(): List<File> = (prefs.getString("hpins", "") ?: "").split("\n").filter { it.isNotEmpty() }.map { File(it) }
    private fun savePins(l: List<File>) { prefs.edit().putString("hpins", l.joinToString("\n") { it.path }).apply() }
    private fun pinSelected() {
        if (sel.isEmpty()) return
        val l = pinList().toMutableList(); var n = 0
        sel.forEach { f -> if (l.none { it.path == f.path }) { l.add(f); n++ } }
        savePins(l); sel.clear(); editMode = false
        Toast.makeText(this, if (n > 0) "$n öğe ana ekrana sabitlendi" else "Zaten sabitlenmiş", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun migratePins() {
        if (prefs.getBoolean("mig", false)) return
        val old = (prefs.getStringSet("pins", emptySet()) ?: emptySet()).sorted().take(4)
        val e = prefs.edit(); old.forEachIndexed { i, p -> e.putString("slot$i", p) }; e.putBoolean("mig", true).apply()
    }

    private fun setSlot(n: Int, f: File) { prefs.edit().putString("slot$n", f.path).apply(); slotsChanged() }

    private fun browse(d: File) { appsMode = false; cur = d; list = null; sel.clear(); editMode = false; refresh() }
    private fun openList(t: String, l: List<File>, g: Boolean = false) { appsMode = false; gal = g; list = l; listTitle = t; sel.clear(); editMode = false; refresh() }

    private fun items(): List<File> {
        val base = list ?: (cur?.listFiles()?.toList() ?: emptyList())
        var l = if (showHidden) base else base.filter { !it.name.startsWith(".") }
        if (picking) l = l.filter { it.isDirectory }
        return l.sortedByDescending { it.lastModified() }
    }

    private fun iconView(f: File): View =
        ImageView(this).apply { setImageDrawable(if (f.isDirectory) Icons.folder() else if (f.extension.lowercase() in TXT) Icons.docBlue() else Icons.file()); setPadding(dp(1), dp(1), dp(1), dp(1)) }

    private var curLv: ListView? = null
    private var curAdapter: BaseAdapter? = null
    private var curBarHost: android.widget.FrameLayout? = null

    private fun updateChrome() {
        chrome(if (editMode) "${sel.size} seçili" else if (list != null) listTitle else if (cur == root) "Ana bellek" else cur?.name ?: "", cur != null && list == null && !editMode)
    }

    /** Seçim değişince listeyi yeniden kurmadan yerinde günceller (kaydırma bozulmaz, titreme olmaz). */
    private fun selectionChanged() {
        val ad = curAdapter; val host = curBarHost
        if (ad == null || host == null || curLv?.isAttachedToWindow != true) { refresh(true); return }
        updateChrome()
        host.removeAllViews()
        if (sel.isNotEmpty()) host.addView(selectionBar(), -1, dp(46))
        ad.notifyDataSetChanged()
    }

    /** keepScroll=true: aynı listede kalındığında (seçim, tümünü seç, sil...) kaydırma konumunu korur. */
    private fun refresh(keepScroll: Boolean = false) {
        val oldLv = curLv
        var sPos = 0; var sTop = 0
        if (keepScroll && oldLv != null && oldLv.isAttachedToWindow) {
            sPos = oldLv.firstVisiblePosition
            sTop = oldLv.getChildAt(0)?.top ?: 0
        }
        curLv = null; curAdapter = null; curBarHost = null
        val l = items()
        updateChrome()
        if (list != null && gal) { body.removeAllViews(); body.addView(galleryView(l)); return }
        val lv = ListView(this).apply { divider = ColorDrawable(0xFF2E2E2E.toInt()); dividerHeight = dp(1); setBackgroundColor(bg) }
        curLv = lv
        lv.adapter = object : BaseAdapter() {
            override fun getCount() = l.size
            override fun getItem(p: Int) = l[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, v: View?, parent: android.view.ViewGroup?): View {
                val f = l[p]
                val row = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(5), dp(12), dp(5)); if (f in sel) setBackgroundColor(0xFF2C3E50.toInt()) }
                if (editMode) {
                    // Soldaki boş alan: seç/kaldır ve (seçiliyse) sürükle SADECE buradan yapılır. Seçili satır renkli görünür.
                    val hit = android.widget.FrameLayout(this@MainActivity).apply {
                        setOnClickListener { toggle(f) }
                        setOnLongClickListener {
                            // sadece seçili bir kutucuğa basılı tutunca seçilenleri alttaki sabit klasörlere sürükle
                            if (f in sel && pendingShare == null) {
                                val payload = sel.joinToString("\n") { it.absolutePath }
                                try { row.startDragAndDrop(ClipData.newPlainText("dosya", payload), View.DragShadowBuilder(row), null, 0) } catch (e: Exception) {}
                            }
                            true
                        }
                    }
                    row.addView(hit, LinearLayout.LayoutParams(dp(56), dp(56)))
                }
                row.addView(iconView(f), dp(48), dp(48))
                val c = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
                c.addView(tv(f.name, 19f, Color.WHITE).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                val sub = if (f.isDirectory) "${f.listFiles()?.size ?: 0} öğe" else sz(f.length())
                c.addView(tv("$sub • ${df.format(Date(f.lastModified()))}", 14f, 0xFF999999.toInt()))
                row.addView(c, LinearLayout.LayoutParams(0, -2, 1f))
                return row
            }
        }
        curAdapter = lv.adapter as? BaseAdapter
        lv.setOnItemClickListener { _, _, p, _ -> val f = l[p]; if (editMode) Unit else if (f.isDirectory) browse(f) else openAny(f) }
        lv.setOnItemLongClickListener { _, v, p, _ ->
            if (pendingShare != null) return@setOnItemLongClickListener true
            val f = l[p]
            if (editMode) {
                // seçim modunda satırın kendisi etkisiz; seç/sürükle sadece kutucuktan
            } else if (f.isFile && (f.extension.lowercase() in IMG || f.extension.lowercase() in VID)) {
                // resim/video: basılı tutunca onay ve bildirim olmadan sil
                if (removeMedia(f)) refresh(true)
            }
            true
        }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val barHost = android.widget.FrameLayout(this)
        if (sel.isNotEmpty()) barHost.addView(selectionBar(), -1, dp(46))
        wrap.addView(barHost, LinearLayout.LayoutParams(-1, -2))
        curBarHost = barHost
        wrap.addView(lv, LinearLayout.LayoutParams(-1, 0, 1f))
        if (pendingShare != null) wrap.addView(saveBar(), -1, -2)
        else if (picking) wrap.addView(Button(this).apply { text = "Bu klasörü seç"; setOnClickListener { if (slotPick in 0..3) setSlot(slotPick, cur!!); slotPick = -1; home() } }, -1, -2)
        else if (clip.isNotEmpty() && cur != null && list == null && !editMode) wrap.addView(pasteBar(), -1, dp(52))
        if (keepScroll && (sPos > 0 || sTop != 0)) lv.setSelectionFromTop(minOf(sPos, maxOf(0, l.size - 1)), sTop)
        curLv = lv
        body.removeAllViews(); body.addView(wrap)
    }


    // ======================= GALERİ (resim + video) =======================
    private val ui = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(3)
    private val fullPool = Executors.newFixedThreadPool(2)
    private val thumbs = object : LruCache<String, Bitmap>(maxOf(4096, (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt())) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private fun isVid(f: File) = f.extension.lowercase() in VID

    /** fit=true: en büyük kenar maxDim'i geçmesin (tam ekran). fit=false: en küçük kenar >= maxDim (küçük resim). */
    private fun decodeImg(f: File, maxDim: Int, fit: Boolean): Bitmap? {
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, o)
            if (o.outWidth <= 0 || o.outHeight <= 0) return null
            var s = 1
            if (fit) { while (maxOf(o.outWidth, o.outHeight) / s > maxDim) s *= 2 }
            else { while (minOf(o.outWidth, o.outHeight) / (s * 2) >= maxDim) s *= 2 }
            val b = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
            val deg = try {
                when (ExifInterface(f.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } catch (e: Exception) { 0 }
            if (deg == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg.toFloat()) }, true)
        } catch (e: Throwable) { null }
    }

    private fun videoFrame(f: File, target: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.path)
            val bm = r.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: r.getFrameAtTime(0) ?: return null
            val sc = target.toFloat() / minOf(bm.width, bm.height)
            if (sc < 1f) Bitmap.createScaledBitmap(bm, maxOf(1, (bm.width * sc).toInt()), maxOf(1, (bm.height * sc).toInt()), true) else bm
        } catch (e: Throwable) { null } finally { try { r.release() } catch (e: Exception) {} }
    }

    private fun loadThumb(f: File): Bitmap? {
        thumbs.get(f.path)?.let { return it }
        val b = if (isVid(f)) videoFrame(f, 360) else decodeImg(f, 360, false)
        if (b != null) thumbs.put(f.path, b)
        return b
    }

    /** Dosyayı diskten, ana listeden ve MediaStore'dan siler. Onay sormaz. */
    private fun removeMedia(f: File): Boolean {
        val ok = try { f.delete() } catch (e: Exception) { false }
        if (ok) {
            cats = cats.mapValues { e -> e.value.filter { it.path != f.path } }
            list = list?.filter { it.path != f.path }
            thumbs.remove(f.path)
            try { MediaScannerConnection.scanFile(this, arrayOf(f.path), null, null) } catch (e: Exception) {}
        } else Toast.makeText(this, "Silinemedi: ${f.name}", Toast.LENGTH_SHORT).show()
        return ok
    }

    private fun shareOne(f: File) {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: if (isVid(f)) "video/*" else "image/*"
        try {
            val u = uri(f)
            val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            i.clipData = ClipData.newRawUri("", u)
            startActivity(Intent.createChooser(i, "Paylaş"))
        } catch (e: Exception) { Toast.makeText(this, "Paylaşılamadı", Toast.LENGTH_SHORT).show() }
    }

    private fun iconBtn(ic: Drawable, desc: String, rot: Float, click: () -> Unit): ImageView =
        ImageView(this).apply {
            setImageDrawable(ic); setColorFilter(Color.WHITE); rotation = rot; contentDescription = desc
            setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { click() }
        }

    /** Kare hücre: küçük resim + videolarda oynat rozeti. */
    private inner class Cell : FrameLayout(this@MainActivity) {
        val img = ImageView(this@MainActivity).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setBackgroundColor(0xFF2A2A2A.toInt()) }
        val badge = ImageView(this@MainActivity).apply {
            setImageDrawable(Icons.play()); setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply { setShape(GradientDrawable.OVAL); setColor(0x99000000.toInt()) }
        }
        init {
            addView(img, FrameLayout.LayoutParams(-1, -1))
            addView(badge, FrameLayout.LayoutParams(dp(26), dp(26), Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(5), 0, 0, dp(5)) })
        }
        override fun onMeasure(w: Int, h: Int) { super.onMeasure(w, w) }
    }

    private fun galleryView(l: List<File>): View {
        val data = l.toMutableList()
        if (data.isEmpty()) return tv("Görüntü veya video bulunamadı", 16f, 0xFF999999.toInt()).apply { gravity = Gravity.CENTER }
        val ad = object : BaseAdapter() {
            override fun getCount() = data.size
            override fun getItem(p: Int) = data[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, v: View?, parent: ViewGroup?): View {
                val c = (v as? Cell) ?: Cell()
                val f = data[p]
                c.badge.visibility = if (isVid(f)) View.VISIBLE else View.GONE
                c.img.tag = f.path
                val hit = thumbs.get(f.path)
                if (hit != null) c.img.setImageBitmap(hit)
                else {
                    c.img.setImageDrawable(null)
                    pool.execute {
                        if (c.img.tag == f.path) {
                            val b = loadThumb(f)
                            if (b != null) runOnUiThread { if (c.img.tag == f.path) c.img.setImageBitmap(b) }
                        }
                    }
                }
                return c
            }
        }
        val gap = dp(2)
        val gv = GridView(this).apply {
            numColumns = 3; horizontalSpacing = gap; verticalSpacing = gap
            stretchMode = GridView.STRETCH_COLUMN_WIDTH; setBackgroundColor(bg)
            selector = ColorDrawable(Color.TRANSPARENT); adapter = ad
        }
        gv.setOnItemClickListener { _, _, p, _ -> if (p in data.indices) Viewer(data, p) { ad.notifyDataSetChanged() } }
        gv.setOnItemLongClickListener { _, _, p, _ ->
            if (p in data.indices && removeMedia(data[p])) { data.removeAt(p); ad.notifyDataSetChanged() }
            true
        }
        return gv
    }

    /** Tam ekran sayfa: resim ya da video. Video sayfa seçilince otomatik başlar. */
    private inner class Pg(val f: File, val onLong: () -> Unit) {
        val vid = isVid(f)
        val root = FrameLayout(this@MainActivity).apply { setBackgroundColor(Color.BLACK) }
        val img = ImageView(this@MainActivity).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val play = ImageView(this@MainActivity).apply {
            setImageDrawable(Icons.play()); setPadding(dp(20), dp(20), dp(20), dp(20)); visibility = View.GONE
            background = GradientDrawable().apply { setShape(GradientDrawable.OVAL); setColor(0x99000000.toInt()) }
        }
        val seek = SeekBar(this@MainActivity).apply { max = 100 }
        var vv: VideoView? = null
        var dead = false
        var active = false
        var seeking = false
        val tick: Runnable = object : Runnable {
            override fun run() {
                val v = vv
                if (v == null || dead || !active) return
                val d = v.duration
                if (d > 0 && !seeking) { seek.max = d; seek.progress = v.currentPosition }
                ui.postDelayed(this, 250)
            }
        }

        fun activate() {
            if (!vid || dead) return
            active = true
            val v = vv ?: VideoView(this@MainActivity).also { vv = it; root.addView(it, 0, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER)) }
            img.visibility = View.VISIBLE; play.visibility = View.GONE; seek.progress = 0
            v.setOnPreparedListener { mp ->
                mp.isLooping = false
                if (active) { v.start(); ui.postDelayed({ if (active && !dead) img.visibility = View.GONE }, 500) }
            }
            v.setOnInfoListener { _, what, _ -> if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) img.visibility = View.GONE; false }
            v.setOnCompletionListener { play.visibility = View.VISIBLE }
            v.setOnErrorListener { _, _, _ ->
                Toast.makeText(this@MainActivity, "Video oynatılamadı", Toast.LENGTH_SHORT).show()
                img.visibility = View.VISIBLE; true
            }
            v.setVideoURI(Uri.fromFile(f))
            ui.removeCallbacks(tick); ui.post(tick)
        }

        fun deactivate() {
            active = false; ui.removeCallbacks(tick)
            vv?.let { try { it.stopPlayback() } catch (e: Exception) {}; root.removeView(it) }
            vv = null
            if (vid) { img.visibility = View.VISIBLE; play.visibility = View.GONE; seek.progress = 0 }
        }

        fun dispose() { dead = true; deactivate() }

        fun pause() {
            val v = vv ?: return
            if (active && v.isPlaying) { v.pause(); play.visibility = View.VISIBLE }
        }

        fun toggle() {
            if (!vid || !active) return
            val v = vv ?: return
            if (v.isPlaying) { v.pause(); play.visibility = View.VISIBLE }
            else { v.start(); play.visibility = View.GONE; ui.removeCallbacks(tick); ui.post(tick) }
        }

        init {
            root.addView(img, FrameLayout.LayoutParams(-1, -1))
            if (vid) {
                root.addView(play, FrameLayout.LayoutParams(dp(76), dp(76), Gravity.CENTER))
                root.addView(seek, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(12), 0, dp(12), dp(16)) })
                seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(sb: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) vv?.seekTo(p) }
                    override fun onStartTrackingTouch(sb: SeekBar?) { seeking = true }
                    override fun onStopTrackingTouch(sb: SeekBar?) { seeking = false }
                })
            }
            root.isClickable = true
            root.setOnClickListener { toggle() }
            root.setOnLongClickListener { onLong(); true }
            fullPool.execute {
                val b = if (vid) loadThumb(f) else decodeImg(f, 2400, true)
                runOnUiThread {
                    if (!dead) {
                        if (b != null) img.setImageBitmap(b)
                        else if (!vid) { img.setPadding(dp(110), dp(110), dp(110), dp(110)); img.setImageDrawable(Icons.file()) }
                    }
                }
            }
        }
    }

    /** Tam ekran gezgin: parmakla kaydırma + üst barda geri/ileri + paylaş. */
    private inner class Viewer(val data: MutableList<File>, start: Int, val changed: () -> Unit) {
        val dlg = Dialog(this@MainActivity, android.R.style.Theme_Black_NoTitleBar)
        val holders = HashMap<Int, Pg>()
        var cur = start
        val pager = ViewPager(this@MainActivity)
        val nameV = tv("", 16f, Color.WHITE).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; typeface = Typeface.DEFAULT_BOLD }
        val countV = tv("", 12f, 0xFFAAAAAA.toInt())
        val prevB = iconBtn(Icons.chevron(), "Önceki", 180f) { go(-1) }
        val nextB = iconBtn(Icons.chevron(), "Sonraki", 0f) { go(1) }

        val adapter = object : PagerAdapter() {
            override fun getCount() = data.size
            override fun isViewFromObject(v: View, o: Any) = v === o
            override fun getItemPosition(o: Any) = PagerAdapter.POSITION_NONE
            override fun instantiateItem(c: ViewGroup, p: Int): Any {
                val f = data[p]
                val pg = Pg(f) { delFile(f) }
                holders[p] = pg
                c.addView(pg.root, -1, -1)
                if (p == cur) pg.activate()
                return pg.root
            }
            override fun destroyItem(c: ViewGroup, p: Int, o: Any) {
                val v = o as View
                val e = holders.entries.firstOrNull { it.value.root === v }
                if (e != null) { e.value.dispose(); holders.remove(e.key) }
                c.removeView(v)
            }
        }

        fun refreshBar() {
            if (data.isEmpty()) return
            nameV.text = data[cur].name
            countV.text = "${cur + 1} / ${data.size}"
            prevB.alpha = if (cur > 0) 1f else 0.3f
            nextB.alpha = if (cur < data.size - 1) 1f else 0.3f
        }

        fun go(d: Int) { val n = cur + d; if (n in data.indices) pager.setCurrentItem(n, true) }

        fun delFile(f: File) {
            val i = data.indexOf(f)
            if (i < 0 || !removeMedia(f)) return
            data.removeAt(i)
            if (data.isEmpty()) { dlg.dismiss(); return }
            cur = minOf(i, data.size - 1)
            adapter.notifyDataSetChanged()
            pager.setCurrentItem(cur, false)
            refreshBar()
        }

        fun pauseVideo() { holders[cur]?.pause() }

        init {
            val top = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(bar); setPadding(dp(4), 0, dp(4), 0) }
            top.addView(iconBtn(Icons.back(), "Geri", 0f) { dlg.dismiss() }, LinearLayout.LayoutParams(dp(44), dp(44)))
            val col = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), 0, dp(6), 0) }
            col.addView(nameV); col.addView(countV)
            top.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
            top.addView(prevB, LinearLayout.LayoutParams(dp(44), dp(44)))
            top.addView(nextB, LinearLayout.LayoutParams(dp(44), dp(44)))
            top.addView(iconBtn(Icons.share(), "Paylaş", 0f) { if (data.isNotEmpty()) shareOne(data[cur]) }, LinearLayout.LayoutParams(dp(44), dp(44)))
            val rootV = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
            rootV.addView(top, LinearLayout.LayoutParams(-1, dp(54)))
            rootV.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))
            pager.adapter = adapter
            pager.setCurrentItem(cur, false)
            pager.addOnPageChangeListener(object : ViewPager.SimpleOnPageChangeListener() {
                override fun onPageSelected(p: Int) {
                    if (p != cur) { holders[cur]?.deactivate(); cur = p; holders[p]?.activate() }
                    refreshBar()
                }
            })
            dlg.setContentView(rootV)
            dlg.window?.apply {
                setLayout(-1, -1); setBackgroundDrawable(ColorDrawable(Color.BLACK))
                addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            dlg.setOnDismissListener {
                holders.values.toList().forEach { it.dispose() }; holders.clear()
                if (this@MainActivity.viewer === this@Viewer) this@MainActivity.viewer = null
                changed()
            }
            this@MainActivity.viewer = this@Viewer
            refreshBar()
            dlg.show()
        }
    }

    override fun onPause() { super.onPause(); viewer?.pauseVideo() }
    override fun onDestroy() { viewer?.dlg?.dismiss(); editor?.dismiss(); super.onDestroy() }

    private fun barBtn(ic: Drawable, label: String, enabled: Boolean, act: () -> Unit): View {
        val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; alpha = if (enabled) 1f else 0.35f }
        b.addView(ImageView(this).apply { setImageDrawable(ic); setColorFilter(Color.WHITE); contentDescription = label.replace("\n", " ") }, dp(24), dp(24))
        b.setOnClickListener { act() }
        return b
    }

    private fun selectionBar(): View {
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(0xFF2A2A2A.toInt()) }
        fun add(ic: Drawable, label: String, enabled: Boolean = true, act: () -> Unit) =
            bar.addView(barBtn(ic, label, enabled, act), LinearLayout.LayoutParams(0, -1, 1f))
        add(Icons.rename(), "Yeniden\nadlandır", sel.size == 1) {
            if (sel.size != 1) { Toast.makeText(this, "Yeniden adlandırmak için tek öğe seçin", Toast.LENGTH_SHORT).show(); return@add }
            val f = sel.first()
            input("Yeni ad", f.name) { n -> if (n.isNotEmpty()) f.renameTo(File(f.parentFile, n)); sel.clear(); if (list != null) list = list!!.filter { it.exists() }; refresh(); scan() }
        }
        add(Icons.delete(), "Sil") {
            AlertDialog.Builder(this).setMessage("${sel.size} öğe silinsin mi?").setPositiveButton("Sil") { _, _ ->
                val x = sel.toList(); sel.clear()
                Thread { x.forEach { it.deleteRecursively() }; runOnUiThread { if (list != null) list = list!!.filter { it.exists() }; refresh() }; scan() }.start()
            }.setNegativeButton("İptal", null).show()
        }
        add(Icons.share(), "Paylaş") {
            if (sel.none { it.isFile }) Toast.makeText(this, "Klasörleri paylaşmak için önce sıkıştırın", Toast.LENGTH_SHORT).show() else share()
        }
        add(Icons.move(), "Taşı") { clip = sel.toList(); cut = true; sel.clear(); editMode = false; refresh(); Toast.makeText(this, "Hedef klasöre gidip Yapıştır'a basın", Toast.LENGTH_SHORT).show() }
        add(Icons.copy(), "Kopyala") { clip = sel.toList(); cut = false; sel.clear(); editMode = false; refresh(); Toast.makeText(this, "Hedef klasöre gidip Yapıştır'a basın", Toast.LENGTH_SHORT).show() }
        add(Icons.zip(), "Sıkıştır") { zipSelected() }
        val zips = sel.filter { it.isFile && it.extension.lowercase() == "zip" }
        if (zips.isNotEmpty()) add(Icons.unzip(), "Zip'i\naç") { val z = zips.toList(); sel.clear(); z.forEach { unzip(it) } }
        return bar
    }

    private fun pasteBar(): View {
        val b = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(0xFF2A2A2A.toInt()); setPadding(dp(16), 0, dp(8), 0) }
        b.addView(ImageView(this).apply { setImageDrawable(Icons.paste()); setColorFilter(Color.WHITE) }, dp(24), dp(24))
        b.addView(tv("Buraya ${if (cut) "taşı" else "kopyala"} (${clip.size})", 15f, Color.WHITE).apply { setPadding(dp(12), 0, 0, 0) }, LinearLayout.LayoutParams(0, -2, 1f))
        b.setOnClickListener { paste() }
        b.addView(ImageView(this).apply { setImageDrawable(Icons.newFolder()); setColorFilter(Color.WHITE); setPadding(dp(10), dp(10), dp(10), dp(10)); contentDescription = "Yeni klasör"; setOnClickListener { newFolder() } }, dp(44), dp(44))
        b.addView(ImageView(this).apply { setImageDrawable(Icons.close()); setColorFilter(0xFFBBBBBB.toInt()); setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { clip = listOf(); refresh() } }, dp(44), dp(44))
        return b
    }

    /** Şu an açık olan klasörde yeni klasör oluşturur. */
    private fun newFolder() {
        val d = cur
        if (d == null || list != null) { Toast.makeText(this, "Yeni klasör için bir klasörün içine girin", Toast.LENGTH_SHORT).show(); return }
        input("Yeni klasör", "Yeni klasör") { n ->
            val name = n.replace("/", "_")
            if (name.isNotEmpty()) {
                val t = uniq(d, name)
                if (t.mkdirs()) refresh(true) else Toast.makeText(this, "Klasör oluşturulamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun uniq(d: File, name: String): File {
        var t = File(d, name); var n = 1
        val base = name.substringBeforeLast('.', name); val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        while (t.exists()) { t = File(d, "${base}_$n$ext"); n++ }
        return t
    }

    private fun zipSelected() {
        val items = sel.toList(); if (items.isEmpty()) return
        val dir = (if (list == null) cur else null) ?: items.first().parentFile ?: return
        val base = if (items.size == 1) (if (items[0].isDirectory) items[0].name else items[0].nameWithoutExtension) else "Arsiv"
        val out = uniq(dir, "$base.zip"); sel.clear()
        Toast.makeText(this, "Sıkıştırılıyor…", Toast.LENGTH_SHORT).show()
        Thread {
            var ok = true
            try {
                ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { z ->
                    fun add(f: File, path: String) {
                        if (f.absoluteFile == out.absoluteFile) return
                        if (f.isDirectory) {
                            val kids = f.listFiles() ?: emptyArray()
                            if (kids.isEmpty()) { z.putNextEntry(ZipEntry("$path/")); z.closeEntry() }
                            kids.forEach { add(it, "$path/${it.name}") }
                        } else {
                            try { f.inputStream().use { i -> z.putNextEntry(ZipEntry(path)); i.copyTo(z); z.closeEntry() } } catch (e: IOException) {}
                        }
                    }
                    items.forEach { add(it, it.name) }
                }
            } catch (e: Exception) { ok = false; out.delete() }
            runOnUiThread {
                Toast.makeText(this, if (ok) "Oluşturuldu: ${out.name}" else "Sıkıştırma başarısız", Toast.LENGTH_LONG).show()
                if (list != null) list = list!! + (if (ok) listOf(out) else emptyList())
                refresh()
            }
            scan()
        }.start()
    }

    private fun unzip(z: File) {
        val parent = z.parentFile ?: return
        val dest = uniq(parent, z.nameWithoutExtension)
        Toast.makeText(this, "Çıkarılıyor…", Toast.LENGTH_SHORT).show()
        Thread {
            var ok = true
            try {
                dest.mkdirs(); val dp = dest.canonicalPath + File.separator
                ZipInputStream(BufferedInputStream(FileInputStream(z))).use { zin ->
                    var e = zin.nextEntry
                    while (e != null) {
                        val o = File(dest, e.name)
                        if (!o.canonicalPath.startsWith(dp)) throw SecurityException("zip-slip")
                        if (e.isDirectory) o.mkdirs() else { o.parentFile?.mkdirs(); FileOutputStream(o).use { zin.copyTo(it) } }
                        zin.closeEntry(); e = zin.nextEntry
                    }
                }
            } catch (e: Exception) { ok = false; dest.deleteRecursively() }
            runOnUiThread {
                Toast.makeText(this, if (ok) "Çıkarıldı: ${dest.name}" else "Zip açılamadı", Toast.LENGTH_LONG).show()
                if (list != null) list = list!! + (if (ok) listOf(dest) else emptyList())
                refresh()
            }
            scan()
        }.start()
    }

    private fun zipDialog(f: File) {
        AlertDialog.Builder(this).setTitle(f.name).setItems(arrayOf("Buraya çıkar", "Başka uygulamayla aç")) { _, w -> if (w == 0) unzip(f) else open(f) }
            .setNegativeButton("İptal", null).show()
    }

    private fun toggle(f: File) { if (!sel.remove(f)) sel.add(f); selectionChanged() }

    private fun uri(f: File) = KFileProvider.uriFor(this, f)

    private fun isText(f: File) = f.extension.lowercase() in TXT && f.length() <= 1_000_000

    private fun openAny(f: File) {
        if (f.extension.lowercase() == "zip") zipDialog(f) else if (isText(f)) openEditor(f) else open(f)
    }

    private fun openEditor(f: File?) {
        editor?.dismiss()
        val dir = f?.parentFile ?: cur ?: File(root, "Documents")
        editor = TextEditor(this, f, dir) {
            if (cur != null || list != null) refresh(true)
            scan()
        }.also { it.show() }
    }

    private fun open(f: File) {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
        try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri(f), mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        catch (e: ActivityNotFoundException) { Toast.makeText(this, "Açacak uygulama yok", Toast.LENGTH_SHORT).show() }
    }

    private fun share() {
        val us = ArrayList(sel.filter { it.isFile }.map { uri(it) })
        if (us.isEmpty()) return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, us).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Paylaş"))
    }

    private fun input(t: String, init: String, ok: (String) -> Unit) {
        val e = EditText(this).apply { setText(init); setSingleLine() }
        AlertDialog.Builder(this).setTitle(t).setView(e).setPositiveButton("Tamam") { _, _ -> ok(e.text.toString().trim()) }.setNegativeButton("İptal", null).show()
    }

    private fun paste() {
        val d = cur ?: return; val its = clip; val mv = cut; clip = listOf()
        Thread {
            its.forEach { f ->
                var t = File(d, f.name); var n = 1
                while (t.exists()) { t = File(d, "${f.nameWithoutExtension}_kopya$n" + (if (f.extension.isEmpty()) "" else "." + f.extension)); n++ }
                if (f.isDirectory && (t.path + "/").startsWith(f.path + "/")) return@forEach
                try { if (mv) { if (!f.renameTo(t)) { f.copyRecursively(t); f.deleteRecursively() } } else f.copyRecursively(t) } catch (e: Exception) {}
            }
            runOnUiThread { refresh() }; scan()
        }.start()
    }

    // ======================= PAYLAŞIMLA GELEN DOSYALARI KAYDET =======================
    @Suppress("DEPRECATION")
    private fun handleShare(i: Intent?) {
        if (i == null || (i.action != Intent.ACTION_SEND && i.action != Intent.ACTION_SEND_MULTIPLE)) return
        val us = LinkedHashSet<Uri>()
        try {
            if (i.action == Intent.ACTION_SEND) i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { us.add(it) }
            else i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { us.addAll(it) }
        } catch (e: Exception) {}
        i.clipData?.let { cd -> for (k in 0 until cd.itemCount) cd.getItemAt(k).uri?.let { us.add(it) } }
        if (us.isEmpty()) { Toast.makeText(this, "Paylaşılan dosya bulunamadı", Toast.LENGTH_SHORT).show(); return }
        pendingShare = us.toList()
        slotPick = -1; picking = true; appsMode = false; list = null; sel.clear(); editMode = false
        val last = File(prefs.getString("lastSave", null) ?: "")
        browse(if (last.isDirectory) last else root)
    }

    private fun cancelShare() { pendingShare = null; picking = false; finish() }

    private fun saveBar(): View {
        val n = pendingShare?.size ?: 0
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF2A2A2A.toInt()); setPadding(dp(8), dp(8), dp(8), dp(8)) }
        box.addView(tv("$n dosya kaydedilecek • klasör seçin", 14f, 0xFFCCCCCC.toInt()).apply { setPadding(dp(4), 0, 0, dp(4)) })
        val row = LinearLayout(this)
        row.addView(Button(this).apply { text = "İptal"; setOnClickListener { cancelShare() } }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply {
            text = "Yeni klasör"
            setOnClickListener {
                val d = cur ?: root
                input("Klasör adı", "") { nm ->
                    val safe = nm.replace('/', '_').replace('\\', '_').trim()
                    if (safe.isEmpty() || safe == "." || safe == "..") return@input
                    val nd = File(d, safe)
                    if (nd.isDirectory || nd.mkdirs()) browse(nd) else Toast.makeText(this@MainActivity, "Klasör oluşturulamadı", Toast.LENGTH_SHORT).show()
                }
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(Button(this).apply { text = "Buraya kaydet"; setOnClickListener { saveShared(cur ?: root) } }, LinearLayout.LayoutParams(0, -2, 1.3f))
        box.addView(row)
        return box
    }

    private fun shareName(u: Uri): String {
        var name: String? = null
        if (u.scheme == "content") try {
            contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) name = c.getString(0) }
        } catch (e: Exception) {}
        var s: String = name?.takeIf { it.isNotBlank() } ?: u.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "dosya_${System.currentTimeMillis()}"
        s = s.replace('/', '_').replace('\\', '_').trim()
        if (s.isEmpty() || s == "." || s == "..") s = "dosya_${System.currentTimeMillis()}"
        if (!s.contains('.')) {
            val ext = try { contentResolver.getType(u)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) } } catch (e: Exception) { null }
            if (ext != null) s += ".$ext"
        }
        return s
    }

    private fun saveShared(dest: File) {
        val us = pendingShare ?: return
        if (!dest.isDirectory || !dest.canWrite()) { Toast.makeText(this, "Bu klasöre yazılamıyor", Toast.LENGTH_SHORT).show(); return }
        pendingShare = null; picking = false
        prefs.edit().putString("lastSave", dest.path).apply()
        Toast.makeText(this, "Kaydediliyor…", Toast.LENGTH_SHORT).show()
        Thread {
            val saved = ArrayList<String>()
            us.forEach { u ->
                var out: File? = null
                try {
                    val o = uniq(dest, shareName(u)); out = o
                    val ins = contentResolver.openInputStream(u) ?: throw IOException("açılamadı")
                    ins.use { i -> FileOutputStream(o).use { i.copyTo(it) } }
                    saved.add(o.path)
                } catch (e: Exception) { try { out?.delete() } catch (x: Exception) {} }
            }
            runOnUiThread {
                val msg = when {
                    saved.isEmpty() -> "Dosya kaydedilemedi"
                    saved.size == us.size -> "${saved.size} dosya ${dest.name} klasörüne kaydedildi"
                    else -> "${saved.size}/${us.size} dosya kaydedildi"
                }
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                browse(dest)
            }
            if (saved.isNotEmpty()) try { MediaScannerConnection.scanFile(this, saved.toTypedArray(), null, null) } catch (e: Exception) {}
            scan()
        }.start()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            pendingShare != null -> { val c = cur; if (c == null || c == root || c.parentFile?.canRead() != true) cancelShare() else browse(c.parentFile!!) }
            editMode -> { sel.clear(); editMode = false; refresh() }
            appsMode -> home()
            list != null -> { list = null; if (cur != null) refresh() else home() }
            cur != null -> { val c = cur!!; if (c == root || c.parentFile?.canRead() != true) home() else browse(c.parentFile!!) }
            else -> super.onBackPressed()
        }
    }
}
