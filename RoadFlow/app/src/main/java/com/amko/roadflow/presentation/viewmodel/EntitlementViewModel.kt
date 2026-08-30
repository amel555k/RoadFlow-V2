package com.amko.roadflow.presentation.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.amko.roadflow.data.local.AppEntitlementState
import com.amko.roadflow.data.local.BillingRepository
import com.amko.roadflow.data.local.GoogleAuthService
import com.amko.roadflow.domain.model.SubscriptionResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EntitlementViewModel(application: Application) : AndroidViewModel(application) {

    private val entitlementRepository = AppEntitlementState.getRepository()
    private val googleAuthService = GoogleAuthService(application)
    private val billingRepository = BillingRepository(application)

    val isPremium: StateFlow<Boolean> = entitlementRepository.isPremium
    val isSubscriptionRequired: StateFlow<Boolean> = entitlementRepository.isSubscriptionRequired
    val price: StateFlow<String> = entitlementRepository.price
    val isInitialized: StateFlow<Boolean> = entitlementRepository.isInitialized
    val checkState: StateFlow<com.amko.roadflow.data.local.EntitlementCheckState> = entitlementRepository.checkState

    private val _isSigningIn = MutableStateFlow(false)
    val isSigningIn: StateFlow<Boolean> = _isSigningIn

    private val _signInError = MutableStateFlow<String?>(null)
    val signInError: StateFlow<String?> = _signInError

    private val _loggedInEmail = MutableStateFlow(entitlementRepository.getSavedEmail())
    val loggedInEmail: StateFlow<String?> = _loggedInEmail

    private val _showConfirmPayment = MutableStateFlow(false)
    val showConfirmPayment: StateFlow<Boolean> = _showConfirmPayment

    fun signIn() {
        viewModelScope.launch {
            _isSigningIn.value = true
            _signInError.value = null

            val result = googleAuthService.signInAsync()

            result.onSuccess { email ->
                entitlementRepository.registerLogin(email)
                _loggedInEmail.value = email

                val alreadySubscribed = entitlementRepository.checkIsAlreadySubscribedAsync(email)
                if (!alreadySubscribed) {
                    _showConfirmPayment.value = true
                }
            }.onFailure { error ->
                _signInError.value = error.message
            }

            _isSigningIn.value = false
        }
    }

    private val _isPurchasing = MutableStateFlow(false)
    val isPurchasing: StateFlow<Boolean> = _isPurchasing

    private val _purchaseError = MutableStateFlow<String?>(null)
    val purchaseError: StateFlow<String?> = _purchaseError

    fun confirmPayment(activity: Activity) {
        val email = loggedInEmail.value ?: return
        viewModelScope.launch {
            _isPurchasing.value = true
            _purchaseError.value = null

            val result = billingRepository.launchPurchaseFlowAsync(activity)

            when (result) {
                is SubscriptionResult.Success, is SubscriptionResult.AlreadyOwned -> {
                    entitlementRepository.confirmPaymentAsync(email)
                    _showConfirmPayment.value = false
                }
                is SubscriptionResult.UserCancelled -> {
                }
                is SubscriptionResult.ProductNotAvailable -> {
                    _purchaseError.value = "Pretplata trenutno nije dostupna"
                }
                is SubscriptionResult.Error -> {
                    _purchaseError.value = result.message
                }
            }

            _isPurchasing.value = false
        }
    }

    fun cancelConfirmPayment() {
        _showConfirmPayment.value = false
    }
    fun signOut() {
        googleAuthService.signOut()
        entitlementRepository.clearEmail()
        _loggedInEmail.value = null
    }

    fun retryAfterNetworkError() {
        viewModelScope.launch {
            entitlementRepository.retryAfterNetworkError()
        }
    }
}
