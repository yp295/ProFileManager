package com.yuda.filemanager

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayout
import com.yuda.filemanager.databinding.ActivityMainBinding
import com.yuda.filemanager.databinding.LayoutFilePaneBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var activePaneIndex = 0

    private val panePaths = mutableListOf(
        Environment.getExternalStorageDirectory(),
        File(Environment.getExternalStorageDirectory(), "Download")
    )
    private val selectedFiles = mutableSetOf<File>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ensureAllFilesAccess()
        setupTabsAndOrientation()
        setupPane(binding.paneLeft, 0)
        setupPane(binding.paneRight, 1)
        setupBottomActions()
        highlightActivePane()
    }

    private fun ensureAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        }
    }

    private fun setupTabsAndOrientation() {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Panel 1"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Panel 2"))
        applyOrientationMode(resources.configuration.orientation)

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                activePaneIndex = tab.position
                if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                    binding.paneLeft.root.visibility = if (activePaneIndex == 0) View.VISIBLE else View.GONE
                    binding.paneRight.root.visibility = if (activePaneIndex == 1) View.VISIBLE else View.GONE
                }
                highlightActivePane()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun applyOrientationMode(orientation: Int) {
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            binding.panesContainer.orientation = android.widget.LinearLayout.HORIZONTAL
            binding.paneLeft.root.visibility = View.VISIBLE
            binding.paneRight.root.visibility = View.VISIBLE
        } else {
            binding.panesContainer.orientation = android.widget.LinearLayout.VERTICAL
            binding.paneLeft.root.visibility = if (activePaneIndex == 0) View.VISIBLE else View.GONE
            binding.paneRight.root.visibility = if (activePaneIndex == 1) View.VISIBLE else View.GONE
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientationMode(newConfig.orientation)
    }

    private fun setupPane(pane: LayoutFilePaneBinding, index: Int) {
        pane.root.setOnClickListener {
            activePaneIndex = index
            highlightActivePane()
        }
        pane.rvFiles.layoutManager = LinearLayoutManager(this)
        pane.tvBreadcrumb.text = panePaths[index].absolutePath

        pane.btnToggleView.setOnClickListener {
            val isGrid = pane.rvFiles.layoutManager is GridLayoutManager
            pane.rvFiles.layoutManager = if (isGrid) LinearLayoutManager(this) else GridLayoutManager(this, 3)
        }
    }

    private fun highlightActivePane() {
        binding.paneLeft.root.setBackgroundColor(if (activePaneIndex == 0) 0x222196F3 else 0x00000000)
        binding.paneRight.root.setBackgroundColor(if (activePaneIndex == 1) 0x224CAF50 else 0x00000000)
    }

    private fun setupBottomActions() {
        binding.btnCopy.setOnClickListener {
            val targetDir = panePaths[if (activePaneIndex == 0) 1 else 0]
            val items = selectedFiles.toList()
            if (items.isEmpty()) {
                binding.tvStatus.text = "Pilih file dulu"
                return@setOnClickListener
            }
            binding.progressTask.visibility = View.VISIBLE
            binding.progressTask.progress = 0
            lifecycleScope.launch(Dispatchers.IO) {
                items.forEachIndexed { idx, src ->
                    val dst = File(targetDir, src.name)
                    if (src.exists() && src.isFile) {
                        BufferedInputStream(src.inputStream(), 65536).use { input ->
                            BufferedOutputStream(FileOutputStream(dst), 65536).use { output ->
                                input.copyTo(output, bufferSize = 65536)
                            }
                        }
                    }
                    val pct = (((idx + 1).toFloat() / items.size) * 100).toInt()
                    withContext(Dispatchers.Main) { binding.progressTask.progress = pct }
                }
                withContext(Dispatchers.Main) {
                    binding.progressTask.visibility = View.GONE
                    binding.tvStatus.text = "Copy selesai ke ${targetDir.absolutePath}"
                }
            }
        }
    }
}
