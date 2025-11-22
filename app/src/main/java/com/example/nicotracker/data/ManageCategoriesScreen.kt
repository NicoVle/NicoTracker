package com.example.nicotracker.data

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCategoriesScreen(
    categoryViewModel: CategoryViewModel,
    onBack: () -> Unit
) {
    // CORRECTION 1 : On utilise 'allCategories' comme défini dans le ViewModel
    val categoriesListState = categoryViewModel.allCategories.collectAsState(initial = emptyList())
    val categories = categoriesListState.value

    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gérer les catégories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (categories.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucune catégorie. Ajoutez-en une !")
                }
            } else {
                LazyColumn {
                    items(categories) { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = category.name, style = MaterialTheme.typography.bodyLarge)

                            IconButton(onClick = {
                                // CORRECTION 2 : On utilise 'delete'
                                categoryViewModel.delete(category)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Divider()
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var newCategoryName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Nouvelle catégorie") },
            text = {
                TextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    label = { Text("Nom de la catégorie") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCategoryName.isNotBlank()) {
                        // CORRECTION 3 : On utilise 'insert' et on crée l'objet Category
                        categoryViewModel.insert(Category(name = newCategoryName))
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