package com.amko.roadflow.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amko.roadflow.presentation.screens.ConfirmPaymentScreen
import com.amko.roadflow.presentation.screens.PaywallScreen
import com.amko.roadflow.presentation.viewmodel.EntitlementViewModel

@Composable
fun EntitlementGate(
    backgroundImageRes: Int,
    entitlementViewModel: EntitlementViewModel = viewModel(),
    content: @Composable () -> Unit
) {
    val isPremium by entitlementViewModel.isPremium.collectAsState()
    val isInitialized by entitlementViewModel.isInitialized.collectAsState()
    val price by entitlementViewModel.price.collectAsState()
    val showConfirmPayment by entitlementViewModel.showConfirmPayment.collectAsState()
    val isPurchasing by entitlementViewModel.isPurchasing.collectAsState()
    val purchaseError by entitlementViewModel.purchaseError.collectAsState()

    val context = LocalContext.current
    val activity = context as? android.app.Activity

    when {
        !isInitialized -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        isPremium -> content()

        showConfirmPayment -> {
            ConfirmPaymentScreen(
                price = price,
                isPurchasing = isPurchasing,
                purchaseError = purchaseError,
                onUplataClick = {
                    activity?.let { entitlementViewModel.confirmPayment(it) }
                }
            )
        }

        else -> {
            PaywallScreen(
                backgroundImageRes = backgroundImageRes,
                price = price,
                isCheckingEntitlement = false,
                onGoogleClick = { entitlementViewModel.signIn() }
            )
        }
    }
}