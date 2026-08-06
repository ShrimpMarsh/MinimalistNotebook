package com.example.minimalistnotebook.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

// 黑科技：免权限生成一个安全的图片 Uri 交给系统相机拍照
fun createImageUri(context: Context): Uri? {
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "ocr_img_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
    }
    return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
}

// 调用 ML Kit 引擎识别图片中的文字
fun processOcrImage(context: Context, uri: Uri, onResult: (String) -> Unit) {
    try {
        val image = InputImage.fromFilePath(context, uri)
        // 使用默认的拉丁字母识别器（完美支持英文单词提取）
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                if (visionText.text.isNotBlank()) {
                    onResult(visionText.text) // 识别成功，返回文字
                } else {
                    Toast.makeText(context, "No text recognized in the image.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Failed to process image.", Toast.LENGTH_SHORT).show()
            }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}