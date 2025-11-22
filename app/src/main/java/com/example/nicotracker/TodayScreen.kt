package com.example.nicotracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nicotracker.data.* // Importe tous nos DAOs, ViewModels, etc.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    categoryViewModel: CategoryViewModel,
    journalEntryViewModel: JournalEntryViewModel
) {
    // 1. Récupération des catégories (pour le menu déroulant)
    val categoriesListState = categoryViewModel.allCategories.collectAsState(initial = emptyList())
    val categories = categoriesListState.value

    // 2. Récupération des entrées permanentes depuis la BDD (pour l'affichage)
    val entriesListState = journalEntryViewModel.allEntries.collectAsState(initial = emptyList())
    val entries = entriesListState.value

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter une entrée")
            }
        }
    ) { padding ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp)) {

            Text(
                text = "Entrées du jour (${entries.size})",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            // Affichage de la liste des entrées permanentes
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(entries) { entry ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = { /* Optionnel: ouvrir les détails */ }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Catégorie : ${entry.categoryName}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Description : ${entry.description}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (entry.value != null) {
                                Text(
                                    text = "Valeur : ${entry.value}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialogue d'ajout d'entrée
    if (showAddDialog) {
        var selectedCategoryName by remember { mutableStateOf(categories.firstOrNull()?.name ?: "") }
        var entryDescription by remember { mutableStateOf("") }
        var entryValue by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Ajouter une entrée") },
            text = {
                Column {
                    // Sélection de la catégorie
                    CategoryDropdown(
                        categories = categories,
                        selectedCategoryName = selectedCategoryName,
                        onCategorySelected = { selectedCategoryName = it }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Champ Description
                    OutlinedTextField(
                        value = entryDescription,
                        onValueChange = { entryDescription = it },
                        label = { Text("Description/Détails") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Champ Valeur (pour l'instant, c'est générique)
                    OutlinedTextField(
                        value = entryValue,
                        onValueChange = { entryValue = it },
                        label = { Text("Valeur (Durée, Quantité, Calories...)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedCategoryName.isNotEmpty() && entryDescription.isNotBlank()) {

                        // ENREGISTREMENT FINAL DANS LA BASE DE DONNÉES
                        val newEntry = JournalEntry(
                            categoryName = selectedCategoryName,
                            description = entryDescription,
                            value = entryValue.toFloatOrNull() // Convertit la chaîne en Float
                        )
                        journalEntryViewModel.insert(newEntry)

                        showAddDialog = false
                    }
                }) {
                    Text("Ajouter")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

// Composant utilitaire pour la liste déroulante des catégories
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDropdown(
    categories: List<Category>,
    selectedCategoryName: String,
    onCategorySelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
    ) {
        OutlinedTextField(
            readOnly = true,
            value = selectedCategoryName,
            onValueChange = { },
            label = { Text("Catégorie") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = {
                        onCategorySelected(category.name)
                        expanded = false
                    }
                )
            }
        }
    }
}