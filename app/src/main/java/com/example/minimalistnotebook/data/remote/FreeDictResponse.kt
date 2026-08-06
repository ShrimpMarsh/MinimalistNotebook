package com.example.minimalistnotebook.data.remote

import com.google.gson.annotations.SerializedName

// 这是 Free Dictionary API 返回的完整单词数据结构
data class FreeDictEntry(
    val word: String,
    val phonetics: List<Phonetic>?,
    val meanings: List<Meaning>?
)

data class Phonetic(
    val text: String?,
    val audio: String? // 读音音频链接
)

data class Meaning(
    val partOfSpeech: String?, // 词性，比如 noun, verb
    val definitions: List<Definition>?
)

data class Definition(
    val definition: String?, // 具体释义
    val example: String?     // 例句（主引擎的例句在这里）
)