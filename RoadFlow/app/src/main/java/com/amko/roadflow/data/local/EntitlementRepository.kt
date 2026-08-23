package com.amko.roadflow.data.local

import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate

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
    }

    suspend fun checkIsAlreadySubscribedAsync(email: String): Boolean {
        val result = paymentService.isPremiumAsync(email)
        _isPremium.value = result
        return result
    }

    suspend fun initializeAsync() {
        val currentMonthKey = "${LocalDate.now().year}-${LocalDate.now().monthValue}"
        val savedMonthKey = prefs.getString("subscription_check_month", null)

        if (savedMonthKey != currentMonthKey) {
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

        _isInitialized.value = true
    }
}