package com.amko.roadflow.domain.model

sealed class SubscriptionResult {
    object Success : SubscriptionResult()
    object UserCancelled : SubscriptionResult()
    object AlreadyOwned : SubscriptionResult()
    object ProductNotAvailable : SubscriptionResult()
    data class Error(val message: String) : SubscriptionResult()
}