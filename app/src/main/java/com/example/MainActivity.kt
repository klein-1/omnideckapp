package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import com.example.app.DotkitShell

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        var sharedText: String? = null
        var sharedUri: Uri? = null
        var sharedTargetToolId: String? = null

        if (intent?.action == Intent.ACTION_SEND) {
            val mime = intent.type.orEmpty()
            when {
                mime == "text/plain" -> {
                    val txt = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!txt.isNullOrBlank()) {
                        sharedText = txt
                        val trimmed = txt.trim().lowercase()
                        sharedTargetToolId = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                            "url-safety"
                        } else {
                            "txt-case"
                        }
                    }
                }
                mime.startsWith("image/") -> {
                    sharedUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    if (sharedUri != null) sharedTargetToolId = "img-filters"
                }
                mime == "application/pdf" -> {
                    sharedUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    if (sharedUri != null) sharedTargetToolId = "pdf-split"
                }
            }
        }

        setContent {
            DotkitShell(
                initialSharedText = sharedText,
                initialSharedUri = sharedUri,
                initialSharedToolId = sharedTargetToolId
            )
        }
    }
}
