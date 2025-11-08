package com.wype.security.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.File
import java.io.FileOutputStream

object ProvisioningEmail {
    
    private const val PROVISIONING_PAYLOAD = "https://example.com/wype-provision?comp=com.wype.security/.provisioning.DeviceAdminRcvr"
    
    fun composeProvisioningEmail(context: Context, toEmail: String): Intent {
        // Generate QR code bitmap
        val qrBitmap = generateQrCode(PROVISIONING_PAYLOAD, 512, 512)
        
        // Save QR bitmap to cache
        val cacheDir = context.cacheDir
        val qrFile = File(cacheDir, "wype_provisioning.png")
        FileOutputStream(qrFile).use { out ->
            qrBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        
        // Get FileProvider URI
        val authority = "${context.packageName}.fileprovider"
        val qrUri = FileProvider.getUriForFile(context, authority, qrFile)
        
        // Compose email intent
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(toEmail))
            putExtra(Intent.EXTRA_SUBJECT, "Your Wype Setup – Scan this QR to enable Device Owner")
            putExtra(Intent.EXTRA_TEXT, """Hello,

Please scan the attached QR code with your device to complete the Wype Device Owner setup.

Instructions:
1. Open the QR scanner on your device
2. Scan the attached QR code image
3. Follow the on-screen prompts to enable Device Owner mode

The QR code contains a provisioning payload that will configure your device for secure management.

Best regards,
Wype Security Team""")
            putExtra(Intent.EXTRA_STREAM, qrUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        return Intent.createChooser(emailIntent, "Send provisioning email")
    }
    
    private fun generateQrCode(content: String, width: Int, height: Int): Bitmap {
        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, width, height)
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
