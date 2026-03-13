package com.wype.security.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.File
import java.io.FileOutputStream

object ProvisioningEmail {
    private const val QR_FILENAME = "wype_provisioning.png"

    private val SUBJECT = "Your Wype Setup – Scan this QR to enable Device Owner"
    private val BODY = """
Hi there,

Thanks for purchasing Wype.
To activate voice-triggered factory reset, Wype needs to become the Device Owner on your device.

IMPORTANT – do these steps after your phone resets:
1. Connect to Wi-Fi
2. When Android asks “Copy apps & data?” — tap Don’t Copy
3. When you’re asked to scan a QR code — scan the Wype QR below
4. Wype will auto-install as Device Owner
5. Once setup is complete you can restore your Google backup normally

Wype never stores any of your personal data.
Please keep this email safe so you can access the QR when needed.

Kind regards,
Wype Team
""".trimIndent()

    private fun provisioningJson(): String = """
{
  "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.wype.security/.admin.WypeDeviceAdminReceiver",
  "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": true,
  "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": true
}
""".trimIndent()

    fun composeProvisioningEmail(context: Context, toEmail: String): Intent {
        val payload = provisioningJson()
        val bitmap = encodeAsBitmap(payload)
        val file = File(context.cacheDir, QR_FILENAME)
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(toEmail))
            putExtra(Intent.EXTRA_SUBJECT, SUBJECT)
            putExtra(Intent.EXTRA_TEXT, BODY)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Email your Wype QR")
    }

    private fun encodeAsBitmap(text: String): Bitmap {
        val m = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 800, 800)
        val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.ARGB_8888)
        for (x in 0 until m.width) for (y in 0 until m.height) {
            bmp.setPixel(x, y, if (m[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
        }
        return bmp
    }
}