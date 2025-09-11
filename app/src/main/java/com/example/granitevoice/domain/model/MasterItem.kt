package com.example.granitevoice.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "master_items")
data class MasterItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val code: String,
    val description: String
)