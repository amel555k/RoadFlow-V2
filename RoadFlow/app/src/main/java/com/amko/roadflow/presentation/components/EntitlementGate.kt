package com.amko.roadflow.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.amko.roadflow.data.local.EntitlementCheckState
import com.amko.roadflow.presentation.screens.ConfirmPaymentScreen
import com.amko.roadflow.presentation.screens.PaywallScreen
import com.amko.roadflow.presentation.viewmodel.EntitlementViewModel

@Composable
fun EntitlementGate(
    backgroundImageRes: Int,
    currentRoute: String? = null,
    onNavigate: ((String) -> Unit)? = null,
    entitlementViewModel: EntitlementViewModel = viewModel(),
    content: @Composable () -> Unit
) {
    val isPremium by entitlementViewModel.isPremium.collectAsState()
    val isInitialized by entitlementViewModel.isInitialized.collectAsState()
    val checkState by entitlementViewModel.checkState.collectAsState()
    val price by entitlementViewModel.price.collectAsState()
    val showConfirmPayment by entitlementViewModel.showConfirmPayment.collectAsState()
    val isPurchasing by entitlementViewModel.isPurchasing.collectAsState()
    val purchaseError by entitlementViewModel.purchaseError.collectAsState()

    val context = LocalContext.current

    var dialogDismissedByUser by remember { mutableStateOf(false) }

    LaunchedEffect(checkState) {
        if (checkState != EntitlementCheckState.NETWORK_ERROR) {
            dialogDismissedByUser = false
        }
    }

    DisposableEffect(checkState) {
        if (checkState != EntitlementCheckState.NETWORK_ERROR) {
            return@DisposableEffect onDispose {}
        }

        val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                entitlementViewModel.retryAfterNetworkError()
            }
        }
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        onDispose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                !isInitialized || checkState == EntitlementCheckState.CHECKING -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Učitavanje...",
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                checkState == EntitlementCheckState.NETWORK_ERROR -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        if (!dialogDismissedByUser) {
                            NoConnectionDialog(
                                title = "Nema internet konekcije",
                                message = "Provjerite internet konekciju kako bi aplikacija mogla nastaviti.",
                                confirmButtonText = "U REDU",
                                onDismiss = {
                                    dialogDismissedByUser = true
                                    entitlementViewModel.retryAfterNetworkError()
                                }
                            )
                        }
                    }
                }

                isPremium -> content()

                showConfirmPayment -> {
                    ConfirmPaymentScreen(
                        price = price,
                        isPurchasing = isPurchasing,
                        purchaseError = purchaseError,
                        onUplataClick = {
                            (context as? android.app.Activity)?.let { entitlementViewModel.confirmPayment(it) }
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

        if (currentRoute != null && onNavigate != null && !isPremium) {
            BottomNavBar(
                currentRoute = currentRoute,
                onNavigate = onNavigate,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}