package io.github.idrisokys.filemanager

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.TextUtils
import android.view.DragEvent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {
    private val bg = 0xFF121212.toInt(); private val bar = 0xFF212121.toInt(); private val card = 0xFF1E1E1E.toInt()
    private val accent = 0xFFFF9100.toInt()
    private lateinit var titleV: TextView; private lateinit var moreB: TextView; private lateinit var body: FrameLayout
    private lateinit var crumbScroll: HorizontalScrollView; private lateinit var crumbRow: LinearLayout
    private lateinit var lv: ListView; private lateinit var listWrap: LinearLayout; private lateinit var pickBtn: Button
    private lateinit var barV: LinearLayout
    private var cur: File? = null; private var list: List<File>? = null; private var listTitle = ""
    private var picking = false; private var pickBack: (() -> Unit)? = null
    private var shown: List<File> = emptyList()
    private val sel = linkedSetOf<File>()
    private var clip = listOf<File>(); private var cut = false
    private var asked = false
    private val root: File = Environment.getExternalStorageDirectory()
    private val prefs by lazy { getSharedPreferences("p", 0) }
    private var cats = mapOf<String, List<File>>()
    private val IMG = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg")
    private val VID = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v")
    private val AUD = setOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "opus")
    private val DOC = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "odt", "rtf", "csv", "epub", "md", "log", "json", "xml", "html")
    private val ARC = setOf("zip", "rar", "7z", "tar", "gz")
    private val df = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()
    private fun tv(t: String, s: Float, c: Int) = TextView(this).apply { text = t; textSize = s; setTextColor(c) }
    private fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_SHORT).show()
    private fun sz(b: Long): String {
        var v = b.toDouble(); var i = 0; val u = arrayOf("B", "KB", "MB", "GB")
        while (v >= 1024 && i < 3) { v /= 1024; i++ }
        return if (i == 0) "$b B" else String.format("%.1f %s", v, u[i])
    }

    private fun iconFor(f: File): Drawable {
        if (f.isDirectory) return GlyphDrawable(GlyphDrawable.FOLDER)
        val e = f.extension.lowercase()
        return when {
            e in DOC -> GlyphDrawable(GlyphDrawable.DOC, 0xFF4F7CAC.toInt())
            e in IMG -> GlyphDrawable(GlyphDrawable.FILE, 0xFF26A69A.toInt())
            e in VID -> GlyphDrawable(GlyphDrawable.FILE, 0xFFE57373.toInt())
            e in AUD -> GlyphDrawable(GlyphDrawable.FILE, 0xFFAB7BD6.toInt())
            e == "apk" -> GlyphDrawable(GlyphDrawable.FILE, 0xFF7CB342.toInt())
            e in ARC -> GlyphDrawable(GlyphDrawable.FILE, 0xFFB0846A.toInt())
            else -> GlyphDrawable(GlyphDrawable.FILE)
        }
    }

    private val fileAdapter = object : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(p: Int): Any = shown[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, v: View?, parent: ViewGroup?): View {
            val f = shown[p]
            val row = LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(56)
                setPadding(dp(6), dp(6), dp(12), dp(6))
                if (f in sel) setBackgroundColor(0xFF2C3E50.toInt())
            }
            row.addView(ImageView(this@MainActivity).apply { setImageDrawable(iconFor(f)) }, dp(44), dp(40))
            val c = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), 0, 0, 0) }
            c.addView(tv(f.name, 16f, Color.WHITE).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            val sub = if (f.isDirectory) "${f.listFiles()?.size ?: 0} öğe" else sz(f.length())
            c.addView(tv("$sub • ${df.format(Date(f.lastModified()))}", 13f, 0xFF9E9E9E.toInt()).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
            row.addView(c, LinearLayout.LayoutParams(0, -2, 1f))
            return row
        }
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.statusBarColor = Color.BLACK; window.navigationBarColor = bar
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }

        // Üst çubuk: ana ekranda başlık, klasörlerde yol (breadcrumb)
        val tb = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(bar); setPadding(dp(6), 0, 0, 0) }
        titleV = tv("", 20f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD; maxLines = 1; ellipsize = TextUtils.TruncateAt.END; setPadding(dp(10), 0, 0, 0) }
        crumbRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        crumbScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(crumbRow, ViewGroup.LayoutParams(-2, -1)) }
        moreB = tv("⋮", 26f, Color.WHITE).apply { gravity = Gravity.CENTER; setOnClickListener { menu(it) } }
        tb.addView(titleV, LinearLayout.LayoutParams(0, -2, 1f))
        tb.addView(crumbScroll, LinearLayout.LayoutParams(0, -1, 1f))
        tb.addView(moreB, dp(48), dp(56))

        body = FrameLayout(this)

        // Dosya listesi (bir kez oluşturulur, kaydırma konumu seçimde bozulmaz)
        lv = ListView(this).apply {
            divider = ColorDrawable(0xFF2A2A2A.toInt()); dividerHeight = 1; setBackgroundColor(bg)
            adapter = fileAdapter
            setOnItemClickListener { _, _, p, _ -> val f = shown[p]; if (sel.isNotEmpty()) toggle(f) else if (f.isDirectory) browse(f) else open(f) }
            setOnItemLongClickListener { _, v, p, _ ->
                if (!picking) { sel.add(shown[p]); refresh(); startDrag(v) }
                true
            }
        }
        pickBtn = Button(this).apply {
            text = "Bu klasörü çubuğa ekle"; visibility = View.GONE
            setOnClickListener { val b = pickBack; pin(cur!!); if (b != null) b() else home() }
        }
        listWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(lv, LinearLayout.LayoutParams(-1, 0, 1f)); addView(pickBtn, -1, -2)
        }

        // Alt çubuk: her ekranda sabit, 4 klasör + ekle butonu
        barV = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(bar) }
        val barWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(View(context).apply { setBackgroundColor(0xFF333333.toInt()) }, -1, 1)
            addView(barV, -1, dp(64))
        }

        col.addView(tb, -1, dp(56))
        col.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(barWrap, -1, -2)
        setContentView(col)
        buildBar(); home()
    }

    private fun hasPerm() = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
    else checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        if (hasPerm()) { if (cats.isEmpty()) scan() }
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
            root.walkTopDown().onEnter { !it.name.startsWith(".") && it != skip }.filter { it.isFile }.forEach { all.add(it) }
            val m = HashMap<String, List<File>>()
            m["img"] = all.filter { it.extension.lowercase() in IMG }
            m["apk"] = all.filter { it.extension.lowercase() == "apk" }
            m["new"] = all.sortedByDescending { it.lastModified() }.take(200)
            cats = m
            runOnUiThread { if (cur == null && list == null && !picking) home() }
        }.start()
    }

    // ---------- Üst çubuk ----------

    private fun chrome() {
        val browsing = cur != null || list != null
        moreB.visibility = if (sel.isNotEmpty() || (cur != null && list == null)) View.VISIBLE else View.GONE
        if (sel.isNotEmpty() || !browsing) {
            crumbScroll.visibility = View.GONE; titleV.visibility = View.VISIBLE
            titleV.text = if (sel.isNotEmpty()) "${sel.size} seçili" else "Dosya Yöneticisi"
            return
        }
        titleV.visibility = View.GONE; crumbScroll.visibility = View.VISIBLE
        buildCrumbs()
    }

    private fun buildCrumbs() {
        crumbRow.removeAllViews()
        val grey = 0xFF9A9A9A.toInt()
        fun icon(d: Drawable, box: Int, pad: Int, go: () -> Unit) {
            crumbRow.addView(ImageView(this).apply {
                setImageDrawable(d); setPadding(dp(pad), dp(pad), dp(pad), dp(pad)); setOnClickListener { go() }
            }, dp(box), dp(box))
        }
        fun chev() {
            crumbRow.addView(ImageView(this).apply { setImageDrawable(GlyphDrawable(GlyphDrawable.CHEVRON, 0xFF8C8C8C.toInt())) }, dp(22), dp(28))
        }
        fun seg(t: String, last: Boolean, go: () -> Unit) {
            crumbRow.addView(tv(t, 18f, if (last) Color.WHITE else grey).apply {
                maxLines = 1; setPadding(dp(4), dp(10), dp(4), dp(10)); setOnClickListener { go() }
            }, -2, -2)
        }

        icon(GlyphDrawable(GlyphDrawable.HOME), 44, 7) { home() }
        val c = cur
        if (list != null) {
            chev(); seg(listTitle, true) {}
        } else if (c != null) {
            chev()
            icon(GlyphDrawable(GlyphDrawable.DRIVE), 38, 5) { browse(root) }
            if (c == root) {
                seg("Ana dizin", true) {}
            } else {
                val under = c.path.startsWith(root.path + "/")
                val parts = if (under) c.path.substring(root.path.length + 1).split("/") else listOf(c.name)
                var acc = root
                parts.forEachIndexed { i, n ->
                    chev()
                    acc = if (under) File(acc, n) else c
                    val target = acc
                    seg(n, i == parts.size - 1) { browse(target) }
                }
            }
        }
        crumbScroll.post { crumbScroll.fullScroll(View.FOCUS_RIGHT) }
    }

    // ---------- Ana ekran ----------

    private fun home() {
        cur = null; list = null; sel.clear(); picking = false; body.removeAllViews()
        chrome()
        val g = GridLayout(this).apply { columnCount = 3; setPadding(dp(8), dp(24), dp(8), dp(24)) }
        fun tile(ic: String, name: String, sub: String, click: () -> Unit) {
            val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(4), dp(10), dp(4), dp(14)) }
            val i = tv(ic, 30f, Color.WHITE).apply {
                gravity = Gravity.CENTER
                background = GradientDrawable().apply { setColor(card); cornerRadius = dp(22).toFloat(); setStroke(dp(1), 0xFF3A3A3A.toInt()) }
            }
            t.addView(i, dp(72), dp(72))
            t.addView(tv(name, 16f, 0xFFEEEEEE.toInt()).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
            if (sub.isNotEmpty()) t.addView(tv(sub, 12f, 0xFF999999.toInt()).apply { gravity = Gravity.CENTER })
            t.setOnClickListener { click() }
            g.addView(t, GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply { width = 0 })
        }
        fun cat(k: String, ic: String, n: String) {
            val l = cats[k]; val sub = if (l == null) "" else "${sz(l.sumOf { it.length() })} (${l.size})"
            tile(ic, n, sub, { openList(n, l ?: emptyList()) })
        }
        val st = StatFs(root.path); val tot = st.totalBytes; val used = tot - st.availableBytes
        tile("💾", "Ana dizin", "${sz(used)} / ${sz(tot)}", { browse(root) })
        tile("📥", "İndirilenler", "", { browse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)) })
        cat("img", "🖼", "Görüntüler")
        cat("apk", "📱", "Uygulamalar")
        cat("new", "🕒", "Yeni Dosyalar")
        body.addView(ScrollView(this).apply { addView(g) })
    }

    // ---------- Gezinme ----------

    private fun browse(d: File) { cur = d; list = null; sel.clear(); refresh(true) }
    private fun openList(t: String, l: List<File>) { list = l; listTitle = t; sel.clear(); refresh(true) }

    private fun items(): List<File> {
        val base = list ?: (cur?.listFiles()?.toList() ?: emptyList())
        val l = if (picking) base.filter { it.isDirectory } else base
        return l.sortedByDescending { it.lastModified() }
    }

    private fun refresh(reset: Boolean = false) {
        shown = items()
        chrome()
        pickBtn.visibility = if (picking) View.VISIBLE else View.GONE
        if (listWrap.parent == null) { body.removeAllViews(); body.addView(listWrap) }
        fileAdapter.notifyDataSetChanged()
        if (reset) lv.setSelection(0)
    }

    private fun toggle(f: File) { if (!sel.remove(f)) sel.add(f); refresh() }

    private fun uri(f: File) = FileProvider.getUriForFile(this, "$packageName.fp", f)

    private fun open(f: File) {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"
        try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri(f), mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        catch (e: ActivityNotFoundException) { toast("Açacak uygulama yok") }
    }

    private fun share() {
        val us = ArrayList(sel.filter { it.isFile }.map { uri(it) })
        if (us.isEmpty()) return
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, us).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Paylaş"))
    }

    // ---------- Alt çubuk (4 klasör) ----------

    private fun barList(): MutableList<String> {
        val s = prefs.getString("bar", null)
        if (s != null) return s.split("\n").filter { it.isNotEmpty() }.toMutableList()
        // eski sürümdeki sabitlenmiş klasörleri taşı
        return (prefs.getStringSet("pins", emptySet()) ?: emptySet()).sorted().take(4).toMutableList()
    }

    private fun saveBar(l: List<String>) { prefs.edit().putString("bar", l.joinToString("\n")).apply() }

    private fun buildBar() {
        barV.removeAllViews()
        val b = barList()
        for (i in 0 until 4) {
            val slot = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(2), dp(4), dp(2), dp(4)) }
            val p = b.getOrNull(i)
            if (p != null) {
                val f = File(p)
                slot.addView(ImageView(this).apply { setImageDrawable(GlyphDrawable(GlyphDrawable.FOLDER)) }, dp(34), dp(30))
                slot.addView(tv(f.name, 11f, 0xFFDDDDDD.toInt()).apply {
                    maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER; setPadding(dp(2), dp(2), dp(2), 0)
                })
                slot.setOnClickListener { if (f.isDirectory) { picking = false; browse(f) } else toast("Klasör bulunamadı") }
                slot.setOnLongClickListener {
                    AlertDialog.Builder(this).setTitle(f.name).setMessage("Alt çubuktan kaldırılsın mı?")
                        .setPositiveButton("Kaldır") { _, _ -> val l = barList(); l.remove(p); saveBar(l); buildBar() }
                        .setNegativeButton("İptal", null).show()
                    true
                }
                slot.setOnDragListener { v, e -> slotDrag(v, e, f) }
            }
            barV.addView(slot, LinearLayout.LayoutParams(0, -1, 1f))
        }
        val plus = FrameLayout(this)
        plus.addView(ImageView(this).apply { setImageDrawable(GlyphDrawable(GlyphDrawable.PLUS, accent)) }, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER))
        plus.setOnClickListener { startPick() }
        barV.addView(plus, dp(56), -1)
    }

    private fun startPick() {
        if (barList().size >= 4) { toast("Çubuk dolu (4 klasör). Kaldırmak için bir klasöre uzun bas."); return }
        val c = cur; val l = list; val t = listTitle
        pickBack = {
            picking = false
            if (l != null) { cur = c; openList(t, l) } else if (c != null) browse(c) else home()
        }
        picking = true; browse(root)
    }

    private fun pin(f: File) {
        val b = barList()
        when {
            f.path in b -> toast("Bu klasör zaten çubukta")
            b.size >= 4 -> toast("Çubuk dolu (4 klasör)")
            else -> { b.add(f.path); saveBar(b); buildBar(); toast("Alt çubuğa eklendi") }
        }
    }

    // ---------- Sürükle-bırak ----------

    private fun startDrag(v: View) {
        val s = tv("${sel.size} öğe", 14f, Color.WHITE).apply {
            setPadding(dp(14), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply { setColor(accent); cornerRadius = dp(18).toFloat() }
            measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            layout(0, 0, measuredWidth, measuredHeight)
        }
        v.startDragAndDrop(ClipData.newPlainText("fm", "fm"), View.DragShadowBuilder(s), "fm", 0)
    }

    private fun slotDrag(v: View, e: DragEvent, dest: File): Boolean {
        if (e.localState != "fm") return false
        when (e.action) {
            DragEvent.ACTION_DRAG_ENTERED -> v.setBackgroundColor(0x55FF9100)
            DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> v.setBackgroundColor(Color.TRANSPARENT)
            DragEvent.ACTION_DROP -> { v.setBackgroundColor(Color.TRANSPARENT); moveSel(dest) }
        }
        return true
    }

    private fun moveSel(dest: File) {
        val its = sel.toList()
        if (its.isEmpty()) return
        sel.clear(); refresh()
        Thread {
            var n = 0
            its.forEach { f ->
                try {
                    if (f.parentFile == dest) return@forEach
                    if (f.isDirectory && (dest.path == f.path || dest.path.startsWith(f.path + "/"))) return@forEach
                    var t = File(dest, f.name); var k = 1
                    while (t.exists()) { t = File(dest, "${f.nameWithoutExtension}_kopya$k" + (if (f.extension.isEmpty()) "" else "." + f.extension)); k++ }
                    if (!f.renameTo(t)) { f.copyRecursively(t); f.deleteRecursively() }
                    n++
                } catch (e: Exception) {}
            }
            runOnUiThread {
                if (list != null) list = list!!.filter { it.exists() }
                toast("$n öğe ${dest.name} klasörüne taşındı")
                refresh()
            }
            scan()
        }.start()
    }

    // ---------- Diğer işlemler ----------

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
                try { if (mv) { if (!f.renameTo(t)) { f.copyRecursively(t); f.deleteRecursively() } } else f.copyRecursively(t) } catch (e: Exception) {}
            }
            runOnUiThread { refresh() }; scan()
        }.start()
    }

    private fun menu(v: View) {
        val p = PopupMenu(this, v); val its = ArrayList<Pair<String, () -> Unit>>()
        fun a(n: String, r: () -> Unit) { its.add(n to r) }
        if (sel.isNotEmpty()) {
            a("Kopyala") { clip = sel.toList(); cut = false; sel.clear(); refresh() }
            a("Taşı") { clip = sel.toList(); cut = true; sel.clear(); refresh() }
            a("Sil") {
                AlertDialog.Builder(this).setMessage("${sel.size} öğe silinsin mi?").setPositiveButton("Sil") { _, _ ->
                    val x = sel.toList(); sel.clear()
                    Thread { x.forEach { it.deleteRecursively() }; runOnUiThread { if (list != null) list = list!!.filter { it.exists() }; refresh() }; scan() }.start()
                }.setNegativeButton("İptal", null).show()
            }
            a("Paylaş") { share() }
            if (sel.size == 1) {
                val f = sel.first()
                a("Yeniden adlandır") { input("Yeni ad", f.name) { n -> if (n.isNotEmpty()) f.renameTo(File(f.parentFile, n)); sel.clear(); if (list != null) list = list!!.filter { it.exists() }; refresh() } }
                if (f.isDirectory) a("Alt çubuğa ekle") { pin(f); sel.clear(); refresh() }
            }
            a("Tümünü seç") { sel.addAll(shown); refresh() }
        } else if (cur != null && list == null) {
            if (clip.isNotEmpty()) a("Yapıştır (${clip.size})") { paste() }
            a("Yeni klasör") { input("Klasör adı", "") { n -> if (n.isNotEmpty()) File(cur, n).mkdirs(); refresh() } }
            a("Ara") { input("Ara", "") { q -> if (q.isNotEmpty()) Thread { val r = cur!!.walkTopDown().filter { it != cur && it.name.contains(q, true) }.take(500).toList(); runOnUiThread { openList("Ara: $q", r) } }.start() } }
            if (!picking) a("Alt çubuğa ekle") { pin(cur!!) }
        }
        if (its.isEmpty()) return
        its.forEachIndexed { i, x -> p.menu.add(0, i, i, x.first) }
        p.setOnMenuItemClickListener { its[it.itemId].second(); true }
        p.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            sel.isNotEmpty() -> { sel.clear(); refresh() }
            list != null -> { list = null; if (cur != null) refresh() else home() }
            picking && cur == root -> { val b = pickBack; if (b != null) b() else home() }
            cur != null -> { val c = cur!!; if (c == root || c.parentFile?.canRead() != true) home() else browse(c.parentFile!!) }
            else -> super.onBackPressed()
        }
    }
}
