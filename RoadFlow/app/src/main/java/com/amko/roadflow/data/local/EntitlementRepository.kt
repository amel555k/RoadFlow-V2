package com.amko.roadflow.data.local

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

enum class EntitlementCheckState {
    CHECKING,
    PREMIUM,
    PAYWALL,
    NETWORK_ERROR
}

class EntitlementRepository(
    private val application: Application,
    private val paymentService: PaymentService
) {
    private val prefs = application.getSharedPreferences("roadflow_prefs", android.content.Context.MODE_PRIVATE)

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium

    private val _isSubscriptionRequired = MutableStateFlow(true)
    val isSubscriptionRequired: StateFlow<Boolean> = _isSubscriptionRequired

    private val _price = MutableStateFlow("3")
    val price: StateFlow<String> = _price

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized

    private val _checkState = MutableStateFlow(EntitlementCheckState.CHECKING)
    val checkState: StateFlow<EntitlementCheckState> = _checkState

    fun getSavedEmail(): String? {
        return prefs.getString("logged_in_email", null)
    }

    fun saveEmail(email: String) {
        prefs.edit().putString("logged_in_email", email).apply()
    }

    fun clearEmail() {
        prefs.edit().remove("logged_in_email").apply()
        _isPremium.value = false
    }

    fun registerLogin(email: String) {
        saveEmail(email)
    }

    suspend fun confirmPaymentAsync(email: String) {
        _isPremium.value = paymentService.ensureUserEntryAsync(email)
        _checkState.value = if (_isPremium.value) EntitlementCheckState.PREMIUM else EntitlementCheckState.PAYWALL
    }

    suspend fun checkIsAlreadySubscribedAsync(email: String): Boolean {
        val result = paymentService.isPremiumAsync(email)
        _isPremium.value = result
        return result
    }

    private fun isInternetAvailable(): Boolean {
        val cm = application.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun initializeAsync() {
        _checkState.value = EntitlementCheckState.CHECKING

        val currentMonthKey = "${LocalDate.now().year}-${LocalDate.now().monthValue}"
        val savedMonthKey = prefs.getString("subscription_check_month", null)

        if (savedMonthKey != currentMonthKey) {
            if (!isInternetAvailable()) {
                _checkState.value = EntitlementCheckState.NETWORK_ERROR
                _isInitialized.value = true
                return
            }

            val globalStatus = paymentService.getGlobalStatusAsync()
            val fetchedPrice = paymentService.getPriceAsync()

            prefs.edit()
                .putString("subscription_check_month", currentMonthKey)
                .putBoolean("subscription_required_cached", globalStatus)
                .putString("price_cached", fetchedPrice)
                .apply()

            _isSubscriptionRequired.value = globalStatus
            _price.value = fetchedPrice
        } else {
            _isSubscriptionRequired.value = prefs.getBoolean("subscription_required_cached", true)
            _price.value = prefs.getString("price_cached", "3") ?: "3"
        }

        val email = getSavedEmail()
        if (email != null && _isSubscriptionRequired.value) {
            _isPremium.value = paymentService.isPremiumAsync(email)
        } else if (!_isSubscriptionRequired.value) {
            _isPremium.value = true
        }

        _checkState.value = if (_isPremium.value) EntitlementCheckState.PREMIUM else EntitlementCheckState.PAYWALL
        _isInitialized.value = true
    }

    suspend fun retryAfterNetworkError() {
        if (_checkState.value != EntitlementCheckState.NETWORK_ERROR) return
        initializeAsync()
    }
}