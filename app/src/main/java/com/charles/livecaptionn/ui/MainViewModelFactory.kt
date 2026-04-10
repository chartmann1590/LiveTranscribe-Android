package com.charles.livecaptionn.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.charles.livecaptionn.di.AppContainer

class MainViewModelFactory(
    private val container: AppContainer,
    private val application: Application
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MainViewModel(container, application) as T
    }
}
