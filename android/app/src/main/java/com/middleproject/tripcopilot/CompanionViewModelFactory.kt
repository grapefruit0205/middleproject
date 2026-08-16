package com.middleproject.tripcopilot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.middleproject.tripcopilot.data.DeviceRepository
import com.middleproject.tripcopilot.ui.CompanionViewModel

class CompanionViewModelFactory(
    private val repository: DeviceRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CompanionViewModel::class.java)) {
            return CompanionViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class ${modelClass.name}")
    }
}
