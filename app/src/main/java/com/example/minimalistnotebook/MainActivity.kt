package com.example.minimalistnotebook

import android.Manifest
import android.app.TimePickerDialog
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.minimalistnotebook.data.local.AppDatabase
import com.example.minimalistnotebook.data.local.ReminderDataStore
import com.example.minimalistnotebook.ui.WordViewModel
import com.example.minimalistnotebook.ui.WordViewModelFactory
import com.example.minimalistnotebook.ui.screens.AddWordScreen
import com.example.minimalistnotebook.ui.screens.DashboardScreen
import com.example.minimalistnotebook.ui.screens.list.WordListScreen
import com.example.minimalistnotebook.ui.theme.MinimalistNotebookTheme
import com.example.minimalistnotebook.utils.ReminderManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🌟 初始化系统的通知渠道，用于挂载小红点和悬浮通知
        ReminderManager.createNotificationChannel(this)

        try {
            val database = AppDatabase.getDatabase(this)
            val viewModel: WordViewModel by viewModels {
                WordViewModelFactory(database.wordDao())
            }

            setContent {
                MinimalistNotebookTheme {
                    val context = LocalContext.current
                    val coroutineScope = rememberCoroutineScope()
                    var currentScreen by remember { mutableStateOf("dashboard") }

                    val wordList by viewModel.allWords.collectAsState()
                    val searchState by viewModel.searchState.collectAsState()

                    // 🌟 智能推送引擎层
                    val reminderDataStore = remember { ReminderDataStore(context) }
                    var showReminderDialog by remember { mutableStateOf(false) }

                    // 原生时间选择器
                    val timePickerDialog = TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            coroutineScope.launch {
                                reminderDataStore.saveReminderTime(hourOfDay, minute)
                                ReminderManager.scheduleDailyReminder(context, hourOfDay, minute)
                                Toast.makeText(context, "复习提醒已设为 ${String.format("%02d:%02d", hourOfDay, minute)}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        20, 0, true // 默认晚上 20:00，24小时制
                    )

                    // 权限请求器 (适配 Android 13+)
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                        onResult = { isGranted ->
                            // 无论是否赋予通知权限，都让用户设置时间（底层查库依然运作）
                            timePickerDialog.show()
                        }
                    )

                    when (currentScreen) {
                        "dashboard" -> {
                            DashboardScreen(
                                wordList = wordList,
                                onNavigateToAdd = { currentScreen = "add" },
                                onNavigateToList = { currentScreen = "list" },
                                onNavigateToReview = { currentScreen = "review" }
                            )
                        }

                        "review" -> {
                            BackHandler { currentScreen = "dashboard" }
                            val reviewUiState by viewModel.reviewUiState.collectAsState()

                            LaunchedEffect(Unit) {
                                viewModel.initReviewQueue(wordList)
                            }

                            com.example.minimalistnotebook.ui.screens.ReviewScreen(
                                uiState = reviewUiState,
                                onSubmitAnswer = { isCorrect -> viewModel.submitAnswer(isCorrect) },
                                onSwipeNext = { viewModel.swipeNext() },
                                onSwipePrevious = { viewModel.swipePrevious() },
                                onExit = {
                                    // 🌟 智能弹窗拦截引擎
                                    if (reviewUiState.isFinished) {
                                        coroutineScope.launch {
                                            val hasSet = reminderDataStore.hasSetInitial.first()
                                            if (!hasSet) {
                                                // 初次完成复习！触发多巴胺设定弹窗
                                                showReminderDialog = true
                                            } else {
                                                // 日常复习，静默记录最后复习时间戳
                                                reminderDataStore.updateLastReviewTime(System.currentTimeMillis())
                                            }
                                        }
                                    }
                                    currentScreen = "dashboard"
                                }
                            )
                        }

                        "add" -> {
                            BackHandler { currentScreen = "dashboard" }

                            AddWordScreen(
                                searchState = searchState,
                                onSearch = { word -> viewModel.searchAndSaveWord(word) },
                                onSave = { word, phonetic, english, chinese, sentence, audioUrl ->
                                    viewModel.saveWordToLocal(word, phonetic, english, chinese, sentence, audioUrl)
                                    Toast.makeText(context, "『$word』已成功加入单词本！", Toast.LENGTH_SHORT).show()
                                    viewModel.resetState()
                                    currentScreen = "dashboard"
                                },
                                onBatchSave = { words ->
                                    viewModel.batchSaveWords(words)
                                    Toast.makeText(context, "已成功批量导入 ${words.size} 个单词！", Toast.LENGTH_SHORT).show()
                                    currentScreen = "dashboard"
                                },
                                onBack = { currentScreen = "dashboard" }
                            )
                        }

                        "list" -> {
                            BackHandler { currentScreen = "dashboard" }

                            WordListScreen(
                                wordList = wordList,
                                onBack = { currentScreen = "dashboard" },
                                onUpdateWord = { updatedWord ->
                                    viewModel.updateWord(updatedWord)
                                },
                                onDeleteWord = { wordToDelete ->
                                    viewModel.deleteWord(wordToDelete)
                                }
                            )
                        }
                    }

                    // 🌟 全局悬浮的智能推送弹窗 UI
                    if (showReminderDialog) {
                        AlertDialog(
                            onDismissRequest = { showReminderDialog = false },
                            title = { Text("🎉 恭喜完成首次复习！") },
                            text = { Text("为了建立长效肌肉记忆，设定一个每日专属提醒时间吧，我们一起坚持打卡。") },
                            confirmButton = {
                                TextButton(onClick = {
                                    showReminderDialog = false
                                    // 动态申请权限并呼出时间选择器
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        timePickerDialog.show()
                                    }
                                }) {
                                    Text("设定时间")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    showReminderDialog = false
                                }) {
                                    Text("暂不需要")
                                }
                            }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Startup Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}