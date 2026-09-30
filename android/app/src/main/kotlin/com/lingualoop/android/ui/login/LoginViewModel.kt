package com.lingualoop.android.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lingualoop.android.data.auth.AuthRepository
import com.lingualoop.android.data.auth.TokenStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { LOGIN, REGISTER }

data class LoginUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val submitting: Boolean = false,
    val error: String? = null,
    val authenticated: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    val tokenStore: TokenStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onModeChange(mode: AuthMode) = _state.update { it.copy(mode = mode, error = null) }
    fun onEmailChange(email: String) = _state.update { it.copy(email = email) }
    fun onPasswordChange(password: String) = _state.update { it.copy(password = password) }
    fun onDisplayNameChange(displayName: String) = _state.update { it.copy(displayName = displayName) }

    fun submit() {
        val current = _state.value
        if (current.submitting) return
        val email = current.email.trim()
        if (email.isEmpty() || current.password.length < 8) {
            _state.update { it.copy(error = "Enter your email and a password of at least 8 characters") }
            return
        }
        _state.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            try {
                when (current.mode) {
                    AuthMode.LOGIN -> authRepository.login(email, current.password)
                    AuthMode.REGISTER -> authRepository.register(
                        email = email,
                        password = current.password,
                        displayName = current.displayName.ifBlank { email },
                    )
                }
                _state.update { it.copy(submitting = false, authenticated = true) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(submitting = false, error = error.message ?: "Authentication failed")
                }
            }
        }
    }
}
