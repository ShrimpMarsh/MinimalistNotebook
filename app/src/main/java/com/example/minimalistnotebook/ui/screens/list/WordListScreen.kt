package com.example.minimalistnotebook.ui.screens.list

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minimalistnotebook.data.local.WordEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

// 🌟 解析辅助函数：用于分离例句和笔记
private fun parseSentence(raw: String): Pair<String, String> {
    return if (raw.contains("|||")) {
        val parts = raw.split("|||")
        parts[0] to parts.getOrElse(1) { "" }
    } else {
        raw to ""
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WordListScreen(
    wordList: List<WordEntity>,
    onBack: () -> Unit,
    onUpdateWord: (WordEntity) -> Unit,
    onDeleteWord: (WordEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val premiumGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFF6ED), Color(0xFFEBE0D3))
    )
    val tornCardColor = Color(0xFFFDFBF7)
    val deepBurgundy = Color(0xFF3C120A)
    val warmOrange = Color(0xFFD65A31)
    val deepText = Color(0xFF2C1E16)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isFlashcardMode by remember { mutableStateOf(false) }
    var editingWord by remember { mutableStateOf<WordEntity?>(null) }

    val mediaPlayer = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    val onPlayAudio: (String) -> Unit = remember {
        { url ->
            if (url.isNotBlank()) {
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val connection = URL(url).openConnection() as HttpURLConnection
                        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000

                        val responseCode = connection.responseCode
                        if (responseCode != 200) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "网络被拒: HTTP $responseCode", Toast.LENGTH_SHORT).show()
                            }
                            return@launch
                        }

                        val file = File(context.cacheDir, "temp_list_audio.mp3")
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
                                Toast.makeText(context, "播放器异常: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "下载失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(premiumGradient)
                .padding(top = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WordListBackButton(onClick = onBack, iconColor = deepText)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp, top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "My Word List",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = deepText
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "You have saved ${wordList.size} words",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Serif,
                        color = deepText.copy(alpha = 0.6f)
                    )
                }

                PremiumToggleSwitch(
                    isChecked = isFlashcardMode,
                    onCheckedChange = { isFlashcardMode = it },
                    cardColor = tornCardColor,
                    accentColor = warmOrange,
                    borderColor = deepText.copy(alpha = 0.1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Crossfade(targetState = isFlashcardMode, label = "mode_crossfade") { isFlashcard ->
                if (isFlashcard) {
                    if (wordList.isEmpty()) {
                        EmptyStateText(textColor = deepText.copy(alpha = 0.5f))
                    } else {
                        val pagerState = rememberPagerState(pageCount = { wordList.size })
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 48.dp),
                            pageSpacing = 16.dp
                        ) { page ->
                            FlashcardItem(
                                word = wordList[page],
                                cardColor = tornCardColor,
                                deepText = deepText,
                                shadowColor = deepBurgundy,
                                accentColor = warmOrange,
                                onPlayAudio = onPlayAudio
                            )
                        }
                    }
                } else {
                    if (wordList.isEmpty()) {
                        EmptyStateText(textColor = deepText.copy(alpha = 0.5f))
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(wordList, key = { it.word }) { wordEntity ->
                                WordCard(
                                    word = wordEntity,
                                    cardColor = tornCardColor,
                                    deepText = deepText,
                                    shadowColor = deepBurgundy,
                                    accentColor = warmOrange,
                                    onPlayAudio = onPlayAudio,
                                    onEditClick = { editingWord = it }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (editingWord != null) {
            EditWordSheet(
                word = editingWord!!,
                bgColor = Color(0xFFFFF6ED),
                cardColor = tornCardColor,
                deepText = deepText,
                deepBurgundy = deepBurgundy,
                onDismiss = { editingWord = null },
                onSave = { updatedWord ->
                    onUpdateWord(updatedWord)
                    editingWord = null
                },
                onDelete = { wordToDelete ->
                    onDeleteWord(wordToDelete)
                    editingWord = null
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWordSheet(
    word: WordEntity,
    bgColor: Color,
    cardColor: Color,
    deepText: Color,
    deepBurgundy: Color,
    onDismiss: () -> Unit,
    onSave: (WordEntity) -> Unit,
    onDelete: (WordEntity) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var editedWord by remember { mutableStateOf(word.word) }
    var editedPhonetic by remember { mutableStateOf(word.note) }

    val initialMeanings = remember { word.englishMeaning.split("\n").filter { it.isNotBlank() } }
    val editedMeanings = remember { mutableStateListOf<String>().apply { addAll(initialMeanings) } }

    val parsedData = remember { parseSentence(word.sentence) }
    var editedExample by remember { mutableStateOf(parsedData.first) }
    var editedMyNotes by remember { mutableStateOf(parsedData.second) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = bgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit Notebook",
                    fontSize = 24.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = deepText
                )
                Text(
                    text = "Delete 🗑️",
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFC62828),
                    modifier = Modifier.clickable { onDelete(word) }.padding(8.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Word", fontSize = 14.sp, fontFamily = FontFamily.Serif, color = deepText.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
            PremiumEditTextField(value = editedWord, onValueChange = { editedWord = it }, cardColor = cardColor, deepText = deepText, singleLine = true)

            Spacer(modifier = Modifier.height(16.dp))

            Text("Phonetic (Optional)", fontSize = 14.sp, fontFamily = FontFamily.Serif, color = deepText.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
            PremiumEditTextField(value = editedPhonetic, onValueChange = { editedPhonetic = it }, cardColor = cardColor, deepText = deepText, singleLine = true)

            Spacer(modifier = Modifier.height(16.dp))

            Text("Meanings", fontSize = 14.sp, fontFamily = FontFamily.Serif, color = deepText.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
            editedMeanings.forEachIndexed { index, meaning ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PremiumEditTextField(
                        value = meaning,
                        onValueChange = { editedMeanings[index] = it },
                        cardColor = cardColor,
                        deepText = deepText,
                        singleLine = false,
                        minLines = 2,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "✖",
                        fontSize = 16.sp,
                        color = deepText.copy(alpha = 0.3f),
                        modifier = Modifier.clickable { editedMeanings.removeAt(index) }.padding(8.dp)
                    )
                }
            }

            Text(
                text = "+ Add New Meaning",
                fontSize = 14.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = deepText.copy(alpha = 0.5f),
                modifier = Modifier.clickable { editedMeanings.add("") }.padding(vertical = 8.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Example Sentence", fontSize = 14.sp, fontFamily = FontFamily.Serif, color = deepText.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
            PremiumEditTextField(value = editedExample, onValueChange = { editedExample = it }, cardColor = cardColor, deepText = deepText, singleLine = false, minLines = 2)

            Spacer(modifier = Modifier.height(16.dp))

            Text("My Free Notes", fontSize = 14.sp, fontFamily = FontFamily.Serif, color = deepText.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
            PremiumEditTextField(value = editedMyNotes, onValueChange = { editedMyNotes = it }, cardColor = cardColor, deepText = deepText, singleLine = false, minLines = 3)

            Spacer(modifier = Modifier.height(32.dp))

            WordListPillButton(
                text = "Save Changes",
                onClick = {
                    if (editedWord.isNotBlank()) {
                        onSave(
                            word.copy(
                                word = editedWord.trim(),
                                note = editedPhonetic.trim(),
                                englishMeaning = editedMeanings.filter { it.isNotBlank() }.joinToString("\n").trim(),
                                sentence = "${editedExample.trim()}|||${editedMyNotes.trim()}"
                            )
                        )
                    }
                },
                isPrimary = true,
                baseColor = deepBurgundy
            )
        }
    }
}

@Composable
fun FlashcardItem(
    word: WordEntity,
    cardColor: Color,
    deepText: Color,
    shadowColor: Color,
    accentColor: Color,
    onPlayAudio: (String) -> Unit
) {
    var isFlipped by remember { mutableStateOf(false) }
    var isPeeking by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "card_flip"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .padding(vertical = 16.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { isFlipped = !isFlipped },
                    onLongPress = {
                        if (isFlipped && word.chineseMeaning.isNotBlank()) {
                            isPeeking = true
                        }
                    },
                    onPress = {
                        tryAwaitRelease()
                        isPeeking = false
                    }
                )
            }
    ) {
        Box(modifier = Modifier.matchParentSize().padding(start = 6.dp, top = 6.dp).background(shadowColor.copy(alpha = 0.15f), RoundedCornerShape(24.dp)))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 6.dp, bottom = 6.dp)
                .background(cardColor, RoundedCornerShape(24.dp))
                .border(1.dp, deepText.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (rotation <= 90f) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = word.word,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color = deepText,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (word.note.isNotBlank()) {
                                Text(
                                    text = word.note,
                                    fontSize = 18.sp,
                                    fontFamily = FontFamily.Serif,
                                    color = deepText.copy(alpha = 0.6f)
                                )
                            }
                            if (word.audioUrl.isNotBlank()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clickable { onPlayAudio(word.audioUrl) }
                                        .background(accentColor.copy(alpha = 0.1f), CircleShape)
                                        .padding(6.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text("🔊", fontSize = 14.sp) }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.graphicsLayer { rotationY = 180f }.fillMaxSize(),
                        contentAlignment = Alignment.TopStart // 🌟 关键修复 1：固定顶级对齐为 TopStart
                    ) {
                        // 🌟 关键修复 2：将动画时长缩短到 180ms，实现干脆利落的物理长按反馈
                        Crossfade(
                            targetState = isPeeking,
                            animationSpec = tween(durationMillis = 180),
                            label = "flashcard_peek"
                        ) { peeking ->
                            if (peeking) {
                                // 🌟 优化后的中文展示：完全匹配英文布局坐标，消除位移顿挫
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = "💡 中文辅助释义",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Serif,
                                        color = accentColor,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = word.chineseMeaning,
                                        fontSize = 17.sp,
                                        fontFamily = FontFamily.Serif,
                                        color = deepText,
                                        lineHeight = 26.sp,
                                        textAlign = TextAlign.Start
                                    )
                                }
                            } else {
                                val defs = word.englishMeaning.split("\n").filter { it.isNotBlank() }
                                val parsedData = parseSentence(word.sentence)

                                Column(
                                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    defs.forEachIndexed { index, def ->
                                        Text(
                                            text = "• $def",
                                            fontSize = 16.sp,
                                            fontFamily = FontFamily.Serif,
                                            color = deepText,
                                            lineHeight = 24.sp,
                                            textAlign = TextAlign.Start
                                        )
                                        if (index < defs.size - 1) Spacer(modifier = Modifier.height(8.dp))
                                    }

                                    if (parsedData.first.isNotBlank() || parsedData.second.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        HorizontalDivider(color = deepText.copy(alpha = 0.05f), thickness = 1.dp)
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }

                                    if (parsedData.first.isNotBlank()) {
                                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                            Box(modifier = Modifier.width(3.dp).height(IntrinsicSize.Min).background(accentColor, RoundedCornerShape(1.dp)))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "e.g. ${parsedData.first}",
                                                fontSize = 15.sp,
                                                fontFamily = FontFamily.Serif,
                                                fontStyle = FontStyle.Italic,
                                                color = deepText.copy(alpha = 0.7f),
                                                lineHeight = 22.sp
                                            )
                                        }
                                    }

                                    if (parsedData.second.isNotBlank()) {
                                        Text(
                                            text = "📝 Note: ${parsedData.second}",
                                            fontSize = 15.sp,
                                            fontFamily = FontFamily.Serif,
                                            color = deepText.copy(alpha = 0.6f),
                                            lineHeight = 22.sp,
                                            textAlign = TextAlign.Start
                                        )
                                    }

                                    if (word.chineseMeaning.isNotBlank()) {
                                        Spacer(modifier = Modifier.weight(1f, fill = false))
                                        Spacer(modifier = Modifier.height(24.dp))
                                        Text(
                                            text = "Hold to peek translation",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Serif,
                                            color = deepText.copy(alpha = 0.3f),
                                            modifier = Modifier.fillMaxWidth(),
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WordCard(word: WordEntity, cardColor: Color, deepText: Color, shadowColor: Color, accentColor: Color, onPlayAudio: (String) -> Unit, onEditClick: (WordEntity) -> Unit) {
    val parsedData = remember(word.sentence) { parseSentence(word.sentence) }
    var isPeeking by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        if (word.chineseMeaning.isNotBlank()) isPeeking = true
                    },
                    onPress = {
                        tryAwaitRelease()
                        isPeeking = false
                    }
                )
            }
    ) {
        Box(modifier = Modifier.matchParentSize().padding(start = 4.dp, top = 4.dp).background(shadowColor.copy(alpha = 0.15f), RoundedCornerShape(16.dp)))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 4.dp, bottom = 4.dp)
                .background(cardColor, RoundedCornerShape(16.dp))
                .border(1.dp, deepText.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = word.word,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = deepText
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Lvl ${word.level}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = deepText.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "✏️",
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clickable { onEditClick(word) }
                            .padding(4.dp)
                            .graphicsLayer { scaleX = -1f }
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (word.note.isNotBlank()) {
                    Text(
                        text = word.note,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Serif,
                        color = deepText.copy(alpha = 0.6f)
                    )
                }
                if (word.audioUrl.isNotBlank()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clickable { onPlayAudio(word.audioUrl) }
                            .background(accentColor.copy(alpha = 0.1f), CircleShape)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("🔊", fontSize = 12.sp) }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Crossfade(
                targetState = isPeeking,
                animationSpec = tween(durationMillis = 180),
                label = "list_card_peek"
            ) { peeking ->
                if (peeking) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        Text(
                            text = "💡 中文辅助释义",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Serif,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = word.chineseMeaning,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Serif,
                            color = deepText,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Start
                        )
                    }
                } else {
                    Column {
                        val defs = word.englishMeaning.split("\n").filter { it.isNotBlank() }
                        if (defs.isNotEmpty()) {
                            val pagerState = rememberPagerState(pageCount = { defs.size })
                            Column {
                                HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 100.dp)
                                ) { page ->
                                    Text(
                                        text = defs[page],
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Serif,
                                        color = deepText,
                                        lineHeight = 22.sp,
                                        modifier = Modifier.verticalScroll(rememberScrollState())
                                    )
                                }

                                if (defs.size > 1) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        repeat(defs.size) { iteration ->
                                            val color = if (pagerState.currentPage == iteration) deepText.copy(alpha = 0.6f) else deepText.copy(alpha = 0.15f)
                                            Box(
                                                modifier = Modifier.padding(horizontal = 2.dp).size(5.dp).background(color, CircleShape)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (parsedData.first.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.width(2.dp).height(IntrinsicSize.Min).background(accentColor, RoundedCornerShape(1.dp)))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = parsedData.first,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic,
                                    color = deepText.copy(alpha = 0.7f),
                                    lineHeight = 20.sp
                                )
                            }
                        }

                        if (parsedData.second.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "📝 ${parsedData.second}",
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Serif,
                                color = deepText.copy(alpha = 0.5f),
                                lineHeight = 20.sp
                            )
                        }

                        if (word.chineseMeaning.isNotBlank()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Hold card to peek translation",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Serif,
                                color = deepText.copy(alpha = 0.3f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateText(textColor: Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "Your notebook is empty.",
            fontSize = 16.sp,
            fontFamily = FontFamily.Serif,
            color = textColor
        )
    }
}

// =====================================================================
// 🌟 独立化组件区
// =====================================================================

@Composable
private fun PremiumEditTextField(
    value: String,
    onValueChange: (String) -> Unit,
    cardColor: Color,
    deepText: Color,
    singleLine: Boolean,
    minLines: Int = 1,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(cardColor, RoundedCornerShape(12.dp))
            .border(1.dp, deepText.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = TextStyle(
                color = deepText,
                fontSize = 16.sp,
                fontFamily = FontFamily.Serif,
                lineHeight = 24.sp
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun WordListPillButton(text: String, onClick: () -> Unit, isPrimary: Boolean = false, baseColor: Color) {
    val bgColor = if (isPrimary) baseColor else Color.Transparent
    val textColor = if (isPrimary) Color.White else baseColor

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(bgColor, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 18.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = textColor)
    }
}

@Composable
private fun WordListBackButton(onClick: () -> Unit, iconColor: Color) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(Color.White.copy(alpha = 0.8f), CircleShape)
            .border(1.dp, iconColor.copy(alpha = 0.05f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = "Back",
            tint = iconColor,
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
private fun PremiumToggleSwitch(isChecked: Boolean, onCheckedChange: (Boolean) -> Unit, cardColor: Color, accentColor: Color, borderColor: Color) {
    val thumbOffset by animateDpAsState(
        targetValue = if (isChecked) 32.dp else 0.dp,
        animationSpec = tween(durationMillis = 250),
        label = "switch_thumb"
    )

    Box(
        modifier = Modifier
            .width(64.dp)
            .height(32.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!isChecked) }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(cardColor, CircleShape)
                .border(1.dp, borderColor, CircleShape),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(modifier = Modifier.size(12.dp, 1.dp).background(Color.Gray.copy(alpha = 0.5f), CircleShape))
                        Box(modifier = Modifier.size(12.dp, 1.dp).background(Color.Gray.copy(alpha = 0.5f), CircleShape))
                        Box(modifier = Modifier.size(12.dp, 1.dp).background(Color.Gray.copy(alpha = 0.5f), CircleShape))
                    }
                }
                Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                    Box(modifier = Modifier.size(10.dp, 14.dp).border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(2.dp)))
                }
            }

            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .padding(2.dp)
                    .size(28.dp)
                    .background(accentColor, CircleShape)
            )
        }
    }
}