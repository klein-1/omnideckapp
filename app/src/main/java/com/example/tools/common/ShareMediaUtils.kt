package com.example.tools.common

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File
import java.io.FileOutputStream

object ShareMediaUtils {

    fun uriForCacheFile(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        runCatching {
            val uri = uriForCacheFile(context, file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun writeTempFile(context: Context, fileName: String, bytes: ByteArray): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { it.write(bytes) }
        return file
    }

    fun shareBitmapPng(context: Context, bmp: Bitmap, fileNamePrefix: String = "omnideck_export") {
        val file = File(context.cacheDir, "${fileNamePrefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        shareFile(context, file, "image/png", "Share Image")
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512, fg: Int = 0xFF141413.toInt(), bg: Int = 0xFFFFFFFF.toInt()): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = QRCodeWriter().encode(content.ifBlank { " " }, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (y in 0 until sizePx) {
            for (x in 0 until sizePx) {
                bmp.setPixel(x, y, if (matrix.get(x, y)) fg else bg)
            }
        }
        return bmp
    }
}
