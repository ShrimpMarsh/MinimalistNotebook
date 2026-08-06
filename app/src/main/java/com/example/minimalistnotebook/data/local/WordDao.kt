package com.example.minimalistnotebook.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// @Dao (Data Access Object) 告诉系统这是用来操作数据的控制器
@Dao
interface WordDao {

    // 1. 录入单词（如果 id 重复，就替换掉旧的）
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWord(word: WordEntity)

    // 2. 更新单词（比如背对了，Level +1）
    @Update
    suspend fun updateWord(word: WordEntity)

    // 3. 彻底删除单词
    @Delete
    suspend fun deleteWord(word: WordEntity)

    // 4. 获取所有单词，按插入顺序倒序排列（用于你的"单词本页面"）
    // 返回 Flow 代表这是一个数据流，数据库一有变化，UI 自动无缝刷新！
    @Query("SELECT * FROM words ORDER BY id DESC")
    fun getAllWords(): Flow<List<WordEntity>>

    // 5. 【核心算法前置】筛选出需要复习的单词！
    // 找出 Level < 9 且下次复习时间 <= 当前时间的单词
    @Query("SELECT * FROM words WHERE level < 9 AND nextReviewTime <= :currentTimeMillis")
    fun getWordsToReview(currentTimeMillis: Long): Flow<List<WordEntity>>

    // 🌟 新增：用于后台闹钟/推送引擎的一次性静态查询，修复 Receiver 报错
    @Query("SELECT * FROM words")
    suspend fun getAllWordsSync(): List<WordEntity>
}