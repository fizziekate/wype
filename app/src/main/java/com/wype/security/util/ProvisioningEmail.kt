package com.wype.security.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.io.File
import java.io.FileOutputStream

object ProvisioningEmail {
    
    private const val QR_SIZE = 512
    
    /**
     * Generates a provisioning QR code bitmap from the given component name
     */
    fun generateProvisioningQrBitmap(componentName: String): Bitmap {
        // Create a simplified provisioning URL with the component
        val provisioningUrl = "https://example.com/wype-provision?comp=$componentName"
        
        // Generate QR code using ZXing
        val writer = MultiFormatWriter()
        val bitMatrix: BitMatrix = writer.encode(
            provisioningUrl,
            BarcodeFormat.QR_CODE,
            QR_SIZE,
            QR_SIZE
        )
        
        return encodeAsBitmap(bitMatrix)
    }
    
    /**
     * Converts a BitMatrix to a Bitmap
     */
    private fun encodeAsBitmap(bitMatrix: BitMatrix): Bitmap {
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        
        return bitmap
    }
    
    /**
     * Writes QR bitmap to cache and returns the file
     */
    fun writeQrToCache(context: Context, bitmap: Bitmap): File {
        val cacheDir = context.cacheDir
        val qrFile = File(cacheDir, "provisioning_qr.png")
        
        FileOutputStream(qrFile).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        }
        
        return qrFile
    }
    
    /**
     * Sends an email with the provisioning QR code attached
     */
    fun sendProvisioningEmail(context: Context, recipientEmail: String, qrFile: File) {
        val qrUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            qrFile
        )
        
        val emailIntent = Intent(Intent.ACTION_SEND).apply {
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipientEmail))
            putExtra(Intent.EXTRA_SUBJECT, "Wype Device Owner Provisioning QR Code")
            putExtra(Intent.EXTRA_TEXT, 
                "Please scan this QR code on your new device during setup to enable Wype Device Owner mode.\n\n" +
                "Instructions:\n" +
                "1. Factory reset your device\n" +
                "2. During initial setup, tap the welcome screen 6 times\n" +
                "3. Scan the attached QR code\n" +
                "4. Follow the on-screen instructions"
            )
            putExtra(Intent.EXTRA_STREAM, qrUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        
        val chooser = Intent.createChooser(emailIntent, "Send provisioning QR via email")
        context.startActivity(chooser)
    }
}
