package io.github.idrisokys.filemanager

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
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
    private val bg = 0xFF121212.toInt(); private val bar = 0xFF212121.toInt(); private val card = 0xFF1E1E1E.toInt()
    private lateinit var titleV: TextView; private lateinit var backB: TextView; private lateinit var body: FrameLayout
    private var cur: File? = null; private var list: List<File>? = null; private var listTitle = ""
    private var picking = false; private var slotPick = -1; private val sel = linkedSetOf<File>()
    private var clip = listOf<File>(); private var cut = false
    private var hidden = false; private var asked = false
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
        backB = tv("☰", 24f, Color.WHITE).apply { gravity = Gravity.CENTER; setOnClickListener { if (cur == null && list == null) menu(it) else onBackPressed() } }
        titleV = tv("", 20f, Color.WHITE).apply { typeface = Typeface.DEFAULT_BOLD; maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        val mb = tv("⋮", 26f, Color.WHITE).apply { gravity = Gravity.CENTER; setOnClickListener { menu(it) } }
        tb.addView(backB, dp(56), dp(56)); tb.addView(titleV, LinearLayout.LayoutParams(0, -2, 1f)); tb.addView(mb, dp(56), dp(56))
        body = FrameLayout(this)
        col.addView(tb, -1, dp(56)); col.addView(body, -1, -1)
        setContentView(col); home()
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
            m["vid"] = all.filter { it.extension.lowercase() in VID }
            m["doc"] = all.filter { it.extension.lowercase() in DOC }
            m["apk"] = all.filter { it.extension.lowercase() == "apk" }
            m["new"] = all.sortedByDescending { it.lastModified() }.take(200)
            cats = m
            runOnUiThread { if (cur == null && list == null && !picking) home() }
        }.start()
    }

    private fun chrome(t: String) {
        titleV.text = t
        backB.text = if (cur == null && list == null) "☰" else "←"
    }

    private fun home() {
        cur = null; list = null; sel.clear(); picking = false; body.removeAllViews()
        chrome("Dosya Yöneticisi")
        val g = GridLayout(this).apply { columnCount = 3; setPadding(dp(8), dp(24), dp(8), dp(24)) }
        fun tile(ic: String, name: String, sub: String, click: () -> Unit, long: (() -> Unit)? = null, res: Drawable? = null, dashed: Boolean = false) {
            val t = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(4), dp(10), dp(4), dp(14)) }
            val i: View = when {
                res != null -> ImageView(this).apply { setImageDrawable(res); setPadding(dp(2), dp(2), dp(2), dp(2)) }
                dashed -> ImageView(this).apply {
                    setImageDrawable(Icons.add()); setColorFilter(0xFF888888.toInt()); setPadding(dp(22), dp(22), dp(22), dp(22))
                    background = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(22).toFloat(); setStroke(dp(2), 0xFF555555.toInt(), dp(6).toFloat(), dp(4).toFloat()) }
                }
                else -> tv(ic, 30f, Color.WHITE).apply {
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply { setColor(card); cornerRadius = dp(22).toFloat(); setStroke(dp(1), 0xFF3A3A3A.toInt()) }
                }
            }
            t.addView(i, dp(72), dp(72))
            t.addView(tv(name, 16f, if (dashed) 0xFF888888.toInt() else 0xFFEEEEEE.toInt()).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
            if (sub.isNotEmpty()) t.addView(tv(sub, 12f, 0xFF999999.toInt()).apply { gravity = Gravity.CENTER })
            t.setOnClickListener { click() }
            if (long != null) t.setOnLongClickListener { long(); true }
            g.addView(t, GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply { width = 0 })
        }
        fun cat(k: String, ic: String, n: String) {
            val l = cats[k]; val sub = if (l == null) "" else "${sz(l.sumOf { it.length() })} (${l.size})"
            tile(ic, n, sub, { openList(n, l ?: emptyList()) })
        }
        val st = StatFs(root.path); val tot = st.totalBytes; val used = tot - st.availableBytes
        tile("💾", "Ana bellek", "${sz(used)} / ${sz(tot)}", { browse(root) })
        tile("📥", "İndirilenler", "", { browse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)) }, null, Icons.folder())
        tile("📊", "Bellek Analizi", "Kullanılan %${used * 100 / tot}", { analysis() })
        cat("img", "🖼", "Görüntüler"); cat("vid", "🎬", "Videolar"); cat("doc", "📄", "Belgeler")
        cat("apk", "🤖", "Uygulamalar"); cat("new", "🕒", "Yeni Dosyalar")
        migratePins()
        for (n in 0 until 4) {
            val p = prefs.getString("slot$n", null)
            if (p == null) tile("", "Ekle", "", { slotPick = n; picking = true; browse(root) }, null, null, true)
            else {
                val f = File(p)
                tile("", f.name, "", { if (f.exists()) browse(f) else Toast.makeText(this, "Klasör bulunamadı", Toast.LENGTH_SHORT).show() }, {
                    AlertDialog.Builder(this).setTitle(f.name).setItems(arrayOf("Başka klasör seç", "Kaldır")) { _, w ->
                        if (w == 0) { slotPick = n; picking = true; browse(root) }
                        else { prefs.edit().remove("slot$n").apply(); home() }
                    }.setNegativeButton("İptal", null).show()
                }, Icons.folder())
            }
        }
        body.addView(ScrollView(this).apply { addView(g) })
    }

    private fun migratePins() {
        if (prefs.getBoolean("mig", false)) return
        val old = (prefs.getStringSet("pins", emptySet()) ?: emptySet()).sorted().take(4)
        val e = prefs.edit(); old.forEachIndexed { i, p -> e.putString("slot$i", p) }; e.putBoolean("mig", true).apply()
    }

    private fun setSlot(n: Int, f: File) { prefs.edit().putString("slot$n", f.path).apply() }

    private fun analysis() {
        val st = StatFs(root.path); val tot = st.totalBytes; val free = st.availableBytes
        val names = mapOf("img" to "Görüntüler", "vid" to "Videolar", "doc" to "Belgeler", "apk" to "Uygulamalar")
        val m = "Toplam: ${sz(tot)}\nKullanılan: ${sz(tot - free)} (%${(tot - free) * 100 / tot})\nBoş: ${sz(free)}\n\n" +
            names.map { (k, n) -> "$n: ${sz(cats[k]?.sumOf { it.length() } ?: 0)}" }.joinToString("\n")
        AlertDialog.Builder(this).setTitle("Bellek Analizi").setMessage(m).setPositiveButton("Tamam", null).show()
    }

    private fun browse(d: File) { cur = d; list = null; sel.clear(); refresh() }
    private fun openList(t: String, l: List<File>) { list = l; listTitle = t; sel.clear(); refresh() }

    private fun items(): List<File> {
        val base = list ?: (cur?.listFiles()?.toList() ?: emptyList())
        var l = if (hidden) base else base.filter { !it.name.startsWith(".") }
        if (picking) l = l.filter { it.isDirectory }
        return l.sortedByDescending { it.lastModified() }
    }

    private fun icon(f: File): String {
        if (f.isDirectory) return "📁"
        val e = f.extension.lowercase()
        return when { e in IMG -> "🖼"; e in VID -> "🎬"; e in AUD -> "🎵"; e in DOC -> "📄"; e == "apk" -> "🤖"; e in setOf("zip", "rar", "7z", "tar", "gz") -> "🗜"; else -> "📃" }
    }

    private fun refresh() {
        val l = items()
        chrome(if (picking) "Klasör seç" else if (sel.isNotEmpty()) "${sel.size} seçili" else if (list != null) listTitle else if (cur == root) "Ana bellek" else cur?.name ?: "")
        val lv = ListView(this).apply { divider = null; setBackgroundColor(bg) }
        lv.adapter = object : BaseAdapter() {
            override fun getCount() = l.size
            override fun getItem(p: Int) = l[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, v: View?, parent: android.view.ViewGroup?): View {
                val f = l[p]
                val row = LinearLayout(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(10), dp(16), dp(10)); if (f in sel) setBackgroundColor(0xFF2C3E50.toInt()) }
                row.addView(if (f.isDirectory) ImageView(this@MainActivity).apply { setImageDrawable(Icons.folder()); setPadding(dp(2), dp(2), dp(2), dp(2)) } else tv(icon(f), 26f, Color.WHITE).apply { gravity = Gravity.CENTER }, dp(44), dp(44))
                val c = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
                c.addView(tv(f.name, 16f, Color.WHITE).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END })
                val sub = if (f.isDirectory) "${f.listFiles()?.size ?: 0} öğe" else sz(f.length())
                c.addView(tv("$sub • ${df.format(Date(f.lastModified()))}", 12f, 0xFF999999.toInt()))
                row.addView(c, LinearLayout.LayoutParams(0, -2, 1f))
                return row
            }
        }
        lv.setOnItemClickListener { _, _, p, _ -> val f = l[p]; if (sel.isNotEmpty()) toggle(f) else if (f.isDirectory) browse(f) else if (f.extension.lowercase() == "zip") zipDialog(f) else open(f) }
        lv.setOnItemLongClickListener { _, _, p, _ -> toggle(l[p]); true }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (sel.isNotEmpty()) wrap.addView(selectionBar(), -1, dp(54))
        wrap.addView(lv, LinearLayout.LayoutParams(-1, 0, 1f))
        if (picking) wrap.addView(Button(this).apply { text = "Bu klasörü seç"; setOnClickListener { if (slotPick in 0..3) setSlot(slotPick, cur!!); slotPick = -1; home() } }, -1, -2)
        else if (clip.isNotEmpty() && cur != null && list == null && sel.isEmpty()) wrap.addView(pasteBar(), -1, dp(52))
        body.removeAllViews(); body.addView(wrap)
    }

    private fun barBtn(ic: Drawable, label: String, enabled: Boolean, act: () -> Unit): View {
        val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; alpha = if (enabled) 1f else 0.35f }
        b.addView(ImageView(this).apply { setImageDrawable(ic); setColorFilter(Color.WHITE) }, dp(22), dp(22))
        b.addView(tv(label, 9f, 0xFFCCCCCC.toInt()).apply { gravity = Gravity.CENTER; maxLines = 2 })
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

    private fun pin(f: File) {
        val n = (0 until 4).firstOrNull { prefs.getString("slot$it", null) == null }
        if (n == null) { Toast.makeText(this, "4 klasör yeri dolu, ana ekrandan birini kaldırın", Toast.LENGTH_SHORT).show(); return }
        setSlot(n, f); Toast.makeText(this, "Ana ekrana eklendi", Toast.LENGTH_SHORT).show()
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

    private fun menu(v: View) {
        val p = PopupMenu(this, v); val its = ArrayList<Pair<String, () -> Unit>>()
        fun a(n: String, r: () -> Unit) { its.add(n to r) }
        val inBrowser = cur != null || list != null
        if (sel.isNotEmpty()) {
            if (sel.size == 1 && sel.first().isDirectory) a("Ana ekrana ekle") { pin(sel.first()); sel.clear(); refresh() }
            a("Tümünü seç") { sel.addAll(items()); refresh() }
        } else {
            if (cur != null && list == null) {
                if (clip.isNotEmpty()) a("Yapıştır (${clip.size})") { paste() }
                a("Yeni klasör") { input("Klasör adı", "") { n -> if (n.isNotEmpty()) File(cur, n).mkdirs(); refresh() } }
                a("Ara") { input("Ara", "") { q -> if (q.isNotEmpty()) Thread { val r = cur!!.walkTopDown().filter { it != cur && it.name.contains(q, true) }.take(500).toList(); runOnUiThread { openList("Ara: $q", r) } }.start() } }
                if (!picking) a("Ana ekrana ekle") { pin(cur!!) }
            }
            a(if (hidden) "Gizli dosyaları gizle" else "Gizli dosyaları göster") { hidden = !hidden; if (inBrowser) refresh() }
        }
        its.forEachIndexed { i, x -> p.menu.add(0, i, i, x.first) }
        p.setOnMenuItemClickListener { its[it.itemId].second(); true }
        p.show()
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
