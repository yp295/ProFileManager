package com.yuda.filemanager

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yuda.filemanager.databinding.ActivityAppManagerBinding
import com.yuda.filemanager.databinding.ItemFileBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AppManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppManagerBinding

    data class AppEntry(
        val label: String,
        val pkg: String,
        val version: String,
        val sourceDir: String,
        val icon: android.graphics.drawable.Drawable?
    )

    inner class AppAdapter : RecyclerView.Adapter<AppAdapter.VH>() {
        var apps: List<AppEntry> = emptyList()
        inner class VH(val b: ItemFileBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val b = ItemFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(b)
        }

        override fun getItemCount(): Int = apps.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val app = apps[position]
            holder.b.tvName.text = app.label
            holder.b.tvDetail.text = "${app.pkg} • ${app.version}"
            if (app.icon != null) {
                holder.b.ivIcon.setImageDrawable(app.icon)
                holder.b.ivIcon.clearColorFilter()
            }
            holder.b.root.setOnClickListener { showAppMenu(app) }
        }
    }

    private val adapter = AppAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manajer Aplikasi"
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.rvApps.layoutManager = LinearLayoutManager(this)
        binding.rvApps.adapter = adapter
        loadApps()
    }

    @Suppress("DEPRECATION")
    private fun loadApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = packageManager
            val pkgs = try {
                pm.getInstalledPackages(0)
            } catch (e: Exception) {
                emptyList()
            }
            val list = pkgs.mapNotNull { pi ->
                val ai: ApplicationInfo = pi.applicationInfo ?: return@mapNotNull null
                val label = try { pm.getApplicationLabel(ai).toString() } catch (e: Exception) { pi.packageName }
                val icon = try { pm.getApplicationIcon(ai) } catch (e: Exception) { null }
                val version = try { pi.versionName ?: "-" } catch (e: Exception) { "-" }
                AppEntry(label, pi.packageName, version, ai.sourceDir, icon)
            }.sortedBy { it.label.lowercase(Locale.US) }
            withContext(Dispatchers.Main) {
                adapter.apps = list
                adapter.notifyDataSetChanged()
                supportActionBar?.subtitle = "${list.size} aplikasi"
            }
        }
    }

    private fun showAppMenu(app: AppEntry) {
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setMessage("${app.pkg}\nVersi: ${app.version}")
            .setPositiveButton("Buka") { _, _ ->
                val intent = packageManager.getLaunchIntentForPackage(app.pkg)
                if (intent != null) startActivity(intent)
                else Toast.makeText(this, "Aplikasi tidak bisa dibuka", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Info Aplikasi") { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:${app.pkg}")
                    }
                )
            }
            .setNegativeButton("Ekstrak APK") { _, _ -> extractApk(app) }
            .show()
    }

    private fun extractApk(app: AppEntry) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val src = File(app.sourceDir)
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                val out = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "${app.pkg}-${app.version}-$stamp.apk"
                )
                src.inputStream().use { input ->
                    out.outputStream().use { output -> input.copyTo(output, 65536) }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AppManagerActivity, "APK disimpan: ${out.name}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AppManagerActivity, "Ekstrak gagal", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
