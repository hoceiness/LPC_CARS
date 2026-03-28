package com.example.lpc_origin_app

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AuthViewModel : ViewModel() {
    private val repository = AuthRepository()

    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    fun register(user: User, password: String) {
        _authState.value = AuthState.Loading
        repository.sendEmailOtp(user, password) { success, error ->
            if (success) {
                _authState.postValue( AuthState.VerificationSent)
            } else {
                _authState.postValue(AuthState.Error(error ?: "Unknown error"))
            }
        }
    }

    fun login(email: String, password: String) {
        _authState.value = AuthState.Loading
        repository.loginUser(email, password) { success, error ->
            if (success) {
                checkRole()
            } else {
                _authState.value = AuthState.Error(error ?: "Unknown error")
            }
        }
    }
    
    fun verifyOtp(code: String) {
        _authState.value = AuthState.Loading
        repository.verifyOtpAndRegister(code) { success, error ->
            if (success) {
                checkRole()
            } else {
                _authState.postValue(AuthState.Error(error ?: "Unknown error"))
            }
        }
    }

    private fun checkRole() {
        val uid = repository.getCurrentUser()?.uid ?: return
        repository.getUserRole(uid) { role ->
            if (role != null) {
                _authState.value = AuthState.Authenticated(role)
            } else {
                _authState.value = AuthState.Error("Failed to fetch user role")
            }
        }
    }
}

sealed class AuthState {
    object Loading : AuthState()
    object VerificationSent : AuthState()
    data class Authenticated(val role: String) : AuthState()
    object Success : AuthState()
    data class Error(val message: String) : AuthState()
}
