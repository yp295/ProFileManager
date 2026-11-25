package com.yuda.filemanager

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yuda.filemanager.databinding.ActivityTextEditorBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TextEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTextEditorBinding
    private lateinit var file: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTextEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val path = intent.getStringExtra("path")
        if (path == null) {
            finish()
            return
        }
        file = File(path)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = file.name
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnSave.setOnClickListener { saveFile() }
        loadFile()
    }

    private fun loadFile() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (file.length() > 2 * 1024 * 1024) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@TextEditorActivity,
                            "File terlalu besar untuk diedit",
                            Toast.LENGTH_LONG
                        ).show()
                        finish()
                    }
                    return@launch
                }
                val text = file.readText()
                withContext(Dispatchers.Main) { binding.etContent.setText(text) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TextEditorActivity, "Tidak bisa membuka file", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    private fun saveFile() {
        val text = binding.etContent.text.toString()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                file.writeText(text)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TextEditorActivity, "Disimpan", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@TextEditorActivity, "Gagal menyimpan", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
