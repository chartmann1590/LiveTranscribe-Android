package com.charles.livecaptionn.ui.premium

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.charles.livecaptionn.billing.ManageAction
import com.charles.livecaptionn.billing.PremiumProduct
import com.charles.livecaptionn.billing.PremiumRepository
import com.charles.livecaptionn.billing.PremiumState
import com.charles.livecaptionn.billing.PurchaseFlowResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PremiumUiState(
    val premium: PremiumState = PremiumState.EMPTY,
    val supportsEmailRestore: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
    val restoreEmail: String = ""
)

class PremiumViewModel(private val repo: PremiumRepository) : ViewModel() {

    private val _state = MutableStateFlow(PremiumUiState(supportsEmailRestore = repo.supportsEmailRestore))
    val state: StateFlow<PremiumUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.state.collect { premium -> _state.update { it.copy(premium = premium) } }
        }
    }

    fun updateRestoreEmail(email: String) {
        _state.update { it.copy(restoreEmail = email) }
    }

    fun refresh(sessionId: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            repo.refresh(sessionId)
                .onSuccess { _state.update { s -> s.copy(isBusy = false) } }
                .onFailure { e -> _state.update { s -> s.copy(isBusy = false, error = e.message ?: "Refresh failed") } }
        }
    }

    fun purchase(activity: Activity, product: PremiumProduct) {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            when (val result = repo.purchase(activity, product, _state.value.restoreEmail.trim().ifBlank { null })) {
                is PurchaseFlowResult.Started -> _state.update { it.copy(isBusy = false) }
                is PurchaseFlowResult.Failed -> _state.update { it.copy(isBusy = false, error = result.message) }
            }
        }
    }

    fun restore() {
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            repo.restore(_state.value.restoreEmail.trim().ifBlank { null })
                .onSuccess { _state.update { s -> s.copy(isBusy = false) } }
                .onFailure { e -> _state.update { s -> s.copy(isBusy = false, error = e.message ?: "Restore failed") } }
        }
    }

    fun manageSubscription(activity: Activity) {
        viewModelScope.launch {
            when (val result = repo.openManageSubscription(activity)) {
                is ManageAction.Opened -> Unit
                is ManageAction.Failed -> _state.update { it.copy(error = result.message) }
            }
        }
    }

    class Factory(private val repo: PremiumRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PremiumViewModel(repo) as T
        }
    }
}
