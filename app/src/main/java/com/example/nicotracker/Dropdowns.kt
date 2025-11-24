package com.example.nicotracker

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.nicotracker.data.Category
import com.example.nicotracker.data.SubCategory

// ------------------- CATEGORY DROPDOWN -------------------

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
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedCategoryName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Catégorie") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
            }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
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



// ------------------- SUBCATEGORY DROPDOWN -------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubCategoryDropdown(
    subCategories: List<SubCategory>,
    selectedSubCategoryId: Int?,
    onSelected: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedName = subCategories.firstOrNull {
        it.id == selectedSubCategoryId
    }?.name ?: "Aucune"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Sous-catégorie") },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
            }
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            subCategories.forEach { sub ->
                DropdownMenuItem(
                    text = { Text(sub.name) },
                    onClick = {
                        onSelected(sub.id)
                        expanded = false
                    }
                )
            }

            DropdownMenuItem(
                text = { Text("Aucune") },
                onClick = {
                    onSelected(null)
                    expanded = false
                }
            )
        }
    }
}
