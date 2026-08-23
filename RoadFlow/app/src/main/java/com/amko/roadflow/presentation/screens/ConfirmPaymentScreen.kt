package com.amko.roadflow.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DarkBackground = Color(0xFF0E1A2B)
private val CardSurface = Color(0xFF16273D)
private val TextSecondary = Color(0xFF9FB3C8)
private val AccentBlue = Color(0xFF6FA8DC)

@Composable
fun ConfirmPaymentScreen(
    price: String,
    isPurchasing: Boolean,
    purchaseError: String?,
    onUplataClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(CardSurface)
                .padding(28.dp)
        ) {
            Text(
                text = "Nastaviti uplatu?",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${price}KM / mjesec",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = AccentBlue,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (isPurchasing) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Button(
                    onClick = onUplataClick,
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Uplata", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Otkažite pretplatu bilo kada",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            if (purchaseError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = purchaseError,
                    color = Color(0xFFE57373),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}