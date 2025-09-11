package com.example.granitevoice.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "master_items",
    indices = [
        Index(value = ["description"], unique = false), // For fuzzy search performance
        Index(value = ["code"], unique = true) // Ensure code uniqueness
    ]
)
data class MasterItem(
    @PrimaryKey
    val code: String, // Use code as primary key to prevent duplicates
    val description: String,
    val remoteId: Int = 0, // Keep the remote ID for reference but don't use as PK
    val unitOfMeasure: String? = null, // Primary UOM for this item
    val category: String? = null,
    val itemType: String? = null
)