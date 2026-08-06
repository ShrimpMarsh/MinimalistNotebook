package com.example.minimalistnotebook

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.minimalistnotebook.data.local.AppDatabase
import com.example.minimalistnotebook.service.MemoryGuardianService
import com.example.minimalistnotebook.ui.WordViewModel
import com.example.minimalistnotebook.ui.WordViewModelFactory
import com.example.minimalistnotebook.ui.screens.AddWordScreen
import com.example.minimalistnotebook.ui.screens.DashboardScreen
import com.example.minimalistnotebook.ui.screens.list.WordListScreen
import com.example.minimalistnotebook.ui.theme.MinimalistNotebookTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            val database = AppDatabase.getDatabase(this)
            val viewModel: WordViewModel by viewModels {
                WordViewModelFactory(database.wordDao())
            }

            setContent {
                MinimalistNotebookTheme {
                    val context = LocalContext.current
                    var currentScreen by remember { mutableStateOf("dashboard") }

                    val wordList by viewModel.allWords.collectAsState()
                    val searchState by viewModel.searchState.collectAsState()

                    // ==========================================
                    // 🌟 记忆胶囊 (灵动岛) 唤醒协议
                    // ==========================================
                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                        onResult = { isGranted ->
                            if (isGranted) {
                                MemoryGuardianService.start(context)
                            } else {
                                Toast.makeText(context, "开启通知权限，体验常驻记忆胶囊功能", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )

                    // 进首页直接静默请求权限/挂载灵动岛
                    LaunchedEffect(Unit) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            MemoryGuardianService.start(context)
                        }
                    }

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
                                onExit = { currentScreen = "dashboard" }
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
                                onUpdateWord = { updatedWord -> viewModel.updateWord(updatedWord) },
                                onDeleteWord = { wordToDelete -> viewModel.deleteWord(wordToDelete) }
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Startup Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }
}