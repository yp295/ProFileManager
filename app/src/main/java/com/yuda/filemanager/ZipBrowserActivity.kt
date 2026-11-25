package com.yuda.filemanager

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yuda.filemanager.databinding.ActivityZipBrowserBinding
import com.yuda.filemanager.databinding.ItemFileBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.util.ArrayDeque
import java.util.Locale

class ZipBrowserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityZipBrowserBinding
    private lateinit var zipFile: File
    private var zip: ZipFile? = null
    private var prefix = ""
    private val backStack = ArrayDeque<String>()

    private data class ZipItem(
        val name: String,
        val isDir: Boolean,
        val entry: ZipArchiveEntry?,
        val size: Long
    )

    private inner class Adapter : RecyclerView.Adapter<Adapter.VH>() {
        var items: List<ZipItem> = emptyList()

        inner class VH(val b: ItemFileBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.b.tvName.text = item.name
            holder.b.tvDetail.text = if (item.isDir) "Folder" else formatSize(item.size)
            if (item.isDir) {
                holder.b.ivIcon.setImageResource(R.drawable.ic_folder)
                holder.b.ivIcon.setColorFilter(Color.parseColor("#FFC107"))
            } else {
                holder.b.ivIcon.setImageResource(R.drawable.ic_file)
                holder.b.ivIcon.setColorFilter(Color.parseColor("#B0BEC5"))
            }
            holder.b.root.setOnClickListener { onItemClick(item) }
        }
    }

    private val adapter = Adapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityZipBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val path = intent.getStringExtra("path")
        if (path == null) {
            finish()
            return
        }
        zipFile = File(path)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = zipFile.name
        binding.toolbar.setNavigationOnClickListener { goBack() }
        binding.rvZip.layoutManager = LinearLayoutManager(this)
        binding.rvZip.adapter = adapter
        binding.btnExtractAll.setOnClickListener { extractAll() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goBack()
            }
        })
        loadEntries()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            zip?.close()
        } catch (e: Exception) {
        }
    }

    private fun goBack() {
        if (backStack.isEmpty()) {
            finish()
            return
        }
        prefix = backStack.pop()
        loadEntries()
    }

    private fun loadEntries() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (zip == null) zip = ZipFile(zipFile)
                val items = listChildren(prefix)
                withContext(Dispatchers.Main) {
                    adapter.items = items
                    adapter.notifyDataSetChanged()
                    binding.tvZipPath.text = if (prefix.isEmpty()) "/" else "/$prefix"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ZipBrowserActivity, "Tidak bisa membuka arsip", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun listChildren(prefix: String): List<ZipItem> {
        val z = zip ?: return emptyList()
        val dirs = mutableMapOf<String, ZipArchiveEntry?>()
        val files = mutableListOf<ZipItem>()
        val entries = z.entries
        while (entries.hasMoreElements()) {
            val e = entries.nextElement()
            val name = e.name
            if (!name.startsWith(prefix)) continue
            val rest = name.removePrefix(prefix)
            if (rest.isEmpty()) continue
            val slash = rest.indexOf('/')
            if (slash == -1) {
                if (!e.isDirectory) files.add(ZipItem(rest, false, e, e.size))
            } else if (slash == rest.length - 1) {
                dirs.getOrPut(rest.dropLast(1)) { e }
            } else {
                val top = rest.substring(0, slash)
                dirs.getOrPut(top) { null }
            }
        }
        val dirItems = dirs.map { (n, e) -> ZipItem(n, true, e, 0) }
            .sortedBy { it.name.lowercase(Locale.US) }
        val fileItems = files.sortedBy { it.name.lowercase(Locale.US) }
        return dirItems + fileItems
    }

    private fun onItemClick(item: ZipItem) {
        if (item.isDir) {
            backStack.push(prefix)
            prefix = prefix + item.name + "/"
            loadEntries()
            return
        }
        val entry = item.entry ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val outDir = File(cacheDir, "zip_extract")
                val out = File(outDir, item.name)
                out.parentFile?.mkdirs()
                zip?.getInputStream(entry)?.use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                withContext(Dispatchers.Main) { openFile(out) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ZipBrowserActivity, "Gagal mengekstrak file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun extractAll() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dest = File(zipFile.parentFile, zipFile.nameWithoutExtension)
                dest.mkdirs()
                val z = zip ?: return@launch
                val entries = z.entries
                while (entries.hasMoreElements()) {
                    val e = entries.nextElement()
                    val out = File(dest, e.name)
                    if (e.isDirectory) {
                        out.mkdirs()
                        continue
                    }
                    out.parentFile?.mkdirs()
                    z.getInputStream(e)?.use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@ZipBrowserActivity,
                        "Diekstrak ke ${dest.absolutePath}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ZipBrowserActivity, "Gagal mengekstrak", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
            Toast.makeText(this, "Tidak ada aplikasi untuk membuka file ini", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak bisa membuka file", Toast.LENGTH_SHORT).show()
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
}
