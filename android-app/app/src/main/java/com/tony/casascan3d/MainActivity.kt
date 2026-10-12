package com.tony.casascan3d

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : Activity() {
    private val captureRequest = 201
    private val exportRequest = 202
    private lateinit var status: TextView
    private lateinit var photoButton: Button
    private lateinit var exportButton: Button
    private var currentSession = ""
    private var pendingPhoto: File? = null
    private var photoCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentSession = getPreferences(MODE_PRIVATE).getString("session", "") ?: ""
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 56, 40, 30)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply { text = "CasaScan 3D"; textSize = 29f }
        status = TextView(this).apply { textSize = 17f; setPadding(0, 30, 0, 30) }
        val newButton = Button(this).apply { text = "Nuova scansione"; setOnClickListener { newSession() } }
        photoButton = Button(this).apply { text = "Scatta foto"; setOnClickListener { takePhoto() } }
        exportButton = Button(this).apply { text = "Esporta ZIP per PC"; setOnClickListener { exportZip() } }
        val help = TextView(this).apply {
            text = "Scatta foto sovrapposte mentre ti sposti lentamente. Mantieni lo stesso zoom. Esporta lo ZIP e ricostruisci con COLMAP sul PC."
            textSize = 15f
            setPadding(0, 25, 0, 0)
        }
        listOf(title, status, newButton, photoButton, exportButton, help).forEach { layout.addView(it) }
        setContentView(layout)
        updateUi()
    }

    private fun sessionDir(): File = File(filesDir, "captures/$currentSession")

    private fun newSession() {
        currentSession = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        getPreferences(MODE_PRIVATE).edit().putString("session", currentSession).apply()
        updateUi()
    }

    private fun updateUi() {
        photoCount = if (currentSession.isBlank()) 0 else sessionDir().listFiles { f -> f.extension.lowercase() == "jpg" }?.size ?: 0
        status.text = if (currentSession.isBlank()) "Inizia una nuova scansione" else "Sessione: $currentSession\nFoto acquisite: $photoCount"
        photoButton.isEnabled = currentSession.isNotBlank()
        exportButton.isEnabled = photoCount > 0
    }

    private fun takePhoto() {
        if (currentSession.isBlank()) return
        val file = File(sessionDir().apply { mkdirs() }, "img_${System.currentTimeMillis()}.jpg")
        pendingPhoto = file
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newUri(contentResolver, "CasaScan photo", uri)
        }
        try { startActivityForResult(intent, captureRequest) }
        catch (e: Exception) {
            pendingPhoto = null
            Toast.makeText(this, "Fotocamera non disponibile: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportZip() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
            putExtra(Intent.EXTRA_TITLE, "CasaScan_$currentSession.zip")
        }
        startActivityForResult(intent, exportRequest)
    }

    @Deprecated("Legacy activity result for prototype")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            captureRequest -> {
                if (resultCode != RESULT_OK) pendingPhoto?.delete()
                pendingPhoto = null
                updateUi()
            }
            exportRequest -> if (resultCode == RESULT_OK && data?.data != null) {
                try {
                    writeZip(data.data!!)
                    Toast.makeText(this, "ZIP esportato", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Errore export: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun writeZip(destination: Uri) {
        val photos = sessionDir().listFiles { f -> f.isFile && f.extension.lowercase() == "jpg" }?.sortedBy { it.name } ?: emptyList()
        require(photos.isNotEmpty()) { "Nessuna foto" }
        contentResolver.openOutputStream(destination)?.use { output ->
            ZipOutputStream(output).use { zip ->
                val manifest = """{"format":"CasaScan3D-v1","session":"$currentSession","count":${photos.size}}"""
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                photos.forEach { photo ->
                    zip.putNextEntry(ZipEntry("images/${photo.name}"))
                    FileInputStream(photo).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: error("Impossibile aprire la destinazione")
    }
}
