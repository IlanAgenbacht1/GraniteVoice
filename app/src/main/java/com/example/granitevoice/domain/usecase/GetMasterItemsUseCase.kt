package com.example.granitevoice.domain.usecase

import com.example.granitevoice.domain.model.MasterItem
import com.example.granitevoice.domain.repository.MasterItemRepository
import javax.inject.Inject

class GetMasterItemsUseCase @Inject constructor(
    private val repository: MasterItemRepository
) {
    suspend operator fun invoke(): List<MasterItem> {
        return repository.getMasterItems()
    }
}