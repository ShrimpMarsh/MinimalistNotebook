package com.example.minimalistnotebook.data.remote

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NetworkClient {

    // Retrofit 必须要求一个基础 URL，但我们在 Api 接口里写了全拼的 URL，
    // 所以这里随便填一个合法的 URL 占位即可。
    private const val BASE_URL = "https://api.dictionaryapi.dev/"

    // lazy 表示“懒加载”，只有当 App 第一次真的需要网络请求时，才会去消耗内存创建这个快递员。
    val api: DictionaryApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create()) // 绑定 Gson 翻译官
            .build()
            .create(DictionaryApi::class.java)
    }
}