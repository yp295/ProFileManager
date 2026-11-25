package com.yuda.filemanager

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.yuda.filemanager.databinding.ActivityMainBinding
import com.yuda.filemanager.databinding.ItemFileBinding
import com.yuda.filemanager.databinding.LayoutFilePaneBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var activePaneIndex = 0
    private val panes = mutableListOf<PaneState>()

    private val storageRoot: File get() = Environment.getExternalStorageDirectory()
    private val dateFmt = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())

    enum class SortMode { NAME, SIZE, DATE }

    inner class PaneState(val paneBinding: LayoutFilePaneBinding, val index: Int) {
        var dir: File = storageRoot
        val backStack = ArrayDeque<File>()
        val forwardStack = ArrayDeque<File>()
        var sortMode: SortMode = SortMode.NAME
        var sortAsc: Boolean = true
        var isGrid: Boolean = false
        val selected = mutableSetOf<File>()
        lateinit var adapter: FileAdapter
    }

    inner class FileAdapter(private val paneIndex: Int) :
        RecyclerView.Adapter<FileAdapter.VH>() {

        var files: List<File> = emptyList()
        var isGrid: Boolean = false

        inner class VH(val b: ItemFileBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun getItemCount(): Int = files.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val file = files[position]
            val pane = panes[paneIndex]
            val isSelected = pane.selected.contains(file)
            holder.b.tvName.text = file.name
            holder.b.tvDetail.text = detailText(file)
            val (iconRes, tint) = iconFor(file)
            holder.b.ivIcon.setImageResource(iconRes)
            holder.b.ivIcon.setColorFilter(tint)
            holder.b.cbSelect.isChecked = isSelected
            holder.b.cbSelect.visibility =
                if (pane.selected.isNotEmpty() && !isGrid) View.VISIBLE else View.GONE
            holder.b.root.setBackgroundColor(if (isSelected) 0x332196F3 else Color.TRANSPARENT)
            if (isGrid) {
                holder.b.root.orientation = LinearLayout.VERTICAL
                holder.b.tvDetail.visibility = View.GONE
                holder.b.tvName.gravity = Gravity.CENTER
                val s = dp(48)
                holder.b.ivIcon.layoutParams =
                    LinearLayout.LayoutParams(s, s).apply { gravity = Gravity.CENTER }
                holder.b.tvName.setPadding(0, dp(4), 0, 0)
            } else {
                holder.b.root.orientation = LinearLayout.HORIZONTAL
                holder.b.tvDetail.visibility = View.VISIBLE
                holder.b.tvName.gravity = Gravity.START
                val s = dp(36)
                holder.b.ivIcon.layoutParams = LinearLayout.LayoutParams(s, s)
                holder.b.tvName.setPadding(0, 0, 0, 0)
            }
            holder.b.root.setOnClickListener { onFileClick(paneIndex, file) }
            holder.b.root.setOnLongClickListener {
                onFileLongClick(paneIndex, file)
                true
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.drawer_open, R.string.drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.btnGrantAccess.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }

        setupTabsAndOrientation()
        panes.add(PaneState(binding.paneLeft, 0))
        panes.add(PaneState(binding.paneRight, 1))
        panes[1].dir = File(storageRoot, "Download")
        setupPane(panes[0])
        setupPane(panes[1])
        setupBottomActions()
        setupDrawer()
        highlightActivePane()
        refreshPermissionState()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    binding.drawerLayout.isDrawerOpen(GravityCompat.START) ->
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    panes.any { it.selected.isNotEmpty() } -> {
                        panes.forEach { it.selected.clear(); it.adapter.notifyDataSetChanged() }
                        updateStatus()
                    }
                    panes[activePaneIndex].backStack.isNotEmpty() -> goBack(panes[activePaneIndex])
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationMode(newConfig.orientation)
    }

    private fun refreshPermissionState() {
        val needGrant =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()
        binding.permissionBanner.visibility = if (needGrant) View.VISIBLE else View.GONE
        if (!needGrant) reloadPanes()
    }

    private fun setupTabsAndOrientation() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Panel 1"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Panel 2"))
        applyOrientationMode(resources.configuration.orientation)

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                activePaneIndex = tab.position
                if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    binding.paneLeft.root.visibility =
                        if (activePaneIndex == 0) View.VISIBLE else View.GONE
                    binding.paneRight.root.visibility =
                        if (activePaneIndex == 1) View.VISIBLE else View.GONE
                }
                highlightActivePane()
                updateStatus()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun applyOrientationMode(orientation: Int) {
        val leftParams = binding.paneLeft.root.layoutParams as LinearLayout.LayoutParams
        val rightParams = binding.paneRight.root.layoutParams as LinearLayout.LayoutParams
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.panesContainer.orientation = LinearLayout.HORIZONTAL
            leftParams.width = 0
            leftParams.height = LinearLayout.LayoutParams.MATCH_PARENT
            leftParams.weight = 1f
            rightParams.width = 0
            rightParams.height = LinearLayout.LayoutParams.MATCH_PARENT
            rightParams.weight = 1f
            binding.paneLeft.root.visibility = View.VISIBLE
            binding.paneRight.root.visibility = View.VISIBLE
        } else {
            binding.panesContainer.orientation = LinearLayout.VERTICAL
            leftParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            leftParams.height = 0
            leftParams.weight = 1f
            rightParams.width = LinearLayout.LayoutParams.MATCH_PARENT
            rightParams.height = 0
            rightParams.weight = 1f
            binding.paneLeft.root.visibility =
                if (activePaneIndex == 0) View.VISIBLE else View.GONE
            binding.paneRight.root.visibility =
                if (activePaneIndex == 1) View.VISIBLE else View.GONE
        }
        binding.paneLeft.root.layoutParams = leftParams
        binding.paneRight.root.layoutParams = rightParams
    }

    private fun setupPane(pane: PaneState) {
        val b = pane.paneBinding
        pane.adapter = FileAdapter(pane.index)
        b.rvFiles.layoutManager = LinearLayoutManager(this)
        b.rvFiles.adapter = pane.adapter
        b.root.setOnClickListener {
            activePaneIndex = pane.index
            highlightActivePane()
            updateStatus()
        }
        b.btnBack.setOnClickListener { goBack(pane) }
        b.btnForward.setOnClickListener { goForward(pane) }
        b.btnSort.setOnClickListener { showSortMenu(pane, b.btnSort) }
        b.btnToggleView.setOnClickListener {
            pane.isGrid = !pane.isGrid
            pane.adapter.isGrid = pane.isGrid
            b.rvFiles.layoutManager =
                if (pane.isGrid) GridLayoutManager(this, 3) else LinearLayoutManager(this)
            pane.adapter.notifyDataSetChanged()
        }
        loadDir(pane)
    }

    private fun setupDrawer() {
        binding.navView.setNavigationItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.nav_storage -> storageRoot
                R.id.nav_download -> File(storageRoot, "Download")
                R.id.nav_dcim -> File(storageRoot, "DCIM")
                R.id.nav_pictures -> File(storageRoot, "Pictures")
                R.id.nav_documents -> File(storageRoot, "Documents")
                R.id.nav_music -> File(storageRoot, "Music")
                R.id.nav_movies -> File(storageRoot, "Movies")
                else -> null
            }
            target?.let { navigateTo(panes[activePaneIndex], it) }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun setupBottomActions() {
        binding.btnCopy.setOnClickListener { copyOrMove(move = false) }
        binding.btnMove.setOnClickListener { copyOrMove(move = true) }
        binding.btnDelete.setOnClickListener { deleteSelected() }
        binding.btnRename.setOnClickListener { renameSelected() }
        binding.btnNew.setOnClickListener { newFolderDialog() }
        binding.btnSelect.setOnClickListener { selectAllToggle() }
        binding.btnMore.setOnClickListener { showMoreMenu() }
    }

    private fun loadDir(pane: PaneState) {
        val raw = try {
            pane.dir.listFiles()?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
        pane.adapter.files = raw.sortedWith(fileComparator(pane))
        pane.adapter.notifyDataSetChanged()
        buildBreadcrumb(pane)
        updateStatus()
    }

    private fun reloadPanes() {
        panes.forEach { loadDir(it) }
    }

    private fun fileComparator(pane: PaneState): Comparator<File> {
        val base: Comparator<File> = when (pane.sortMode) {
            SortMode.NAME -> compareBy { it.name.lowercase(Locale.US) }
            SortMode.SIZE -> compareBy { it.length() }
            SortMode.DATE -> compareBy { it.lastModified() }
        }
        val dirFirst = compareByDescending<File> { it.isDirectory }
        return dirFirst.then(if (pane.sortAsc) base else base.reversed())
    }

    private fun navigateTo(pane: PaneState, dir: File, pushHistory: Boolean = true) {
        if (!dir.exists()) {
            toast("Folder tidak ada")
            return
        }
        if (pushHistory && pane.dir != dir) {
            pane.backStack.push(pane.dir)
            pane.forwardStack.clear()
        }
        pane.dir = dir
        pane.selected.clear()
        loadDir(pane)
    }

    private fun goBack(pane: PaneState) {
        if (pane.backStack.isEmpty()) return
        pane.forwardStack.push(pane.dir)
        pane.dir = pane.backStack.pop()
        pane.selected.clear()
        loadDir(pane)
    }

    private fun goForward(pane: PaneState) {
        if (pane.forwardStack.isEmpty()) return
        pane.backStack.push(pane.dir)
        pane.dir = pane.forwardStack.pop()
        pane.selected.clear()
        loadDir(pane)
    }

    private fun buildBreadcrumb(pane: PaneState) {
        val tv = pane.paneBinding.tvBreadcrumb
        val sb = SpannableStringBuilder()
        val rootPath = storageRoot.absolutePath
        val dirPath = pane.dir.absolutePath
        fun addSegment(label: String, target: File) {
            val start = sb.length
            sb.append(label)
            sb.setSpan(object : ClickableSpan() {
                override fun onClick(widget: View) {
                    navigateTo(pane, target)
                }
            }, start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        addSegment("Internal", storageRoot)
        if (dirPath.startsWith(rootPath)) {
            val rel = dirPath.removePrefix(rootPath).trim('/')
            if (rel.isNotEmpty()) {
                var acc = storageRoot
                for (part in rel.split("/")) {
                    acc = File(acc, part)
                    val target = acc
                    sb.append(" / ")
                    addSegment(part, target)
                }
            }
        } else {
            sb.append("  ")
            addSegment(dirPath, pane.dir)
        }
        tv.text = sb
        tv.movementMethod = LinkMovementMethod.getInstance()
        tv.highlightColor = Color.TRANSPARENT
    }

    private fun onFileClick(paneIndex: Int, file: File) {
        val pane = panes[paneIndex]
        activePaneIndex = paneIndex
        highlightActivePane()
        if (pane.selected.isNotEmpty()) {
            toggleSelect(pane, file)
            return
        }
        if (file.isDirectory) navigateTo(pane, file) else openFile(file)
    }

    private fun onFileLongClick(paneIndex: Int, file: File) {
        val pane = panes[paneIndex]
        activePaneIndex = paneIndex
        highlightActivePane()
        toggleSelect(pane, file)
    }

    private fun toggleSelect(pane: PaneState, file: File) {
        if (pane.selected.contains(file)) pane.selected.remove(file) else pane.selected.add(file)
        pane.adapter.notifyDataSetChanged()
        updateStatus()
    }

    private fun selectAllToggle() {
        val pane = panes[activePaneIndex]
        val all = pane.adapter.files
        if (all.isEmpty()) return
        if (pane.selected.size == all.size) pane.selected.clear() else pane.selected.addAll(all)
        pane.adapter.notifyDataSetChanged()
        updateStatus()
    }

    private fun copyOrMove(move: Boolean) {
        val srcPane = panes[activePaneIndex]
        val dstDir = panes[1 - activePaneIndex].dir
        val items = srcPane.selected.toList()
        if (items.isEmpty()) {
            toast("Pilih file dulu (tahan file untuk memilih)")
            return
        }
        if (!dstDir.exists()) dstDir.mkdirs()
        binding.progressTask.visibility = View.VISIBLE
        binding.progressTask.progress = 0
        lifecycleScope.launch(Dispatchers.IO) {
            items.forEachIndexed { idx, src ->
                try {
                    val dst = File(dstDir, src.name)
                    if (move) moveRecursively(src, dst) else copyRecursively(src, dst)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                val pct = ((idx + 1) * 100 / items.size)
                withContext(Dispatchers.Main) { binding.progressTask.progress = pct }
            }
            withContext(Dispatchers.Main) {
                binding.progressTask.visibility = View.GONE
                srcPane.selected.clear()
                reloadPanes()
                toast(if (move) "Pindah selesai" else "Copy selesai")
            }
        }
    }

    private fun copyRecursively(src: File, dst: File) {
        if (src.isDirectory) {
            dst.mkdirs()
            src.listFiles()?.forEach { copyRecursively(it, File(dst, it.name)) }
        } else {
            src.inputStream().use { input ->
                dst.outputStream().use { output -> input.copyTo(output, 65536) }
            }
        }
    }

    private fun moveRecursively(src: File, dst: File) {
        if (!src.renameTo(dst)) {
            copyRecursively(src, dst)
            src.deleteRecursively()
        }
    }

    private fun deleteSelected() {
        val pane = panes[activePaneIndex]
        val items = pane.selected.toList()
        if (items.isEmpty()) {
            toast("Pilih file dulu (tahan file untuk memilih)")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Hapus")
            .setMessage("Hapus ${items.size} item?")
            .setPositiveButton("Hapus") { _, _ ->
                binding.progressTask.visibility = View.VISIBLE
                lifecycleScope.launch(Dispatchers.IO) {
                    items.forEach { it.deleteRecursively() }
                    withContext(Dispatchers.Main) {
                        binding.progressTask.visibility = View.GONE
                        pane.selected.clear()
                        reloadPanes()
                        toast("Dihapus")
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun renameSelected() {
        val pane = panes[activePaneIndex]
        if (pane.selected.size != 1) {
            toast("Pilih 1 file untuk rename")
            return
        }
        val file = pane.selected.first()
        val input = EditText(this).apply {
            setText(file.name)
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle("Rename")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != file.name) {
                    val ok = file.renameTo(File(file.parentFile, newName))
                    if (ok) {
                        pane.selected.clear()
                        reloadPanes()
                    } else toast("Rename gagal")
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun newFolderDialog() {
        val pane = panes[activePaneIndex]
        val input = EditText(this).apply { hint = "Nama folder" }
        AlertDialog.Builder(this)
            .setTitle("Folder Baru")
            .setView(input)
            .setPositiveButton("Buat") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (File(pane.dir, name).mkdirs()) reloadPanes()
                    else toast("Gagal membuat folder")
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun newFileDialog() {
        val pane = panes[activePaneIndex]
        val input = EditText(this).apply { hint = "Nama file" }
        AlertDialog.Builder(this)
            .setTitle("File Baru")
            .setView(input)
            .setPositiveButton("Buat") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    try {
                        if (File(pane.dir, name).createNewFile()) reloadPanes()
                        else toast("Gagal membuat file")
                    } catch (e: Exception) {
                        toast("Gagal membuat file")
                    }
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun showMoreMenu() {
        val popup = PopupMenu(this, binding.btnMore)
        popup.menu.add(0, 1, 0, "File Baru")
        popup.menu.add(0, 2, 0, "Properti")
        popup.menu.add(0, 3, 0, "Refresh")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> newFileDialog()
                2 -> showProperties()
                3 -> reloadPanes()
            }
            true
        }
        popup.show()
    }

    private fun showProperties() {
        val pane = panes[activePaneIndex]
        if (pane.selected.size != 1) {
            toast("Pilih 1 file untuk lihat properti")
            return
        }
        val file = pane.selected.first()
        val info = if (file.isDirectory) {
            val n = try { file.listFiles()?.size ?: 0 } catch (e: Exception) { 0 }
            "Folder • $n item"
        } else {
            formatSize(file.length())
        }
        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setMessage("Lokasi: ${file.absolutePath}\n$info\nDiubah: ${dateFmt.format(Date(file.lastModified()))}")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showSortMenu(pane: PaneState, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 11, 0, "Nama (A-Z)")
        popup.menu.add(0, 12, 0, "Nama (Z-A)")
        popup.menu.add(0, 13, 0, "Ukuran (kecil-besar)")
        popup.menu.add(0, 14, 0, "Ukuran (besar-kecil)")
        popup.menu.add(0, 15, 0, "Tanggal (terbaru)")
        popup.menu.add(0, 16, 0, "Tanggal (terlama)")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                11 -> { pane.sortMode = SortMode.NAME; pane.sortAsc = true }
                12 -> { pane.sortMode = SortMode.NAME; pane.sortAsc = false }
                13 -> { pane.sortMode = SortMode.SIZE; pane.sortAsc = true }
                14 -> { pane.sortMode = SortMode.SIZE; pane.sortAsc = false }
                15 -> { pane.sortMode = SortMode.DATE; pane.sortAsc = false }
                16 -> { pane.sortMode = SortMode.DATE; pane.sortAsc = true }
            }
            loadDir(pane)
            true
        }
        popup.show()
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeFor(file))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("Tidak ada aplikasi untuk membuka file ini")
        } catch (e: Exception) {
            toast("Tidak bisa membuka file")
        }
    }

    private fun mimeFor(file: File): String {
        return when (file.extension.lowercase(Locale.US)) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> "image/*"
            "mp4", "mkv", "webm", "3gp", "avi", "mov" -> "video/*"
            "mp3", "wav", "ogg", "flac", "m4a", "aac" -> "audio/*"
            "txt", "log", "md", "json", "xml", "ini", "kt", "java" -> "text/plain"
            "pdf" -> "application/pdf"
            "apk" -> "application/vnd.android.package-archive"
            else -> "*/*"
        }
    }

    private fun highlightActivePane() {
        binding.paneLeft.root.setBackgroundColor(if (activePaneIndex == 0) 0x222196F3 else 0x00000000)
        binding.paneRight.root.setBackgroundColor(if (activePaneIndex == 1) 0x224CAF50 else 0x00000000)
    }

    private fun updateStatus() {
        val pane = panes[activePaneIndex]
        val sel = pane.selected.size
        binding.tvStatus.text = if (sel > 0) {
            "${pane.dir.absolutePath} • $sel dipilih"
        } else {
            pane.dir.absolutePath
        }
    }

    private fun detailText(file: File): String {
        val date = dateFmt.format(Date(file.lastModified()))
        return if (file.isDirectory) {
            val n = try { file.listFiles()?.size ?: 0 } catch (e: Exception) { 0 }
            "Folder • $n item • $date"
        } else {
            "${formatSize(file.length())} • $date"
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = -1
        while (v >= 1024 && i < units.size - 1) {
            v /= 1024
            i++
        }
        return String.format(Locale.US, "%.1f %s", v, units[i])
    }

    private fun iconFor(file: File): Pair<Int, Int> {
        if (file.isDirectory) return R.drawable.ic_folder to Color.parseColor("#FFC107")
        return when (file.extension.lowercase(Locale.US)) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" ->
                R.drawable.ic_file to Color.parseColor("#4CAF50")
            "mp4", "mkv", "webm", "3gp", "avi", "mov" ->
                R.drawable.ic_file to Color.parseColor("#F44336")
            "mp3", "wav", "ogg", "flac", "m4a", "aac" ->
                R.drawable.ic_file to Color.parseColor("#FF9800")
            "zip", "rar", "7z", "tar", "gz" ->
                R.drawable.ic_file to Color.parseColor("#9C27B0")
            "apk" -> R.drawable.ic_file to Color.parseColor("#2196F3")
            "pdf" -> R.drawable.ic_file to Color.parseColor("#E53935")
            "txt", "log", "md", "json", "xml", "ini", "kt", "java" ->
                R.drawable.ic_file to Color.parseColor("#9E9E9E")
            else -> R.drawable.ic_file to Color.parseColor("#B0BEC5")
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
