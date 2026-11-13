package io.github.idrisokys.filemanager

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.view.DragEvent
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
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
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
    private var picking = false; private var slotPick = -1; private val sel = linkedSetOf<File>()
    private var clip = listOf<File>(); private var cut = false
    private var hidden = true; private var asked = false
    private val root: File = Environment.getExternalStorageDirectory()
    private val prefs by lazy { getSharedPreferences("p", 0) }
    private var cats = mapOf<String, List<File>>()
    private val IMG = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "svg")
    private val VID = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v")
    private val AUD = setOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "opus")
    private val DOC = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "odt", "rtf", "csv", "epub")
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
        titleV = tv("", 15f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        crumbL = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        crumbS = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; visibility = View.GONE; addView(crumbL, -2, -1) }
        cancelV = tv("İptal", 15f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD; maxLines = 1; gravity = Gravity.CENTER_VERTICAL; visibility = View.GONE; setPadding(dp(12), 0, 0, 0); setOnClickListener { sel.clear(); refresh() } }
        pinV = ImageView(this).apply {
            setImageDrawable(Icons.pin()); setColorFilter(Color.WHITE); setPadding(dp(7), dp(7), dp(7), dp(7)); visibility = View.GONE
            contentDescription = "Ana ekrana sabitle"; setOnClickListener { pinSelected() }
        }
        titleRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(titleV, LinearLayout.LayoutParams(-2, -2)); addView(pinV, LinearLayout.LayoutParams(dp(34), dp(34)))
        }
        val center = FrameLayout(this).apply {
            addView(titleRow, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL))
            addView(crumbS, FrameLayout.LayoutParams(-1, -1))
        }
        tb.setPadding(dp(12), 0, dp(12), 0); tb.addView(center, LinearLayout.LayoutParams(0, -1, 1f)); tb.addView(cancelV, LinearLayout.LayoutParams(-2, -1))
        body = FrameLayout(this)
        dock = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(dockBg) }
        col.addView(tb, -1, dp(40)); col.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        col.addView(View(this).apply { setBackgroundColor(0xFF333333.toInt()) }, -1, dp(1))
        col.addView(dock, -1, dp(58))
        setContentView(col); migratePins(); updateDock(); home()
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
            cats = m
            runOnUiThread { if (cur == null && list == null && !picking) home() }
        }.start()
    }

    private fun chrome(t: String, crumbs: Boolean = false) {
        titleV.text = t
        titleRow.visibility = if (crumbs) View.GONE else View.VISIBLE
        pinV.visibility = if (sel.isNotEmpty()) View.VISIBLE else View.GONE
        crumbS.visibility = if (crumbs) View.VISIBLE else View.GONE
        if (crumbs) buildCrumbs()
        cancelV.visibility = if (sel.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun buildCrumbs() {
        crumbL.removeAllViews()
        fun chev() = ImageView(this).apply { setImageDrawable(Icons.chevron()); setColorFilter(0xFF888888.toInt()) }
        val last = cur ?: return
        crumbL.addView(ImageView(this).apply { setImageDrawable(Icons.homeColor()); setOnClickListener { home() } }, dp(26), dp(26))
        crumbL.addView(chev(), dp(18), dp(18))
        crumbL.addView(ImageView(this).apply { setImageDrawable(Icons.folder()); setOnClickListener { browse(root) } }, dp(26), dp(26))
        val chain = ArrayList<File>(); var f: File? = last
        while (f != null && f.path != root.path) { chain.add(0, f); f = f.parentFile }
        chain.forEach { d ->
            crumbL.addView(chev(), dp(18), dp(18))
            crumbL.addView(tv(d.name, 14f, if (d == last) Color.WHITE else 0xFFBBBBBB.toInt()).apply {
                maxLines = 1; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), 0, dp(6), 0); setOnClickListener { if (d != last) browse(d) }
            }, LinearLayout.LayoutParams(-2, -1))
        }
        crumbS.post { crumbS.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun updateDock() {
        dock.removeAllViews()
        for (n in 0 until 5) {
            val p = prefs.getString("slot$n", null)
            val f = if (p == null) null else File(p)
            val v = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
            if (f == null) {
                v.addView(ImageView(this).apply {
                    setImageDrawable(Icons.add()); setColorFilter(0xFF888888.toInt()); setPadding(dp(5), dp(5), dp(5), dp(5))
                    background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(8).toFloat(); setStroke(dp(1), 0xFF555555.toInt(), dp(4).toFloat(), dp(3).toFloat()) }
                }, dp(30), dp(30))
                v.addView(tv("Ekle", 10f, 0xFF888888.toInt()).apply { gravity = Gravity.CENTER })
                v.setOnClickListener { slotPick = n; picking = true; browse(root) }
            } else {
                v.addView(ImageView(this).apply { setImageDrawable(Icons.folder()) }, dp(30), dp(30))
                v.addView(tv(f.name, 10f, 0xFFCCCCCC.toInt()).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER; setPadding(dp(2), 0, dp(2), 0) })
                v.setOnClickListener { if (f.isDirectory) { picking = false; list = null; browse(f) } else Toast.makeText(this, "Klasör bulunamadı", Toast.LENGTH_SHORT).show() }
                v.setOnLongClickListener { slotMenu(n); true }
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
    private fun slotsChanged() { updateDock(); if (cur == null && list == null && !picking) home() }

    private fun home() {
        cur = null; list = null; sel.clear(); picking = false; body.removeAllViews()
        chrome("Dosya Yöneticisi")
        val g = GridLayout(this).apply { columnCount = 4; setPadding(dp(4), dp(12), dp(4), dp(12)) }
        fun tile(ic: String, name: String, sub: String, click: () -> Unit, long: (() -> Unit)? = null, res: Drawable? = null, dashed: Boolean = false) {
            val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(2), dp(6), dp(2), dp(8)) }
            val i: View = when {
                res != null -> ImageView(this).apply { setImageDrawable(res); setPadding(dp(2), dp(2), dp(2), dp(2)) }
                dashed -> ImageView(this).apply {
                    setImageDrawable(Icons.add()); setColorFilter(0xFF888888.toInt()); setPadding(dp(22), dp(22), dp(22), dp(22))
                    background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(22).toFloat(); setStroke(dp(2), 0xFF555555.toInt(), dp(6).toFloat(), dp(4).toFloat()) }
                }
                else -> tv(ic, 22f, Color.WHITE).apply {
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply { setColor(card); cornerRadius = dp(14).toFloat(); setStroke(dp(1), 0xFF3A3A3A.toInt()) }
                }
            }
            t.addView(i, dp(52), dp(52))
            t.addView(tv(name, 12f, if (dashed) 0xFF888888.toInt() else 0xFFEEEEEE.toInt()).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
            if (sub.isNotEmpty()) t.addView(tv(sub, 10f, 0xFF999999.toInt()).apply { gravity = Gravity.CENTER })
            t.setOnClickListener { click() }
            if (long != null) t.setOnLongClickListener { long(); true }
            g.addView(t, GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply { width = 0 })
        }
        fun cat(k: String, ic: String, n: String, res: Drawable? = null) {
            val l = cats[k]; val sub = if (l == null) "" else "${sz(l.sumOf { it.length() })} (${l.size})"
            tile(ic, n, sub, { openList(n, l ?: emptyList()) }, null, res)
        }
        val st = StatFs(root.path); val tot = st.totalBytes; val used = tot - st.availableBytes
        tile("", "Ana bellek", "${sz(used)} / ${sz(tot)}", { browse(root) }, null, Icons.homeColor())
        tile("📥", "İndirilenler", "", { browse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)) }, null, Icons.folder())
        cat("img", "🖼", "Görüntüler")
        cat("apk", "🤖", "Uygulamalar", Icons.androidApp())
        pinList().filter { it.exists() }.forEach { f ->
            tile("", f.name, "", {
                if (f.isDirectory) browse(f) else if (f.extension.lowercase() == "zip") zipDialog(f) else open(f)
            }, {
                savePins(pinList().filter { it.path != f.path })
                Toast.makeText(this, "Sabitleme kaldırıldı: ${f.name}", Toast.LENGTH_SHORT).show(); home()
            }, if (f.isDirectory) Icons.folder() else Icons.file())
        }
        body.addView(ScrollView(this).apply { addView(g) })
    }

    private fun pinList(): List<File> = (prefs.getString("hpins", "") ?: "").split("\n").filter { it.isNotEmpty() }.map { File(it) }
    private fun savePins(l: List<File>) { prefs.edit().putString("hpins", l.joinToString("\n") { it.path }).apply() }
    private fun pinSelected() {
        if (sel.isEmpty()) return
        val l = pinList().toMutableList(); var n = 0
        sel.forEach { f -> if (l.none { it.path == f.path }) { l.add(f); n++ } }
        savePins(l); sel.clear()
        Toast.makeText(this, if (n > 0) "$n öğe ana ekrana sabitlendi" else "Zaten sabitlenmiş", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun migratePins() {
        if (prefs.getBoolean("mig", false)) return
        val old = (prefs.getStringSet("pins", emptySet()) ?: emptySet()).sorted().take(5)
        val e = prefs.edit(); old.forEachIndexed { i, p -> e.putString("slot$i", p) }; e.putBoolean("mig", true).apply()
    }

    private fun setSlot(n: Int, f: File) { prefs.edit().putString("slot$n", f.path).apply(); slotsChanged() }

    private fun browse(d: File) { cur = d; list = null; sel.clear(); refresh() }
    private fun openList(t: String, l: List<File>) { list = l; listTitle = t; sel.clear(); refresh() }

    private fun items(): List<File> {
        val base = list ?: (cur?.listFiles()?.toList() ?: emptyList())
        var l = if (hidden) base else base.filter { !it.name.startsWith(".") }
        if (picking) l = l.filter { it.isDirectory }
        return l.sortedByDescending { it.lastModified() }
    }

    private fun iconView(f: File): View =
        ImageView(this).apply { setImageDrawable(if (f.isDirectory) Icons.folder() else Icons.file()); setPadding(dp(1), dp(1), dp(1), dp(1)) }

    private fun refresh() {
        val l = items()
        chrome(if (sel.isNotEmpty()) "${sel.size} seçili" else if (list != null) listTitle else if (cur == root) "Ana bellek" else cur?.name ?: "", cur != null && list == null && sel.isEmpty())
        val lv = ListView(this).apply { divider = ColorDrawable(0xFF2E2E2E.toInt()); dividerHeight = dp(1); setBackgroundColor(bg) }
        lv.adapter = object : BaseAdapter() {
            override fun getCount() = l.size
            override fun getItem(p: Int) = l[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, v: View?, parent: android.view.ViewGroup?): View {
                val f = l[p]
                val row = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(5), dp(12), dp(5)); if (f in sel) setBackgroundColor(0xFF2C3E50.toInt()) }
                row.addView(iconView(f), dp(40), dp(40))
                val c = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
                c.addView(tv(f.name, 16f, Color.WHITE).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                val sub = if (f.isDirectory) "${f.listFiles()?.size ?: 0} öğe" else sz(f.length())
                c.addView(tv("$sub • ${df.format(Date(f.lastModified()))}", 12f, 0xFF999999.toInt()))
                row.addView(c, LinearLayout.LayoutParams(0, -2, 1f))
                return row
            }
        }
        lv.setOnItemClickListener { _, _, p, _ -> val f = l[p]; if (sel.isNotEmpty()) toggle(f) else if (f.isDirectory) browse(f) else if (f.extension.lowercase() == "zip") zipDialog(f) else open(f) }
        lv.setOnItemLongClickListener { _, v, p, _ ->
            val f = l[p]; val was = f in sel
            if (!was) sel.add(f)
            val payload = sel.joinToString("\n") { it.absolutePath }
            try { v.startDragAndDrop(ClipData.newPlainText("dosya", payload), View.DragShadowBuilder(v), null, 0) } catch (e: Exception) {}
            if (!was) refresh()
            true
        }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (sel.isNotEmpty()) wrap.addView(selectionBar(), -1, dp(46))
        wrap.addView(lv, LinearLayout.LayoutParams(-1, 0, 1f))
        if (picking) wrap.addView(Button(this).apply { text = "Bu klasörü seç"; setOnClickListener { if (slotPick in 0..4) setSlot(slotPick, cur!!); slotPick = -1; home() } }, -1, -2)
        else if (clip.isNotEmpty() && cur != null && list == null && sel.isEmpty()) wrap.addView(pasteBar(), -1, dp(52))
        body.removeAllViews(); body.addView(wrap)
    }

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
        add(Icons.move(), "Taşı") { clip = sel.toList(); cut = true; sel.clear(); refresh(); Toast.makeText(this, "Hedef klasöre gidip Yapıştır'a basın", Toast.LENGTH_SHORT).show() }
        add(Icons.copy(), "Kopyala") { clip = sel.toList(); cut = false; sel.clear(); refresh(); Toast.makeText(this, "Hedef klasöre gidip Yapıştır'a basın", Toast.LENGTH_SHORT).show() }
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
        b.addView(ImageView(this).apply { setImageDrawable(Icons.close()); setColorFilter(0xFFBBBBBB.toInt()); setPadding(dp(10), dp(10), dp(10), dp(10)); setOnClickListener { clip = listOf(); refresh() } }, dp(44), dp(44))
        return b
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

    private fun toggle(f: File) { if (!sel.remove(f)) sel.add(f); refresh() }

    private fun uri(f: File) = KFileProvider.uriFor(this, f)

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

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            sel.isNotEmpty() -> { sel.clear(); refresh() }
            list != null -> { list = null; if (cur != null) refresh() else home() }
            cur != null -> { val c = cur!!; if (c == root || c.parentFile?.canRead() != true) home() else browse(c.parentFile!!) }
            else -> super.onBackPressed()
        }
    }
}
