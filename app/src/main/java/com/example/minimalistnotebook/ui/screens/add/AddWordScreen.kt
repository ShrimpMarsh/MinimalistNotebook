package com.example.minimalistnotebook.ui.screens

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.minimalistnotebook.ui.SearchState
import com.example.minimalistnotebook.ui.components.BatchTextSheet
import com.example.minimalistnotebook.utils.createImageUri
import com.example.minimalistnotebook.utils.processOcrImage
import java.util.UUID
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class EditableDef(val id: String, val isSelected: Boolean, val english: String)

private fun extractPOS(def: String): String {
    val lower = def.lowercase().trim()
    return when {
        lower.startsWith("n.") || lower.contains("(noun)") || lower.startsWith("noun") -> "noun"
        lower.startsWith("v.") || lower.contains("(verb)") || lower.startsWith("verb") -> "verb"
        lower.startsWith("adj.") || lower.contains("(adjective)") || lower.startsWith("adjective") -> "adj"
        lower.startsWith("adv.") || lower.contains("(adverb)") || lower.startsWith("adverb") -> "adv"
        lower.startsWith("prep.") || lower.contains("preposition") -> "prep"
        lower.startsWith("pron.") || lower.contains("pronoun") -> "pron"
        lower.startsWith("conj.") || lower.contains("conjunction") -> "conj"
        else -> "other"
    }
}

private fun cleanChineseHint(raw: String): String {
    if (raw.isBlank()) return ""

    var cleaned = raw.replace(Regex("\\b\\d+[\n\\.,、)]\\s*"), "")
        .replace(Regex("\\([\\d一二三四五六七八九十]+\\)\\s*"), "")
        .replace("\r", "")
        .trim()

    val posRegex = Regex("\\s+(?=[nvdajrvi]+\\.)")
    cleaned = cleaned.replace(posRegex, "\n• ")

    if (cleaned.isNotBlank() && !cleaned.startsWith("•")) {
        cleaned = "• $cleaned"
    }

    cleaned = cleaned.replace(Regex("\n{2,}"), "\n").trim()
    return cleaned
}

@Composable
fun AddWordScreen(
    searchState: SearchState,
    onSearch: (String) -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
    onBatchSave: (List<String>) -> Unit = {},
    onBack: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val mediaPlayer = remember { MediaPlayer() }
    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer.release()
        }
    }

    var showBatchSheet by remember { mutableStateOf(false) }
    var showOcrMenu by remember { mutableStateOf(false) }
    var batchInitialText by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var showMoreDefs by remember { mutableStateOf(false) }

    val premiumGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFF6ED), Color(0xFFEBE0D3))
    )
    val tornCardColor = Color(0xFFFDFBF7)
    val deepBurgundy = Color(0xFF3C120A)
    val warmOrange = Color(0xFFD65A31)
    val deepText = Color(0xFF2C1E16)

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            Toast.makeText(context, "Scanning text...", Toast.LENGTH_SHORT).show()
            processOcrImage(context, uri) { recognizedText ->
                batchInitialText = recognizedText
                showBatchSheet = true
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && photoUri != null) {
            Toast.makeText(context, "Scanning text...", Toast.LENGTH_SHORT).show()
            processOcrImage(context, photoUri!!) { recognizedText ->
                batchInitialText = recognizedText
                showBatchSheet = true
            }
        }
    }

    val editableDefs = remember { mutableStateListOf<EditableDef>() }
    var editableChineseHint by remember { mutableStateOf("") }
    var editableExample by remember { mutableStateOf("") }
    var userNote by remember { mutableStateOf("") }
    var showEditExampleDialog by remember { mutableStateOf(false) }
    var showNoteInput by remember { mutableStateOf(false) }
    var isExampleMissing by remember { mutableStateOf(false) }

    LaunchedEffect(searchState) {
        if (searchState is SearchState.Success) {
            editableDefs.clear()
            showMoreDefs = false

            val seenPOS = mutableSetOf<String>()
            editableDefs.addAll(searchState.definitions.map { eng ->
                val pos = extractPOS(eng)
                val shouldSelect = if (pos != "other") {
                    if (pos !in seenPOS) {
                        seenPOS.add(pos)
                        true
                    } else false
                } else {
                    seenPOS.isEmpty().also { if (it) seenPOS.add("other") }
                }
                EditableDef(id = UUID.randomUUID().toString(), isSelected = shouldSelect, english = eng)
            })

            editableChineseHint = cleanChineseHint(searchState.chineseHint)
            editableExample = searchState.sentence
            userNote = ""
            showNoteInput = false
            isExampleMissing = searchState.sentence.isBlank()
        } else {
            editableDefs.clear()
            editableChineseHint = ""
            editableExample = ""
            userNote = ""
            showNoteInput = false
            isExampleMissing = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(premiumGradient)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AddWordBackButton(onClick = onBack, iconColor = deepText)
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("New Word", fontSize = 36.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = deepText)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Type an English word to search", fontSize = 14.sp, fontFamily = FontFamily.Serif, color = deepText.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(40.dp))

            AddWordSearchField(
                value = inputText,
                onValueChange = { inputText = it },
                hint = "e.g. serendipity",
                cardColor = tornCardColor,
                accentColor = warmOrange,
                textColor = deepText,
                onSearch = { if (inputText.isNotBlank() && searchState !is SearchState.Loading) onSearch(inputText.trim()) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            AddWordPillButton(
                text = "Save Word",
                onClick = {
                    if (searchState is SearchState.Success) {
                        val selectedEnglish = editableDefs.filter { it.isSelected && it.english.isNotBlank() }.joinToString("\n") { it.english }
                        // 🌟 保存时：强制使用有道接口的音频链接
                        val encodedWord = URLEncoder.encode(searchState.word, "UTF-8")
                        val youdaoAudioUrl = "https://dict.youdao.com/dictvoice?audio=$encodedWord&type=1"
                        onSave(searchState.word, searchState.phonetic, selectedEnglish, editableChineseHint, "$editableExample|||$userNote", youdaoAudioUrl)
                    }
                },
                isPrimary = true,
                baseColor = deepBurgundy
            )

            Box(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), contentAlignment = Alignment.TopCenter) {
                when (searchState) {
                    is SearchState.Idle -> { }
                    is SearchState.Loading -> Text("Searching dictionary...", color = deepText.copy(alpha = 0.6f), fontFamily = FontFamily.Serif)
                    is SearchState.Error -> Text("Error: ${searchState.message}", color = Color(0xFFC62828), fontFamily = FontFamily.Serif)
                    is SearchState.Success -> {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Box(modifier = Modifier.matchParentSize().padding(start = 6.dp, top = 6.dp).background(deepBurgundy.copy(alpha = 0.15f), RoundedCornerShape(24.dp)))

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(end = 6.dp, bottom = 6.dp)
                                    .background(tornCardColor, RoundedCornerShape(24.dp))
                                    .border(1.dp, deepText.copy(alpha = 0.1f), RoundedCornerShape(24.dp))
                                    .padding(24.dp)
                            ) {
                                Text(text = searchState.word, fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = deepText)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (searchState.phonetic.isNotBlank()) Text(text = searchState.phonetic, fontSize = 16.sp, fontFamily = FontFamily.Serif, color = deepText.copy(alpha = 0.6f))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier.clickable {
                                            coroutineScope.launch(Dispatchers.IO) {
                                                try {
                                                    // 🌟 核心：强制重定向到极其稳定的有道词典 API
                                                    val encodedWord = URLEncoder.encode(searchState.word, "UTF-8")
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

                                                    val file = File(context.cacheDir, "temp_dict_audio.mp3")
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
                                        }.background(warmOrange.copy(alpha = 0.1f), CircleShape).padding(6.dp),
                                        contentAlignment = Alignment.Center
                                    ) { Text(text = "🔊", fontSize = 14.sp) }
                                }
                                Spacer(modifier = Modifier.height(24.dp))

                                val displayThreshold = 4
                                val shouldFold = editableDefs.size > 5

                                editableDefs.forEachIndexed { index, def ->
                                    val isBeyondThreshold = shouldFold && index >= displayThreshold
                                    if (!isBeyondThreshold || showMoreDefs) {
                                        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
                                            Box(modifier = Modifier.padding(top = 2.dp).size(20.dp).background(if (def.isSelected) warmOrange else tornCardColor, RoundedCornerShape(6.dp)).border(1.5.dp, if (def.isSelected) Color.Transparent else deepText.copy(alpha = 0.3f), RoundedCornerShape(6.dp)).clickable { editableDefs[index] = def.copy(isSelected = !def.isSelected) }, contentAlignment = Alignment.Center) {
                                                if (def.isSelected) Text("✔", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            BasicTextField(value = def.english, onValueChange = { editableDefs[index] = def.copy(english = it) }, textStyle = TextStyle(fontSize = 16.sp, fontFamily = FontFamily.Serif, color = deepText, lineHeight = 22.sp), modifier = Modifier.weight(1f))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = "✖", fontSize = 16.sp, color = deepText.copy(alpha = 0.3f), modifier = Modifier.padding(top = 2.dp).clickable { editableDefs.removeAt(index) }.padding(4.dp))
                                        }
                                    }
                                }

                                if (shouldFold) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showMoreDefs = !showMoreDefs }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (showMoreDefs) "▲ Hide Niche Definitions" else "▼ View More Definitions (${editableDefs.size - displayThreshold} more)",
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Serif,
                                            color = warmOrange,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Box(modifier = Modifier.fillMaxWidth().clickable { editableDefs.add(EditableDef(id = UUID.randomUUID().toString(), isSelected = true, english = "")) }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text("+ Add Custom Definition", fontSize = 14.sp, fontFamily = FontFamily.Serif, color = deepText.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                HorizontalDivider(color = deepText.copy(alpha = 0.05f), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(16.dp))

                                AnimatedVisibility(
                                    visible = isExampleMissing,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                            .background(warmOrange.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("💡", fontSize = 16.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Dictionary didn't provide an example. Add your own context to help memory!",
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Serif,
                                            color = warmOrange,
                                            lineHeight = 18.sp
                                        )
                                    }
                                }

                                if (editableExample.isNotBlank()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp)
                                            .clickable { showEditExampleDialog = true }
                                    ) {
                                        Box(modifier = Modifier.width(3.dp).height(IntrinsicSize.Min).background(warmOrange, RoundedCornerShape(1.dp)))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "e.g. $editableExample",
                                            fontSize = 15.sp,
                                            fontFamily = FontFamily.Serif,
                                            fontStyle = FontStyle.Italic,
                                            color = deepText.copy(alpha = 0.7f),
                                            lineHeight = 22.sp
                                        )
                                    }
                                } else {
                                    Text("Example Sentence", fontSize = 14.sp, fontFamily = FontFamily.Serif, color = deepText.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    BasicTextField(
                                        value = editableExample,
                                        onValueChange = {
                                            editableExample = it
                                            if (it.isNotBlank()) isExampleMissing = false
                                        },
                                        textStyle = TextStyle(fontSize = 15.sp, fontFamily = FontFamily.Serif, color = deepText, lineHeight = 22.sp, fontStyle = FontStyle.Italic),
                                        modifier = Modifier.fillMaxWidth().background(tornCardColor, RoundedCornerShape(12.dp)).border(1.dp, deepText.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(12.dp),
                                        decorationBox = { innerTextField ->
                                            if (editableExample.isEmpty()) Text("Add an example sentence...", color = deepText.copy(alpha = 0.4f), fontSize = 15.sp, fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic)
                                            innerTextField()
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }

                                AnimatedVisibility(
                                    visible = showNoteInput || userNote.isNotEmpty(),
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Text("📝 Personal Note", fontSize = 14.sp, fontFamily = FontFamily.Serif, color = deepText.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        BasicTextField(
                                            value = userNote,
                                            onValueChange = { userNote = it },
                                            textStyle = TextStyle(fontSize = 15.sp, fontFamily = FontFamily.Serif, color = deepText, lineHeight = 22.sp),
                                            modifier = Modifier.fillMaxWidth().background(tornCardColor, RoundedCornerShape(12.dp)).border(1.dp, deepText.copy(alpha = 0.15f), RoundedCornerShape(12.dp)).padding(12.dp),
                                            decorationBox = { innerTextField ->
                                                if (userNote.isEmpty()) Text("Add your personal notes here...", color = deepText.copy(alpha = 0.4f), fontSize = 15.sp, fontFamily = FontFamily.Serif)
                                                innerTextField()
                                            }
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                    }
                                }

                                if (!showNoteInput && userNote.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxWidth().clickable { showNoteInput = true }.padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("+ Add Personal Note", fontSize = 14.sp, fontFamily = FontFamily.Serif, color = deepText.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }

                                HorizontalDivider(color = deepText.copy(alpha = 0.05f), thickness = 1.dp)
                                Spacer(modifier = Modifier.height(16.dp))
                                com.example.minimalistnotebook.ui.components.ScratchOffHint(hintText = editableChineseHint, onHintChange = { editableChineseHint = it })
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            HorizontalDivider(color = deepText.copy(alpha = 0.1f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(24.dp))
            Text("Advanced Entry", fontSize = 12.sp, color = deepText.copy(alpha = 0.5f), fontFamily = FontFamily.Serif)
            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AddWordSmallActionCard(
                    emoji = "📝",
                    title = "Batch Text",
                    cardColor = tornCardColor,
                    textColor = deepText,
                    shadowColor = deepBurgundy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        batchInitialText = ""
                        showBatchSheet = true
                    }
                )
                AddWordSmallActionCard(
                    emoji = "📷",
                    title = "Camera OCR",
                    cardColor = tornCardColor,
                    textColor = deepText,
                    shadowColor = deepBurgundy,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        showOcrMenu = true
                    }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showEditExampleDialog) {
            AlertDialog(
                onDismissRequest = { showEditExampleDialog = false },
                title = { Text("Edit Example", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = deepText) },
                text = {
                    BasicTextField(
                        value = editableExample,
                        onValueChange = { editableExample = it },
                        textStyle = TextStyle(fontSize = 16.sp, fontFamily = FontFamily.Serif, color = deepText, lineHeight = 24.sp),
                        modifier = Modifier.fillMaxWidth().background(tornCardColor, RoundedCornerShape(12.dp)).border(1.dp, deepText.copy(alpha = 0.2f), RoundedCornerShape(12.dp)).padding(12.dp)
                    )
                },
                confirmButton = {
                    Text("Done", modifier = Modifier.clickable { showEditExampleDialog = false }.padding(8.dp), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = warmOrange)
                },
                containerColor = Color(0xFFFFF6ED)
            )
        }

        if (showBatchSheet) {
            BatchTextSheet(
                initialText = batchInitialText,
                onDismiss = { showBatchSheet = false },
                onBatchSave = onBatchSave
            )
        }

        if (showOcrMenu) {
            AddWordOcrMenuSheet(
                onDismiss = { showOcrMenu = false },
                onTakePhoto = {
                    photoUri = createImageUri(context)
                    photoUri?.let { cameraLauncher.launch(it) }
                },
                onChooseGallery = {
                    galleryLauncher.launch("image/*")
                },
                bgColor = Color(0xFFFFF6ED),
                btnColor = deepBurgundy
            )
        }
    }
}

@Composable
private fun AddWordSearchField(value: String, onValueChange: (String) -> Unit, hint: String, cardColor: Color, accentColor: Color, textColor: Color, onSearch: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(cardColor, CircleShape).border(1.5.dp, textColor.copy(alpha = 0.1f), CircleShape).padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) Text(text = hint, color = textColor.copy(alpha = 0.4f), fontSize = 18.sp, fontFamily = FontFamily.Serif)
            BasicTextField(value = value, onValueChange = onValueChange, textStyle = TextStyle(color = textColor, fontSize = 18.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold), singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val offset by animateDpAsState(targetValue = if (isPressed) 2.dp else 0.dp, animationSpec = tween(100), label = "")

        Box(modifier = Modifier.size(48.dp).clickable(interactionSource = interactionSource, indication = null, onClick = onSearch)) {
            Box(modifier = Modifier.padding(start = offset, top = offset, end = 2.dp - offset, bottom = 2.dp - offset).fillMaxSize().background(accentColor, CircleShape), contentAlignment = Alignment.Center) {
                Text(text = "🔍", fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun AddWordPillButton(text: String, onClick: () -> Unit, isPrimary: Boolean = false, enabled: Boolean = true, baseColor: Color) {
    val bgColor = if (!enabled) baseColor.copy(alpha = 0.1f) else if (isPrimary) baseColor else Color.Transparent
    val textColor = if (!enabled) baseColor.copy(alpha = 0.3f) else if (isPrimary) Color.White else baseColor

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(bgColor, CircleShape)
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 18.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = textColor)
    }
}

@Composable
private fun AddWordSmallActionCard(emoji: String, title: String, cardColor: Color, textColor: Color, shadowColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val offset by animateDpAsState(targetValue = if (isPressed) 3.dp else 0.dp, animationSpec = tween(100), label = "")

    Box(modifier = modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)) {
        Box(modifier = Modifier.matchParentSize().padding(start = 3.dp, top = 3.dp).background(shadowColor.copy(alpha = 0.15f), RoundedCornerShape(16.dp)))
        Column(modifier = Modifier.fillMaxWidth().padding(start = offset, top = offset, end = 3.dp - offset, bottom = 3.dp - offset).background(cardColor, RoundedCornerShape(16.dp)).border(1.dp, textColor.copy(alpha = 0.1f), RoundedCornerShape(16.dp)).padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = title, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, color = textColor.copy(alpha = 0.8f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWordOcrMenuSheet(onDismiss: () -> Unit, onTakePhoto: () -> Unit, onChooseGallery: () -> Unit, bgColor: Color, btnColor: Color) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = bgColor, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).padding(bottom = 24.dp)) {
            Text("Select Image Source", fontSize = 20.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF2C1E16))
            Spacer(modifier = Modifier.height(24.dp))
            AddWordPillButton(text = "📷 Take Photo", onClick = { onTakePhoto(); onDismiss() }, isPrimary = true, baseColor = btnColor)
            Spacer(modifier = Modifier.height(16.dp))
            AddWordPillButton(text = "🖼️ Choose from Gallery", onClick = { onChooseGallery(); onDismiss() }, isPrimary = true, baseColor = btnColor)
        }
    }
}

@Composable
private fun AddWordBackButton(onClick: () -> Unit, iconColor: Color) {
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