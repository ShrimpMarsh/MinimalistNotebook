package com.example.minimalistnotebook.data.repository

import com.example.minimalistnotebook.data.remote.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FetchedWordData(
    val word: String,
    val phonetic: String,
    val definitions: List<String>,
    val chineseHint: String,
    val audioUrl: String = "",
    val exampleSentence: String = ""
)

class WordRepository {
    suspend fun fetchWordInfo(searchWord: String): Result<FetchedWordData> = withContext(Dispatchers.IO) {
        try {
            var youdaoResponse: com.google.gson.JsonObject? = null
            try {
                youdaoResponse = NetworkClient.api.getYoudaoWord(searchWord)
            } catch (e: Exception) { e.printStackTrace() }

            val freeDictResponse = NetworkClient.api.getFreeDictionaryWord(searchWord)

            val rawChineseList = mutableListOf<String>()
            if (youdaoResponse != null) {
                try {
                    if (youdaoResponse.has("ec")) {
                        val ec = youdaoResponse.getAsJsonObject("ec")
                        val wordArr = ec.getAsJsonArray("word")
                        if (wordArr.size() > 0) {
                            val trs = wordArr[0].asJsonObject.getAsJsonArray("trs")
                            trs.forEach { trElement ->
                                val tr = trElement.asJsonObject.getAsJsonArray("tr")[0].asJsonObject.getAsJsonObject("l").getAsJsonArray("i")[0].asString
                                rawChineseList.add(tr)
                            }
                        }
                    } else if (youdaoResponse.has("basic")) {
                        val basic = youdaoResponse.getAsJsonObject("basic")
                        if (basic.has("explains")) {
                            val explains = basic.getAsJsonArray("explains")
                            explains.forEach { rawChineseList.add(it.asString) }
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            val splitChineseList = mutableListOf<String>()
            val posRegex = Regex("^[a-zA-Z]+\\.\\s*")

            rawChineseList.forEach { rawText ->
                val posPrefix = posRegex.find(rawText)?.value ?: ""
                val parts = rawText.split("；", ";")

                parts.forEachIndexed { index, part ->
                    var trimmed = part.trim()
                    if (trimmed.isNotEmpty()) {
                        if (index > 0 && posPrefix.isNotEmpty() && !posRegex.containsMatchIn(trimmed)) {
                            trimmed = "$posPrefix$trimmed"
                        }
                        splitChineseList.add(trimmed)
                    }
                }
            }

            // 🌟 修复：限制最多只取前 4 条中文释义，防止喧宾夺主
            val formattedChinese = if (splitChineseList.isNotEmpty()) {
                splitChineseList.distinct().take(4).mapIndexed { index, meaning ->
                    "${index + 1}. $meaning"
                }.joinToString("\n")
            } else {
                ""
            }

            val allDefinitions = mutableListOf<String>()
            var audioUrl = ""
            var phonetic = ""
            var firstExample = ""

            if (freeDictResponse.isNotEmpty()) {
                val entry = freeDictResponse[0]
                phonetic = entry.phonetics?.firstOrNull { it.text?.isNotEmpty() == true }?.text ?: ""

                val rawAudio = entry.phonetics?.firstOrNull { it.audio?.isNotEmpty() == true }?.audio ?: ""
                audioUrl = when {
                    rawAudio.startsWith("//") -> "https:$rawAudio"
                    rawAudio.startsWith("http://") -> rawAudio.replace("http://", "https://")
                    else -> rawAudio
                }

                entry.meanings?.forEach { meaning ->
                    val pos = meaning.partOfSpeech ?: "unknown"
                    meaning.definitions?.take(2)?.forEach { def ->
                        if (!def.definition.isNullOrBlank()) {
                            allDefinitions.add("($pos) ${def.definition}")
                        }
                        if (firstExample.isEmpty() && !def.example.isNullOrBlank()) {
                            firstExample = def.example!!
                        }
                    }
                }
            }

            if (firstExample.isEmpty() && youdaoResponse != null) {
                try {
                    val rawJsonStr = youdaoResponse.toString()
                    val regex = Regex("\"eng\"\\s*:\\s*\"([^\"]*?)\"")
                    val matches = regex.findAll(rawJsonStr)

                    for (match in matches) {
                        val cleanSentence = match.groupValues[1].replace(Regex("<[^>]*>"), "")
                        if (cleanSentence.contains(searchWord, ignoreCase = true)) {
                            firstExample = cleanSentence
                            break
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }

            if (allDefinitions.isEmpty()) allDefinitions.add("No English definition found.")

            Result.success(
                FetchedWordData(
                    word = searchWord,
                    phonetic = phonetic,
                    definitions = allDefinitions,
                    chineseHint = formattedChinese,
                    audioUrl = audioUrl,
                    exampleSentence = firstExample
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Network error occurred"))
        }
    }
}