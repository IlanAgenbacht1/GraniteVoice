package com.example.granitevoice.data.repository

import com.example.granitevoice.data.remote.StockDetailGraniteSource
import com.example.granitevoice.domain.model.ItemStockSummary
import com.example.granitevoice.domain.model.MasterItem
import com.example.granitevoice.domain.repository.StockRepository
import javax.inject.Inject

class StockRepositoryImpl @Inject constructor(
    private val stockDetailSource: StockDetailGraniteSource
) : StockRepository {

    override suspend fun getItemStockSummary(item: MasterItem): Result<ItemStockSummary> {
        return stockDetailSource.getItemStockSummary(item)
    }
}