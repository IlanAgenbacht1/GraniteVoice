package com.example.granitevoice.domain.repository

import com.example.granitevoice.domain.model.ItemStockSummary
import com.example.granitevoice.domain.model.MasterItem

interface StockRepository {
    suspend fun getItemStockSummary(item: MasterItem): Result<ItemStockSummary>
}