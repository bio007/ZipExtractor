package com.kacera.zipextractor

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputLayout
import com.obsez.android.lib.filechooser.ChooserDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.headers.HeaderReader
import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.model.LocalFileHeader
import net.lingala.zip4j.util.InternalZipConstants.CHARSET_UTF_8
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.io.path.Path


private const val TYPE_ZIP = "application/zip"

class ExtractActivity : ComponentActivity() {

    private lateinit var dialog: AlertDialog

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent: Intent? = intent
        val uri = intent?.data

        if (intent?.type != TYPE_ZIP || uri == null) {
            finish()
            overridePendingTransition(0, 0)
            return
        }

        dialog = AlertDialog.Builder(this, R.style.Theme_Extractor_Dialog)
            .setTitle(getString(R.string.extracting))
            .setView(R.layout.layout_progress)
            .setCancelable(false)
            .create()

        lifecycleScope.launchWhenStarted {
            val rootDir = extract(uri)
            if (rootDir != null) {
                ChooserDialog(this@ExtractActivity, R.style.Theme_Extractor_ChooserDialog)
                    .withStartFile(rootDir.path)
                    .displayPath(false)
                    .withNavigateUpTo { it != cacheDir }
                    .withChosenListener { path, file ->
                        val folderIntent = Intent(Intent.ACTION_VIEW)
                        folderIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        folderIntent.setDataAndType(
                            FileProvider.getUriForFile(
                                this@ExtractActivity,
                                "${applicationContext.packageName}.provider",
                                file
                            ),
                            guessType(MimeTypeMap.getFileExtensionFromUrl(path))
                        )

                        finish()
                        overridePendingTransition(0, 0)

                        startActivity(
                            Intent.createChooser(
                                folderIntent,
                                getString(R.string.open_with)
                            )
                        )
                    }
                    .withNegativeButton(R.string.close) { dialog, _ ->
                        dialog?.cancel()
                        finish()
                        overridePendingTransition(0, 0)
                    }
                    .withOnCancelListener { dialog ->
                        dialog?.cancel()
                        finish()
                        overridePendingTransition(0, 0)
                    }
                    .build()
                    .show()
            } else {
                Toast.makeText(this@ExtractActivity, "Failed to open archive ${uri.lastPathSegment}", Toast.LENGTH_LONG).show()
                finish()
                overridePendingTransition(0, 0)
            }
        }
    }

    private suspend fun showProgress() {
        withContext(Dispatchers.Main) {
            dialog.show()
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private suspend fun extract(uri: Uri): File? {
        return withContext(Dispatchers.IO) {
            try {
                contentResolver.openInputStream(uri)?.use {
                    with(ZipInputStream(it) { runBlocking { obtainPassword(uri) } }) {
                        var header: LocalFileHeader? = nextEntry
                        var readLen: Int
                        val readBuffer = ByteArray(4096)

                        showProgress()
                        delay(250)

                        val zipRoot = File(cacheDir, "${UUID.randomUUID()}")

                        while (header != null) {
                            if (header.isDirectory) {
                                header = nextEntry
                                continue
                            }

                            val outputFile = with(Path(header.fileName)) {
                                val parent = parent?.let {
                                    File(zipRoot, "$parent")
                                } ?: zipRoot

                                parent.mkdirs()

                                File(parent, fileName.toString()).apply {
                                    createNewFile()
                                    deleteOnExit()
                                }
                            }
                            outputFile.outputStream().use { output ->
                                while (read(readBuffer).also { bytesRead ->
                                        readLen = bytesRead
                                    } != -1) {
                                    output.write(readBuffer, 0, readLen)
                                }
                            }
                            header = nextEntry
                        }

                        zipRoot
                    }
                }
            } catch (e: ZipException) {
                e.message?.let { Log.i("ZIP", it) }
                null
            } catch (ignored: IOException) {
                ignored.printStackTrace()
                null
            } finally {
                dialog.dismiss()
            }
        }
    }

    @WorkerThread
    @Suppress("BlockingMethodInNonBlockingContext")
    @Throws(IOException::class)
    private suspend fun obtainPassword(uri: Uri): CharArray? =
        contentResolver.openInputStream(uri)?.use {
            if (HeaderReader().readLocalFileHeader(it, CHARSET_UTF_8).isEncrypted) {
                withContext(Dispatchers.Main) {
                    suspendCoroutine { continuation ->
                        showPasswordDialog(continuation)
                    }
                }
            } else {
                null
            }
        }

    @SuppressLint("InflateParams")
    private fun showPasswordDialog(continuation: Continuation<CharArray?>) {
        val dialog = AlertDialog.Builder(this, R.style.Theme_Extractor)
            .setView(R.layout.layout_input)
            .setTitle(getString(R.string.insert_password))
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                with(dialog as AlertDialog) {
                    findViewById<TextInputLayout>(R.id.input)?.editText?.text?.toString()?.let {
                        continuation.resume(it.toCharArray())
                    }
                }
            }
            .setOnCancelListener {
                continuation.resume(null)
            }
            .create()
        dialog.setOnShowListener {
            with(it as AlertDialog) {
                val editText = findViewById<TextInputLayout>(R.id.input)?.editText
                editText?.setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        dismiss()
                        editText.text?.toString()?.let { password ->
                            continuation.resume(password.toCharArray())
                        }
                        true
                    } else {
                        false
                    }
                }
            }
        }
        dialog.show()
    }

    private fun guessType(extension: String): String {
        return when (extension) {
            "png", "jpg", "jpeg", "bmp", "webp", "gif" -> "image/$extension"
            "mov", "mp4", "mpeg", "ogg" -> "video/$extension"
            "txt", "rft", "xml", "css" -> "text/plain"
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx" -> "application/$extension"
            else -> {
                Log.d("XXX", "Unknown type - $extension")
                "application/*"
            }
        }
    }
}