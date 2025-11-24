package com.example.nicotracker.data

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import com.example.nicotracker.CategoryDropdown


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSubCategoriesScreen(
    categoryViewModel: CategoryViewModel,
    subCategoryViewModel: SubCategoryViewModel,
    onBack: () -> Unit
) {
    val categories by categoryViewModel.allCategories.collectAsState(initial = emptyList())

    var selectedCategoryName by remember { mutableStateOf(categories.firstOrNull()?.name ?: "") }
    val selectedCategory = categories.firstOrNull { it.name == selectedCategoryName }
    val selectedCategoryId = selectedCategory?.id ?: -1

    val subCategories by subCategoryViewModel
        .getSubCategoriesForCategory(selectedCategoryId)
        .collectAsState(initial = emptyList())

    var newSubCategoryName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gérer les sous-catégories") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {

            Text("Catégorie", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            CategoryDropdown(
                categories = categories,
                selectedCategoryName = selectedCategoryName,
                onCategorySelected = { selectedCategoryName = it }
            )

            Spacer(Modifier.height(24.dp))

            Text("Sous-catégories", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            LazyColumn {
                items(subCategories) { sub ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(sub.name)
                        IconButton(onClick = { subCategoryViewModel.delete(sub) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer")
                        }
                    }
                    Divider()
                }
            }

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = newSubCategoryName,
                onValueChange = { newSubCategoryName = it },
                label = { Text("Nouvelle sous-catégorie") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    if (newSubCategoryName.isNotBlank() && selectedCategoryId != -1) {
                        subCategoryViewModel.insert(
                            SubCategory(
                                name = newSubCategoryName,
                                parentCategoryId = selectedCategoryId
                            )
                        )
                        newSubCategoryName = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Ajouter")
            }
        }
    }
}
