package com.example.nicotracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nicotracker.data.*
import com.example.nicotracker.ui.theme.NicoTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(this)
        val categoryRepository = CategoryRepository(database.categoryDao())
        val journalRepository = JournalEntryRepository(database.journalEntryDao())
        val subCategoryRepository = SubCategoryRepository(database.subCategoryDao())

        setContent {
            NicoTrackerTheme {

                val categoryViewModel: CategoryViewModel = viewModel(
                    factory = CategoryViewModelFactory(categoryRepository)
                )
                val journalEntryViewModel: JournalEntryViewModel = viewModel(
                    factory = JournalEntryViewModelFactory(journalRepository)
                )
                val subCategoryViewModel: SubCategoryViewModel = viewModel(
                    factory = SubCategoryViewModelFactory(subCategoryRepository)
                )

                // Insère les catégories par défaut si la base est vide
                LaunchedEffect(Unit) {
                    categoryViewModel.insertAllIfEmpty(
                        listOf(
                            Category(name = "Sport"),
                            Category(name = "Repas"),
                            Category(name = "Sommeil"),
                            Category(name = "Action productive"),
                            Category(name = "Temps d'écran"),
                            Category(name = "Nombre de pas"),
                            Category(name = "Humeur")
                        )
                    )
                }

                NicoTrackerApp(
                    categoryViewModel,
                    journalEntryViewModel,
                    subCategoryViewModel
                )
            }
        }
    }
}


// -------------------- NAVIGATION ------------------------

sealed class Screen(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    object Today : Screen("Aujourd'hui", Icons.Filled.Home)
    object History : Screen("Historique", Icons.Filled.List)
    object Stats : Screen("Stats", Icons.Filled.Star)
    object Settings : Screen("Paramètres", Icons.Filled.Settings)
    object ManageSubCategories : Screen("Gérer sous-catégories", Icons.Filled.Settings)
}


@Composable
fun NicoTrackerApp(
    categoryViewModel: CategoryViewModel,
    journalEntryViewModel: JournalEntryViewModel,
    subCategoryViewModel: SubCategoryViewModel
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Today) }
    var showAddDialog by remember { mutableStateOf(false) }

    val bottomScreens = listOf(Screen.Today, Screen.History, Screen.Stats, Screen.Settings)

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomScreens.forEach { screen ->
                    NavigationBarItem(
                        selected = (screen == currentScreen),
                        onClick = { currentScreen = screen },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (currentScreen is Screen.Today) {
                FloatingActionButton(
                    onClick = { showAddDialog = true }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Ajouter")
                }
            }
        }
    ) { padding ->

        when (currentScreen) {

            Screen.Today -> TodayScreen(
                categoryViewModel = categoryViewModel,
                journalEntryViewModel = journalEntryViewModel,
                subCategoryViewModel = subCategoryViewModel,
                showAddDialog = showAddDialog,
                onDismissDialog = { showAddDialog = false },
                modifier = Modifier.padding(padding)
            )

            Screen.History -> HistoryScreen()
            Screen.Stats -> StatsScreen()

            Screen.Settings -> SettingsScreen(
                onManageSubCategories = { currentScreen = Screen.ManageSubCategories }
            )

            Screen.ManageSubCategories -> ManageSubCategoriesScreen(
                categoryViewModel = categoryViewModel,
                subCategoryViewModel = subCategoryViewModel,
                onBack = { currentScreen = Screen.Settings }
            )
        }
    }
}
