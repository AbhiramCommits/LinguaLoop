package com.lingualoop.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingualoop.android.data.api.dto.QueueResponse
import com.lingualoop.android.data.api.dto.StatsResponse
import com.lingualoop.android.data.learner.LearnerRepository
import com.lingualoop.android.data.session.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val queue: QueueResponse? = null,
    val stats: StatsResponse? = null,
    val pendingSync: Int = 0,
    val offline: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val learnerRepository: LearnerRepository,
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val queue = learnerRepository.queue()
                val stats = learnerRepository.stats()
                val pending = sessionRepository.pendingCount()
                _state.update {
                    it.copy(loading = false, queue = queue, stats = stats, pendingSync = pending, offline = false)
                }
            } catch (error: IOException) {
                _state.update { it.copy(loading = false, offline = true) }
            } catch (error: Exception) {
                _state.update { it.copy(loading = false, error = error.message ?: "Could not load your data") }
            }
        }
    }
}
