package com.amko.roadflow.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun LocationPermissionExplanationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Dozvola za lokaciju",
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = buildAnnotatedString {
                        append("Potrebno je dozvoliti aplikaciji ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("RoadFlow")
                        }
                        append(" da pristupa lokaciji vašeg uređaja ako želite koristiti ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("aktivno praćenje")
                        }
                        append(" i ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("signalizaciju")
                        }
                        append(".\n\nZa bolje iskustvo odaberite:\n\n")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("• Tačna (precizna) lokacija\n")
                        }
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("• Dozvoli sve vrijeme")
                        }
                    },
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        AutoSizeText(
                            text = "ODUSTANI",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        AutoSizeText(
                            text = "RAZUMIJEM",
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoSizeText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    var multiplier by remember { mutableFloatStateOf(1f) }

    Text(
        text = text,
        color = color,
        maxLines = 1,
        softWrap = false,
        fontSize = 14.sp * multiplier,
        overflow = TextOverflow.Visible,
        modifier = modifier,
        onTextLayout = {
            if (it.hasVisualOverflow) {
                multiplier *= 0.9f
            }
        }
    )
}