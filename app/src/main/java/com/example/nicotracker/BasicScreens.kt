package com.example.nicotracker

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HistoryScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Historique (écran placeholder)", style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
fun StatsScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Statistiques (écran placeholder)", style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
fun SettingsScreen(
    onManageSubCategories: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            "Paramètres",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(24.dp))


        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onManageSubCategories,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Gérer les sous-catégories")
        }
    }
}
