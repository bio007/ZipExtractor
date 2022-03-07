package com.kacera.zipintenttestapp

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.FileProvider
import com.kacera.zipintenttestapp.ui.theme.ZipIntentTestAppTheme
import java.io.File


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZipIntentTestAppTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    CenterScreen()
                }
            }
        }
    }

    @Composable
    fun CenterScreen() {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button("ZIP")
        }
    }

    @Composable
    fun Button(fileType: String) {
        TextButton(onClick = {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.setDataAndType(
                FileProvider.getUriForFile(
                    this,
                    "${applicationContext.packageName}.provider",
                    File(filesDir, "test.zip")
                ),
                "application/zip"
            )

            try {
                startActivity(Intent.createChooser(intent, "Open ZIP?"))
            } catch (e: ActivityNotFoundException) {
                Log.e("ZIP Opener", "No App found!")
            }
        }) {
            Text(text = "Open $fileType")
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun DefaultPreview() {
        ZipIntentTestAppTheme {
            CenterScreen()
        }
    }
}