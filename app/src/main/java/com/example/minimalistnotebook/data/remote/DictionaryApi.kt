package com.example.minimalistnotebook.data.remote

import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface DictionaryApi {

    // 1. 主引擎：Free Dictionary API
    // {word} 是个占位符，查询时会被替换成用户输入的单词
    @GET("https://api.dictionaryapi.dev/api/v2/entries/en/{word}")
    suspend fun getFreeDictionaryWord(
        @Path("word") word: String
    ): List<FreeDictEntry>

    // 2. 兜底引擎：有道 API
    // 有道的 JSON 结构非常庞大且不规则，为了不把你绕晕，我们直接用 JsonObject 接收，
    // 之后在 ViewModel 的逻辑里像剥洋葱一样手动提取我们需要的例句即可。
    @GET("https://dict.youdao.com/jsonapi")
    suspend fun getYoudaoWord(
        @Query("q") word: String
    ): JsonObject
}