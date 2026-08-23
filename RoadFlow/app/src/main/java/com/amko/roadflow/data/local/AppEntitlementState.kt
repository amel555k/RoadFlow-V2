package com.amko.roadflow.data.local

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AppEntitlementState {

    private var repository: EntitlementRepository? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun init(application: Application) {
        if (repository != null) return

        val firebaseService = FirebaseService()
        val paymentService = PaymentService(firebaseService)
        repository = EntitlementRepository(application, paymentService)

        scope.launch {
            repository?.initializeAsync()
        }
    }

    fun getRepository(): EntitlementRepository {
        return repository ?: throw IllegalStateException("AppEntitlementState.init() must be called before use")
    }
}