package com.example.nicotracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.nicotracker.data.Category
import com.example.nicotracker.data.CategoryViewModel



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCategoriesScreen(
    categoryViewModel: CategoryViewModel,
    onBack: () -> Unit
) {
    val categories by categoryViewModel.categories.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<Category?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gérer les catégories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Delete, contentDescription = "Retour")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter")
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                items(categories) { category ->
                    CategoryRow(
                        category = category,
                        onDelete = { categoryViewModel.deleteCategory(category) },
                        onRename = { showRenameDialog = category }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            onAdd = { name ->
                categoryViewModel.addCategory(name)
                showAddDialog = false
            },
            onCancel = { showAddDialog = false }
        )
    }

    showRenameDialog?.let { categoryToRename ->
        RenameCategoryDialog(
            oldCategory = categoryToRename,
            onRename = { newName ->
                categoryViewModel.renameCategory(categoryToRename, newName)
                showRenameDialog = null
            },
            onCancel = { showRenameDialog = null }
        )
    }
}

@Composable
fun CategoryRow(
    category: Category,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(category.name)
            Row {
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = "Renommer")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                }
            }
        }
    }
}

@Composable
fun AddCategoryDialog(
    onAdd: (String) -> Unit,
    onCancel: () -> Unit
) {
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Ajouter une catégorie") },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Nom de la catégorie") }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onAdd(text.trim()) }) {
                Text("Ajouter")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Annuler")
            }
        }
    )
}

@Composable
fun RenameCategoryDialog(
    oldCategory: Category,
    onRename: (String) -> Unit,
    onCancel: () -> Unit
) {
    var text by remember { mutableStateOf(oldCategory.name) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Renommer la catégorie") },
        text = {
            TextField(
                value = text,
                onValueChange = { text = it }
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onRename(text.trim()) }) {
                Text("Renommer")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Annuler")
            }
        }
    )
}
