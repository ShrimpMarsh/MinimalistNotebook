package com.example.minimalistnotebook.ui.screens

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.minimalistnotebook.data.local.WordEntity
import com.example.minimalistnotebook.ui.QuestionType
import com.example.minimalistnotebook.ui.ReviewUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Composable
fun ReviewScreen(
    uiState: ReviewUiState,
    onSubmitAnswer: (Boolean) -> Unit,
    onSwipeNext: () -> Unit,
    onSwipePrevious: () -> Unit,
    onExit: () -> Unit
) {
    val premiumGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFF6ED), Color(0xFFEBE0D3))
    )
    val deepText = Color(0xFF2C1E16)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val mediaPlayer = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    val playAudio: () -> Unit = remember(uiState.currentWord?.id) {
        {
            uiState.currentWord?.word?.let { targetWord ->
                if (targetWord.isNotBlank()) {
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val encodedWord = URLEncoder.encode(targetWord, "UTF-8")
                            val reliableUrl = "https://dict.youdao.com/dictvoice?audio=$encodedWord&type=1"

                            val connection = URL(reliableUrl).openConnection() as HttpURLConnection
                            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
                            connection.connectTimeout = 5000
                            connection.readTimeout = 5000

                            val responseCode = connection.responseCode
                            if (responseCode != 200) {
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "网络被拒: HTTP $responseCode", Toast.LENGTH_LONG).show()
                                }
                                return@launch
                            }

                            val file = File(context.cacheDir, "temp_review_audio.mp3")
                            connection.inputStream.use { input ->
                                file.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }

                            withContext(Dispatchers.Main) {
                                try {
                                    mediaPlayer.reset()
                                    mediaPlayer.setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())

                                    FileInputStream(file).use { fis ->
                                        mediaPlayer.setDataSource(fis.fd)
                                    }

                                    mediaPlayer.prepare()
                                    mediaPlayer.start()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "播放器异常: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "下载失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
        }
    }

    // 🌟 核心修复 1：只有在选择题（CHOICE）模式下，才会在刚展示题目时自动发音
    LaunchedEffect(uiState.currentWord?.id) {
        if (!uiState.isFinished && uiState.questionType == QuestionType.CHOICE) {
            playAudio()
        }
    }

    val offsetX = remember { Animatable(0f) }
    val rotationY = remember { Animatable(0f) }

    LaunchedEffect(uiState.isRevealed, uiState.currentWord?.id) {
        if (uiState.isRevealed) {
            rotationY.animateTo(180f, tween(800, easing = FastOutSlowInEasing))
        } else {
            rotationY.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(premiumGradient)
            .pointerInput(uiState.isRevealed) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            if (offsetX.value < -150f && uiState.isRevealed) {
                                offsetX.animateTo(-1000f, tween(250))
                                onSwipeNext()
                                offsetX.snapTo(0f)
                            } else if (offsetX.value > 150f) {
                                offsetX.animateTo(1000f, tween(250))
                                onSwipePrevious()
                                offsetX.snapTo(0f)
                            } else {
                                offsetX.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
                            }
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 48.dp, start = 24.dp)
                .size(44.dp)
                .background(Color.White.copy(alpha = 0.6f), CircleShape)
                .border(1.dp, deepText.copy(alpha = 0.05f), CircleShape)
                .clickable { onExit() }
                .zIndex(10f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Back",
                tint = deepText,
                modifier = Modifier.size(28.dp)
            )
        }

        if (uiState.isFinished) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("All Caught Up!", fontSize = 32.sp, fontFamily = FontFamily.Serif, color = deepText)
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                            .clickable { onExit() }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("Back to Dashboard", fontSize = 16.sp, color = deepText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            uiState.currentWord?.let { word ->
                val cardModifier = Modifier
                    .fillMaxSize()
                    .padding(top = 110.dp, bottom = 90.dp, start = 24.dp, end = 24.dp)

                Box(
                    modifier = cardModifier
                        .offset(y = 16.dp)
                        .scale(0.92f)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFFFDFBF7).copy(alpha = 0.5f))
                )

                Box(
                    modifier = cardModifier
                        .graphicsLayer {
                            this.translationX = offsetX.value
                            this.rotationY = rotationY.value
                            if (uiState.isRevealed) {
                                this.rotationZ = offsetX.value / 35f
                            }
                            cameraDistance = 16f * density
                        }
                ) {
                    if (rotationY.value <= 90f) {
                        ReviewFrontCard(word, uiState, playAudio, onSubmitAnswer)
                    } else {
                        Box(modifier = Modifier.fillMaxSize().graphicsLayer { this.rotationY = 180f }) {
                            ReviewBackCard(word, playAudio)
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (uiState.isRevealed) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 40.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HandDrawnArrow(isLeft = true)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Swipe next", fontSize = 20.sp, fontFamily = FontFamily.Cursive, color = deepText.copy(alpha = 0.5f))
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Swipe back", fontSize = 20.sp, fontFamily = FontFamily.Cursive, color = deepText.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.width(8.dp))
                            HandDrawnArrow(isLeft = false)
                        }
                    }
                } else {
                    Text(
                        text = "Choose the correct answer",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Serif,
                        color = deepText.copy(alpha = 0.3f),
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun HandDrawnArrow(isLeft: Boolean) {
    val arrowColor = Color(0xFF2C1E16).copy(alpha = 0.4f)
    Canvas(modifier = Modifier.size(32.dp, 12.dp)) {
        val path = Path().apply {
            if (isLeft) {
                moveTo(size.width, size.height / 2 + 2f)
                quadraticTo(size.width / 2, size.height / 2 - 4f, 0f, size.height / 2)
                moveTo(8f, size.height / 2 - 5f)
                lineTo(0f, size.height / 2)
                lineTo(6f, size.height / 2 + 6f)
            } else {
                moveTo(0f, size.height / 2 - 2f)
                quadraticTo(size.width / 2, size.height / 2 + 4f, size.width, size.height / 2)
                moveTo(size.width - 8f, size.height / 2 - 5f)
                lineTo(size.width, size.height / 2)
                lineTo(size.width - 6f, size.height / 2 + 6f)
            }
        }
        drawPath(
            path = path,
            color = arrowColor,
            style = Stroke(
                width = 1.2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
fun ReviewFrontCard(word: WordEntity, uiState: ReviewUiState, playAudio: () -> Unit, onSubmit: (Boolean) -> Unit) {
    val tornCardColor = Color(0xFFFDFBF7)
    val deepText = Color(0xFF2C1E16)
    val correctColor = Color(0xFF4CAF50)
    val wrongColor = Color(0xFFE57373)
    val warmOrange = Color(0xFFD65A31)

    var selectedOption by remember(word.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val shakeOffset = remember { Animatable(0f) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(32.dp))
            .background(tornCardColor)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (uiState.questionType == QuestionType.CHOICE) {
            Text(
                word.word,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif,
                color = deepText,
                modifier = Modifier.clickable { playAudio() }
            )
            if (word.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(word.note, fontSize = 18.sp, color = deepText.copy(alpha = 0.5f), fontFamily = FontFamily.Serif)
            }
            Spacer(modifier = Modifier.height(48.dp))

            uiState.choiceOptions.forEach { option ->
                val isCorrect = word.englishMeaning.split("\n").map { it.trim() }.contains(option.trim())
                val isSelected = option == selectedOption

                val bgColor = when {
                    selectedOption != null && isCorrect -> correctColor.copy(alpha = 0.15f)
                    isSelected && !isCorrect -> wrongColor.copy(alpha = 0.15f)
                    else -> Color.White
                }

                val textColor = when {
                    selectedOption != null && isCorrect -> correctColor
                    isSelected && !isCorrect -> wrongColor
                    else -> deepText
                }

                val modifierOffset = if (isSelected && !isCorrect) shakeOffset.value else 0f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .offset(x = modifierOffset.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(bgColor)
                        .border(
                            width = 1.dp,
                            color = if (bgColor != Color.White) textColor.copy(alpha = 0.5f) else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable(enabled = selectedOption == null) {
                            selectedOption = option
                            scope.launch {
                                if (!isCorrect) {
                                    shakeOffset.animateTo(15f, tween(50))
                                    shakeOffset.animateTo(-15f, tween(50))
                                    shakeOffset.animateTo(15f, tween(50))
                                    shakeOffset.animateTo(0f, tween(50))
                                }
                                delay(800)
                                onSubmit(isCorrect)
                            }
                        }
                        .padding(20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    val annotatedOption = buildAnnotatedString {
                        val regex = Regex("^\\([a-zA-Z]+\\)")
                        val match = regex.find(option.trim())
                        if (match != null) {
                            withStyle(style = SpanStyle(color = warmOrange, fontWeight = FontWeight.Bold)) {
                                append(match.value)
                            }
                            append(" ")
                            append(option.trim().substring(match.range.last + 1).trim())
                        } else {
                            append(option.trim())
                        }
                    }

                    Text(
                        text = annotatedOption,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Serif,
                        color = textColor,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Start
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "I forget",
                fontSize = 15.sp,
                fontFamily = FontFamily.Serif,
                color = deepText.copy(alpha = 0.4f),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = selectedOption == null) {
                        selectedOption = "forget"
                        scope.launch {
                            delay(500)
                            onSubmit(false)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

        } else {
            val isSpelling = uiState.questionType == QuestionType.SPELLING
            val parsedSentence = word.sentence.split("|||").firstOrNull()?.trim() ?: ""

            val actualType = if (!isSpelling && parsedSentence.contains(word.word, ignoreCase = true)) {
                QuestionType.FILL_BLANK
            } else {
                QuestionType.SPELLING
            }

            var inputText by remember(word.id) { mutableStateOf("") }
            var evaluatedCorrect by remember(word.id) { mutableStateOf<Boolean?>(null) }

            val title = if (actualType == QuestionType.SPELLING) "Spell the word" else "Fill in the blank"

            Text(
                text = title,
                fontSize = 14.sp,
                color = deepText.copy(alpha = 0.5f),
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (actualType == QuestionType.FILL_BLANK) {
                val maskedSentence = parsedSentence.replace("(?i)${Regex.escape(word.word)}".toRegex(), "_________")
                Text(
                    text = maskedSentence,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Serif,
                    color = deepText,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                val hint = remember(word.id) {
                    word.englishMeaning.split("\n").filter { it.isNotBlank() }.randomOrNull()?.trim() ?: word.chineseMeaning
                }

                val annotatedHint = buildAnnotatedString {
                    val regex = Regex("^\\([a-zA-Z]+\\)")
                    val match = regex.find(hint)
                    if (match != null) {
                        withStyle(style = SpanStyle(color = warmOrange, fontWeight = FontWeight.Bold)) {
                            append(match.value)
                        }
                        append(" ")
                        append(hint.substring(match.range.last + 1).trim())
                    } else {
                        append(hint)
                    }
                }

                Text(
                    text = annotatedHint,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Serif,
                    color = deepText,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            val bgColor = when (evaluatedCorrect) {
                true -> correctColor.copy(alpha = 0.15f)
                false -> wrongColor.copy(alpha = 0.15f)
                null -> Color.White
            }

            val textColor = when (evaluatedCorrect) {
                true -> correctColor
                false -> wrongColor
                null -> deepText
            }

            val modifierOffset = if (evaluatedCorrect == false) shakeOffset.value else 0f

            BasicTextField(
                value = inputText,
                onValueChange = { if (evaluatedCorrect == null) inputText = it },
                textStyle = TextStyle(
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Serif,
                    color = textColor,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = modifierOffset.dp)
                    .background(bgColor, RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = if (bgColor != Color.White) textColor.copy(alpha = 0.5f) else deepText.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(20.dp),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.Center) {
                        if (inputText.isEmpty()) {
                            Text(
                                text = "Type your answer",
                                color = deepText.copy(alpha = 0.3f),
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Serif
                            )
                        }
                        innerTextField()
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (inputText.isNotBlank() && evaluatedCorrect == null) deepText else deepText.copy(alpha = 0.1f))
                    .clickable(enabled = inputText.isNotBlank() && evaluatedCorrect == null) {
                        val isCorrect = inputText.trim().equals(word.word, ignoreCase = true)
                        evaluatedCorrect = isCorrect

                        // 🌟 核心修复 2：提交答案（敲定对错）的瞬间，播放单词音频
                        playAudio()

                        scope.launch {
                            if (!isCorrect) {
                                shakeOffset.animateTo(15f, tween(50))
                                shakeOffset.animateTo(-15f, tween(50))
                                shakeOffset.animateTo(15f, tween(50))
                                shakeOffset.animateTo(0f, tween(50))
                            }
                            delay(800)
                            onSubmit(isCorrect)
                        }
                    }
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Submit",
                    color = if (inputText.isNotBlank() && evaluatedCorrect == null) Color.White else deepText.copy(alpha = 0.3f),
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "I forget",
                fontSize = 15.sp,
                fontFamily = FontFamily.Serif,
                color = deepText.copy(alpha = 0.4f),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = evaluatedCorrect == null) {
                        evaluatedCorrect = false
                        inputText = word.word

                        // 🌟 核心修复 3：点击忘记，自动补全正确答案的瞬间，播放单词音频
                        playAudio()

                        scope.launch {
                            delay(800)
                            onSubmit(false)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun ReviewBackCard(word: WordEntity, playAudio: () -> Unit) {
    val tornCardColor = Color(0xFFFDFBF7)
    val deepText = Color(0xFF2C1E16)
    val warmOrange = Color(0xFFD65A31)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(32.dp))
            .background(tornCardColor)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 48.dp, horizontal = 24.dp)
    ) {
        Text(
            word.word,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Serif,
            color = deepText,
            modifier = Modifier.clickable { playAudio() }
        )
        if (word.note.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(word.note, fontSize = 16.sp, color = deepText.copy(alpha = 0.5f), fontFamily = FontFamily.Serif)
        }
        Spacer(modifier = Modifier.height(24.dp))

        Text(word.englishMeaning, fontSize = 18.sp, fontFamily = FontFamily.Serif, color = deepText, lineHeight = 26.sp)
        Spacer(modifier = Modifier.height(32.dp))

        val parts = word.sentence.split("|||")
        val example = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
        val note = parts.getOrNull(1)?.takeIf { it.isNotBlank() }

        if (example != null) {
            Row {
                Box(modifier = Modifier.width(3.dp).height(24.dp).background(warmOrange, RoundedCornerShape(1.dp)))
                Spacer(modifier = Modifier.width(12.dp))
                Text(example, fontSize = 16.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, color = deepText.copy(alpha = 0.7f), lineHeight = 24.sp)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (note != null) {
            Text("📝 Note: $note", fontSize = 15.sp, fontFamily = FontFamily.Serif, color = deepText.copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(24.dp))
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))
        Spacer(modifier = Modifier.height(32.dp))

        if (word.chineseMeaning.isNotBlank()) {
            com.example.minimalistnotebook.ui.components.ScratchOffHint(
                hintText = word.chineseMeaning,
                onHintChange = { }
            )
        }
    }
}