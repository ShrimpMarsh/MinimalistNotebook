package com.example.minimalistnotebook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.minimalistnotebook.data.local.WordDao
import com.example.minimalistnotebook.data.local.WordEntity
import com.example.minimalistnotebook.data.repository.WordRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.random.Random

sealed class SearchState {
    object Idle : SearchState()
    object Loading : SearchState()
    data class Success(
        val word: String,
        val phonetic: String,
        val definitions: List<String>,
        val chineseHint: String,
        val sentence: String,
        val audioUrl: String
    ) : SearchState()
    data class Error(val message: String) : SearchState()
}

enum class QuestionType {
    CHOICE,
    SPELLING,
    FILL_BLANK
}

data class ReviewUiState(
    val isFinished: Boolean = false,
    val currentWord: WordEntity? = null,
    val questionType: QuestionType = QuestionType.CHOICE,
    val choiceOptions: List<String> = emptyList(),
    val isRevealed: Boolean = false,
    val isShake: Boolean = false
)

// 🌟 核心引擎数据结构：复习任务快照
data class ReviewTask(
    val word: WordEntity,
    val type: QuestionType,
    val options: List<String>,
    var isFailed: Boolean = false // 记录本局是否曾经答错过
)

class WordViewModel(private val wordDao: WordDao) : ViewModel() {

    private val repository = WordRepository()

    private val _searchState = MutableStateFlow<SearchState>(SearchState.Idle)
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    val allWords: StateFlow<List<WordEntity>> = wordDao.getAllWords()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // 🌟 记忆间隔天数：Lv 9 对应索引 9 的 180 天长尾巡航
    private val intervals = doubleArrayOf(0.0, 0.5, 1.0, 2.0, 4.0, 7.0, 15.0, 30.0, 60.0, 180.0)

    private val fallbackDistractors = listOf(
        "(noun) A piece of furniture with a flat top and one or more legs.",
        "(verb) To move through the water by moving your arms and legs.",
        "(adjective) Having a great distance from top to bottom.",
        "(noun) A small domesticated carnivorous mammal with soft fur.",
        "(verb) To rest your mind and body by closing your eyes.",
        "(adjective) Producing or reflecting a lot of light.",
        "(noun) A vehicle with four wheels powered by an internal engine.",
        "(verb) To speak softly and quietly.",
        "(noun) The star around which the earth orbits.",
        "(adjective) Expressing or feeling deep sorrow or unhappiness."
    )

    private val _reviewUiState = MutableStateFlow(ReviewUiState())
    val reviewUiState: StateFlow<ReviewUiState> = _reviewUiState.asStateFlow()

    private val reviewQueue = ArrayDeque<ReviewTask>()
    private val historyStack = ArrayDeque<ReviewUiState>()
    private val futureStack = ArrayDeque<ReviewUiState>()
    private val previousAnswerIndex = mutableMapOf<String, Int>()

    fun initReviewQueue(allWords: List<WordEntity>) {
        val now = System.currentTimeMillis()

        // 🌟 核心修改 1：移除对 Lv 9 的过滤，允许半年后的词汇回归参与大考
        val dueWords = allWords.filter { it.nextReviewTime <= now }.shuffled()

        reviewQueue.clear()
        historyStack.clear()
        futureStack.clear()
        previousAnswerIndex.clear()

        val tasks = dueWords.map { word ->
            val type = determineQuestionType(word.level)
            val options = if (type == QuestionType.CHOICE) generateOptions(word, allWords) else emptyList()
            ReviewTask(word, type, options)
        }
        reviewQueue.addAll(tasks)

        nextQuestion()
    }

    private fun generateOptions(word: WordEntity, allWordsList: List<WordEntity>): List<String> {
        val correctSlice = word.englishMeaning.split("\n").filter { it.isNotBlank() }.randomOrNull()?.trim() ?: word.englishMeaning.trim()

        var distractors = allWordsList
            .filter { it.word != word.word && it.englishMeaning.isNotBlank() }
            .shuffled()
            .take(3)
            .map { w -> w.englishMeaning.split("\n").filter { it.isNotBlank() }.randomOrNull()?.trim() ?: w.englishMeaning.trim() }

        if (distractors.size < 3) {
            val needed = 3 - distractors.size
            val extraDistractors = fallbackDistractors
                .filter { it != correctSlice && !distractors.contains(it) }
                .shuffled()
                .take(needed)
            distractors = distractors + extraDistractors
        }
        return (distractors + correctSlice).shuffled()
    }

    // 🌟 核心修改 2：高层算法级调度
    private fun determineQuestionType(level: Int): QuestionType {
        return when {
            level <= 2 -> QuestionType.CHOICE
            level <= 4 -> QuestionType.SPELLING
            level in 5..8 -> {
                // 巩固期：只考硬核输出
                if (Random.nextInt(100) < 70) QuestionType.FILL_BLANK else QuestionType.SPELLING
            }
            else -> {
                // Lv 9 宗师级：180天大考，返璞归真只考认读唤醒
                QuestionType.CHOICE
            }
        }
    }

    // 🌟 核心修改 3：大考失败的软着陆
    private fun calculateDropLevel(currentLevel: Int): Int {
        return when (currentLevel) {
            in 0..3 -> 0             // 新手期跌回0
            in 4..6 -> currentLevel - 2 // 中期降2级
            9 -> 6                   // 半年大考遗忘：不回退过猛，软着陆到 Lv 6 (15天梯队)
            else -> currentLevel - 1    // 高维降1级 (7->6, 8->7)
        }
    }

    private fun nextQuestion() {
        if (futureStack.isNotEmpty()) {
            _reviewUiState.value = futureStack.removeFirst()
            return
        }

        if (reviewQueue.isEmpty()) {
            _reviewUiState.value = ReviewUiState(isFinished = true)
            return
        }

        val currentTask = reviewQueue.first()

        val displayOptions = if (currentTask.type == QuestionType.CHOICE && currentTask.isFailed) {
            currentTask.options.shuffled()
        } else {
            currentTask.options
        }

        _reviewUiState.value = ReviewUiState(
            isFinished = false,
            currentWord = currentTask.word,
            questionType = currentTask.type,
            choiceOptions = displayOptions,
            isRevealed = false,
            isShake = false
        )
    }

    fun submitAnswer(isCorrect: Boolean) {
        val currentState = _reviewUiState.value
        if (reviewQueue.isEmpty()) return
        val currentTask = reviewQueue.first()

        viewModelScope.launch {
            if (isCorrect) {
                if (currentTask.isFailed) {
                    // 答错后的本局重试成功：执行计算降级
                    val newLevel = calculateDropLevel(currentTask.word.level)
                    // 🌟 遗忘抢救法则：哪怕保级在 Lv 6，也无视其 15 天间隔，强制要求明天重新唤醒！
                    val nextTime = System.currentTimeMillis() + (1L * 24 * 60 * 60 * 1000)

                    val updatedWord = currentTask.word.copy(level = newLevel, nextReviewTime = nextTime)
                    wordDao.insertWord(updatedWord)

                    reviewQueue.removeFirst()
                    _reviewUiState.value = currentState.copy(isRevealed = true, currentWord = updatedWord)
                } else {
                    // 一遍完美答对：正常升级与顺延间隔 (Lv 9 答对，依然保持 9，延迟 180 天)
                    val newLevel = minOf(currentTask.word.level + 1, 9)
                    val delayDays = intervals[newLevel]
                    val nextTime = System.currentTimeMillis() + (delayDays * 24 * 60 * 60 * 1000).toLong()

                    val updatedWord = currentTask.word.copy(level = newLevel, nextReviewTime = nextTime)
                    wordDao.insertWord(updatedWord)

                    reviewQueue.removeFirst()
                    _reviewUiState.value = currentState.copy(isRevealed = true, currentWord = updatedWord)
                }
            } else {
                // 答错瞬间：打上失败标记，原封不动扔回队尾，题型锁定！
                currentTask.isFailed = true

                reviewQueue.removeFirst()
                reviewQueue.addLast(currentTask)

                _reviewUiState.value = currentState.copy(isRevealed = true, isShake = true)
            }
        }
    }

    fun swipeNext() {
        val currentState = _reviewUiState.value
        if (!currentState.isRevealed && !currentState.isFinished) return

        if (!currentState.isFinished) {
            historyStack.addLast(currentState)
        }
        nextQuestion()
    }

    fun swipePrevious() {
        if (historyStack.isEmpty()) return

        val currentState = _reviewUiState.value
        if (!currentState.isFinished) {
            futureStack.addFirst(currentState)
        }

        _reviewUiState.value = historyStack.removeLast()
    }

    // ==================== 数据录入与管理操作区 ====================

    fun searchAndSaveWord(word: String) {
        if (word.isBlank()) return
        _searchState.value = SearchState.Loading

        viewModelScope.launch {
            val result = repository.fetchWordInfo(word)
            result.onSuccess { data ->
                _searchState.value = SearchState.Success(
                    word = data.word,
                    phonetic = data.phonetic,
                    definitions = data.definitions,
                    chineseHint = data.chineseHint,
                    sentence = data.exampleSentence,
                    audioUrl = data.audioUrl
                )
            }.onFailure { error ->
                _searchState.value = SearchState.Error(error.message ?: "Unknown Error")
            }
        }
    }

    fun saveWordToLocal(word: String, phonetic: String, englishMeaning: String, chineseMeaning: String, sentence: String, audioUrl: String) {
        viewModelScope.launch {
            val newWord = WordEntity(
                id = UUID.randomUUID().toString(),
                word = word,
                englishMeaning = englishMeaning,
                chineseMeaning = chineseMeaning,
                sentence = sentence,
                note = phonetic,
                audioUrl = audioUrl,
                level = 0,
                nextReviewTime = 0L
            )
            wordDao.insertWord(newWord)
        }
    }

    fun batchSaveWords(words: List<String>) {
        viewModelScope.launch {
            words.forEach { wordText ->
                val newWord = WordEntity(
                    id = UUID.randomUUID().toString(),
                    word = wordText,
                    englishMeaning = "",
                    chineseMeaning = "",
                    sentence = "",
                    note = "",
                    audioUrl = "",
                    level = 0,
                    nextReviewTime = 0L
                )
                wordDao.insertWord(newWord)
            }
        }
    }

    fun resetState() {
        _searchState.value = SearchState.Idle
    }

    fun updateWord(word: WordEntity) {
        viewModelScope.launch {
            wordDao.updateWord(word)
        }
    }

    fun deleteWord(word: WordEntity) {
        viewModelScope.launch {
            wordDao.deleteWord(word)
        }
    }
}

class WordViewModelFactory(private val wordDao: WordDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WordViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WordViewModel(wordDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}