package com.example.minimalistnotebook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchTextSheet(
    initialText: String = "",
    onDismiss: () -> Unit,
    onBatchSave: (List<String>) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // 🌟 统一的高级色彩规范
    val premiumBgColor = Color(0xFFFFF6ED) // 奶油暖白底色
    val tornCardColor = Color(0xFFFDFBF7)
    val deepText = Color(0xFF2C1E16)       // 深咖色文字
    val deepBurgundy = Color(0xFF3C120A)   // 深勃艮第红

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = premiumBgColor // 🌟 升级弹窗底色
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Batch Text Entry",
                fontSize = 24.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = deepText
            )

            Text(
                text = "Separate words with spaces, commas, or new lines.",
                fontSize = 14.sp,
                fontFamily = FontFamily.Serif,
                color = deepText.copy(alpha = 0.6f),
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(tornCardColor, RoundedCornerShape(16.dp))
                    .border(1.dp, deepText.copy(alpha = 0.1f), RoundedCornerShape(16.dp)) // 🌟 极细的浅色边框
                    .padding(16.dp)
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "e.g.\napple\nbanana, cherry\ndog",
                        color = deepText.copy(alpha = 0.3f),
                        fontFamily = FontFamily.Serif,
                        fontSize = 16.sp
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(
                        color = deepText,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Serif,
                        lineHeight = 24.sp
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 🌟 替换为高级胶囊按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(deepBurgundy, CircleShape)
                    .clickable {
                        val words = text.split(Regex("[\\s,;，；\n]+"))
                            .filter { it.isNotBlank() }
                            .map { it.trim().lowercase() }
                            .distinct()

                        if (words.isNotEmpty()) {
                            onBatchSave(words)
                        }
                        onDismiss()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text("Smart Import", fontSize = 18.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}