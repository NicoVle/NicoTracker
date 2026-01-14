package com.example.nicotracker.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.nicotracker.NeonCyan
import com.example.nicotracker.NeonOrange

@Composable
fun ChallengeSelectionDialog(
    onDismiss: () -> Unit,
    onHistoryClick: () -> Unit,
    onBankClick: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "INTERFACE DÉFIS",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(24.dp))

                // Bouton BANQUE (Le gros bouton tactique)
                BigTacticalButton(
                    text = "ARMURERIE (BANQUE)",
                    icon = Icons.Default.Inventory2,
                    color = NeonCyan,
                    onClick = onBankClick
                )

                Spacer(Modifier.height(16.dp))

                // Bouton HISTORIQUE
                BigTacticalButton(
                    text = "HISTORIQUE & STATS",
                    icon = Icons.Default.History,
                    color = NeonOrange,
                    onClick = onHistoryClick
                )
            }
        }
    }
}

@Composable
fun BigTacticalButton(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = color)
            Spacer(Modifier.width(12.dp))
            Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}