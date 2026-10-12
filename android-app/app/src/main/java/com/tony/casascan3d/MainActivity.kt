package com.tony.casascan3d

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
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
    private lateinit var guidance: TextView
    private lateinit var preview: ImageView
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
            setPadding(32, 36, 32, 36)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val title = TextView(this).apply { text = "CasaScan 3D"; textSize = 28f }
        status = TextView(this).apply { textSize = 18f; setPadding(0, 22, 0, 16) }
        val newButton = Button(this).apply {
            text = "Nuova scansione"
            setOnClickListener { newSession() }
        }
        photoButton = Button(this).apply {
            text = "Scatta foto"
            setOnClickListener { takePhoto() }
        }
        exportButton = Button(this).apply {
            text = "Esporta ZIP per PC"
            setOnClickListener { exportZip() }
        }
        guidance = TextView(this).apply {
            textSize = 16f
            setPadding(0, 22, 0, 20)
        }
        val previewTitle = TextView(this).apply {
            text = "Ultima fotografia (riferimento per lo scatto successivo)"
            textSize = 15f
        }
        preview = ImageView(this).apply {
            adjustViewBounds = true
            maxHeight = (resources.displayMetrics.density * 300).toInt()
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Anteprima dell'ultima foto acquisita"
        }
        listOf(title, status, newButton, photoButton, exportButton, guidance, previewTitle, preview)
            .forEach { layout.addView(it) }
        setContentView(ScrollView(this).apply { addView(layout) })
        updateUi()
    }

    private fun sessionDir(): File = File(filesDir, "captures/$currentSession")

    private fun photos(): List<File> =
        if (currentSession.isBlank()) emptyList()
        else sessionDir().listFiles { f -> f.isFile && f.extension.equals("jpg", true) }
            ?.sortedBy { it.name } ?: emptyList()

    private fun newSession() {
        currentSession = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        getPreferences(MODE_PRIVATE).edit().putString("session", currentSession).apply()
        updateUi()
    }

    private fun updateUi() {
        val files = photos()
        photoCount = files.size
        status.text = if (currentSession.isBlank()) {
            "Inizia una nuova scansione"
        } else {
            "Sessione: $currentSession\nFoto acquisite: $photoCount / 40-60 consigliate"
        }
        photoButton.isEnabled = currentSession.isNotBlank()
        exportButton.isEnabled = photoCount > 0
        guidance.text = when {
            currentSession.isBlank() -> "Crea una sessione per iniziare."
            photoCount == 0 -> "Parti da un angolo della stanza. Mantieni lo zoom fisso e fotografa dettagli riconoscibili."
            photoCount < 10 -> "Spostati lateralmente di circa 20-40 cm tra gli scatti. Mantieni il 70-80% della scena in comune con la foto precedente."
            photoCount < 40 -> "Continua lungo il perimetro della stanza. Inquadra mobili e spigoli da posizioni diverse, non ruotare soltanto sul posto."
            else -> "Copri anche gli angoli, le parti alte e i dettagli mancanti. Controlla che le fotografie consecutive condividano elementi riconoscibili."
        }
        preview.setImageDrawable(null)
        files.lastOrNull()?.let { file ->
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            val sample = generateSequence(1) { it * 2 }
                .first { bounds.outWidth / it <= 1000 && bounds.outHeight / it <= 1000 }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            preview.setImageBitmap(BitmapFactory.decodeFile(file.absolutePath, options))
        }
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
        try {
            startActivityForResult(intent, captureRequest)
        } catch (e: Exception) {
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
                if (resultCode != RESULT_OK || pendingPhoto?.length() == 0L) pendingPhoto?.delete()
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
        val files = photos()
        require(files.isNotEmpty()) { "Nessuna foto" }
        contentResolver.openOutputStream(destination)?.use { output ->
            ZipOutputStream(output).use { zip ->
                val manifest = """{"format":"CasaScan3D-v1","session":"$currentSession","count":${files.size}}"""
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                files.forEach { photo ->
                    zip.putNextEntry(ZipEntry("images/${photo.name}"))
                    FileInputStream(photo).use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: error("Impossibile aprire la destinazione")
    }
}
