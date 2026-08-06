package com.example.minimalistnotebook.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// 🌟 @Entity 告诉 Room 这是一张数据库的表，表名叫 words
@Entity(tableName = "words")
data class WordEntity(
    // 🌟 @PrimaryKey 告诉 Room 这是唯一的主键（ID）
    @PrimaryKey
    val id: String,
    val word: String,
    val englishMeaning: String,
    val chineseMeaning: String,
    val sentence: String,
    val note: String,
    val audioUrl: String,
    val level: Int,
    val nextReviewTime: Long
)

//无用注释