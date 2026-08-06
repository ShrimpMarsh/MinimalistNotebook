package com.example.minimalistnotebook.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minimalistnotebook.ui.theme.InkBlack
import com.example.minimalistnotebook.ui.theme.PaperBackground
import com.example.minimalistnotebook.ui.theme.RetroAmber
import com.example.minimalistnotebook.ui.theme.SlateGray
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 1. 修复后的复古按压按钮
@Composable
fun RetroButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shadowColor: androidx.compose.ui.graphics.Color = com.example.minimalistnotebook.ui.theme.RetroAmber,
    shadowOffset: androidx.compose.ui.unit.Dp = 4.dp
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    // 🌟 修复：必须是通过 interactionSource 来点出这个方法
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedOffset by androidx.compose.animation.core.animateDpAsState(
        targetValue = if (isPressed) 0.dp else shadowOffset,
        animationSpec = androidx.compose.animation.core.tween(100), label = ""
    )

    Box(
        modifier = modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    ) {
        // 底层阴影
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(top = shadowOffset, start = shadowOffset)
                .background(shadowColor, androidx.compose.foundation.shape.CircleShape)
                .border(2.dp, com.example.minimalistnotebook.ui.theme.InkBlack, androidx.compose.foundation.shape.CircleShape)
        )
        // 表层按钮
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = animatedOffset, end = animatedOffset)
                .background(com.example.minimalistnotebook.ui.theme.PaperBackground, androidx.compose.foundation.shape.CircleShape)
                .border(2.dp, com.example.minimalistnotebook.ui.theme.InkBlack, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            androidx.compose.material3.Text(
                text = text,
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                color = com.example.minimalistnotebook.ui.theme.InkBlack
            )
        }
    }
}

// 2. 复古展示卡片 (带黑色硬阴影，用于展示单词)
@Composable
fun RetroCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        // 底层：黑色硬阴影
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(start = 4.dp, top = 4.dp)
                .background(InkBlack, RoundedCornerShape(16.dp))
        )
        // 顶层：卡片本体
        Box(
            modifier = Modifier
                .padding(end = 4.dp, bottom = 4.dp)
                .background(PaperBackground, RoundedCornerShape(16.dp))
                .border(2.dp, InkBlack, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            content() // 卡片里面放什么由外部决定
        }
    }
}

// 🎨 终极进化版：“斜向参差蜡笔”刮刮乐组件
@Composable
fun ScratchOffHint(
    hintText: String,
    onHintChange: (String) -> Unit
) {
    // 🌟 如果没有释义，直接默认刮开状态，方便用户输入
    var isRevealed by remember(hintText.isEmpty()) { mutableStateOf(hintText.isBlank()) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        // 1. 底层：变成了【自由编辑的输入框】
        BasicTextField(
            value = hintText,
            onValueChange = onHintChange,
            textStyle = TextStyle(
                fontSize = 14.sp,
                fontFamily = FontFamily.Serif,
                color = Color(0xFF555555), // SlateGray
                lineHeight = 20.sp
            ),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                // 给输入框加一个微弱的背景，暗示这里可以打字
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.03f), RoundedCornerShape(4.dp))
                    .padding(8.dp)
                ) {
                    if (hintText.isEmpty()) {
                        Text("Type Chinese meaning...", color = Color.Gray.copy(alpha = 0.5f), fontSize = 14.sp, fontFamily = FontFamily.Serif)
                    }
                    innerTextField()
                }
            }
        )

        // 2. 顶层：刮刮卡遮罩 (如果被揭开或者内容为空，则消失)
        AnimatedVisibility(
            visible = !isRevealed,
            exit = fadeOut(animationSpec = tween(600)) + shrinkHorizontally(
                animationSpec = tween(600),
                shrinkTowards = Alignment.End
            ),
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { isRevealed = true }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .drawBehind {
                        drawRect(color = Color(0xFFE5E5E5L))
                        val stripeWidth = 2.dp.toPx()
                        var x = -size.height
                        while (x < size.width) {
                            drawLine(
                                color = Color(0xFFB0B0B0L),
                                start = Offset(x, size.height),
                                end = Offset(x + size.height, 0f),
                                strokeWidth = stripeWidth
                            )
                            x += stripeWidth * 3f
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tap to reveal hint",
                    fontSize = 12.sp,
                    color = Color.DarkGray.copy(alpha = 0.8f),
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}