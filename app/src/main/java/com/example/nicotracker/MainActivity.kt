package com.example.nicotracker

import com.example.nicotracker.data.AppDatabase
import com.example.nicotracker.data.CategoryRepository
import com.example.nicotracker.data.CategoryDao
import com.example.nicotracker.data.Category
import com.example.nicotracker.data.CategoryViewModel
import com.example.nicotracker.data.CategoryViewModelFactory
import com.example.nicotracker.data.ManageCategoriesScreen
import com.example.nicotracker.data.JournalEntryViewModel
import com.example.nicotracker.data.JournalEntryViewModelFactory
import com.example.nicotracker.data.JournalEntryRepository


import android.os.Bundle
import androidx.lifecycle.viewmodel.compose.viewModel // Pour la fonction viewModel()
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel // TRÈS IMPORTANT pour viewModel()
import com.example.nicotracker.ui.theme.NicoTrackerTheme // TRÈS IMPORTANT pour NicoTrackerTheme


// ==============================================
//  DATA MODELS
// ==============================================



data class JournalEntry(
    val id: Int,
    val date: String,
    val type: String, // <--- C'est maintenant du texte (ex: "Boxe", "Sieste")
    val details: String,
    val duree: String? = null,
    val quantite: String? = null,
    val qualite: Int? = null,
    val commentaire: String? = null
)

// ==============================================
//  BOTTOM NAV SCREENS
// ==============================================

sealed class Screen(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    object Today : Screen("today", "Aujourd'hui", Icons.Filled.Home)
    object History : Screen("history", "Historique", Icons.Filled.Home)
    object Stats : Screen("stats", "Stats", Icons.Filled.Settings)
    object Settings : Screen("settings", "Paramètres", Icons.Filled.Settings)
    object ManageCategories : Screen(
        route = "manage_categories",
        label = "Catégories",
        icon = Icons.Filled.Settings   // icône peu importe, on ne l’utilisera pas dans la barre du bas
    )
}

// ==============================================
//  MAIN ACTIVITY
// ==============================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialisation de la BDD et des Repositories
        val database = AppDatabase.getDatabase(this)
        val categoryRepository = CategoryRepository(database.categoryDao())

        // NOUVEAU : Initialisation du Repository et du ViewModel pour les entrées
        val journalEntryRepository = JournalEntryRepository(database.journalEntryDao())

        setContent {
            NicoTrackerTheme {
                NicoTrackerApp(
                    categoryViewModel = viewModel(
                        factory = CategoryViewModelFactory(categoryRepository)
                    ),
                    // NOUVEAU : Passage du JournalEntryViewModel
                    journalEntryViewModel = viewModel(
                        factory = JournalEntryViewModelFactory(journalEntryRepository)
                    )
                )
            }
        }
    }
}

// ==============================================
//  APP STRUCTURE WITH SCAFFOLD
// ==============================================

@Composable
fun NicoTrackerApp(categoryViewModel: CategoryViewModel,
                   journalEntryViewModel: JournalEntryViewModel) {

    // --- État : écran courant ---
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Today) }

    // --- RÉCUPÉRATION DES CATÉGORIES DEPUIS LA BDD ---
    // On suppose que votre ViewModel a une variable 'allCategories' qui est un Flow/State
    // Si ça souligne en rouge, vérifiez le nom dans votre CategoryViewModel
    val categoriesListState = categoryViewModel.allCategories.collectAsState(initial = emptyList())
    val categories = categoriesListState.value

    // --- État : liste des entrées du jour ---
    // Note : Je mets une liste vide pour démarrer propre, car les anciens exemples utilisaient l'Enum
    val entries = remember { mutableStateListOf<JournalEntry>() }

    val bottomScreens = listOf(
        Screen.Today,
        Screen.History,
        Screen.Stats,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                bottomScreens.forEach { screen ->
                    NavigationBarItem(
                        selected = (screen == currentScreen),
                        onClick = { currentScreen = screen },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (currentScreen) {
                Screen.Today -> TodayScreen(
                    // NOUVEAU : on passe le ViewModel des entrées à TodayScreen
                    categoryViewModel = categoryViewModel,
                    journalEntryViewModel = journalEntryViewModel)

                Screen.History -> HistoryScreen(entries = entries)

                Screen.Stats -> StatsScreen()

                Screen.Settings -> SettingsScreen(
                    onManageCategories = { currentScreen = Screen.ManageCategories }
                )

                Screen.ManageCategories -> ManageCategoriesScreen(
                    categoryViewModel = categoryViewModel,
                    onBack = { currentScreen = Screen.Settings }
                )
            }
        }
    }
}

// ==============================================
//  TODAY SCREEN
// ==============================================

@Composable
fun TodayScreen(
    entries: List<JournalEntry>,
    categories: List<Category>, // <--- On reçoit les vraies catégories ici
    onAddEntry: (JournalEntry) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    Column(Modifier.padding(16.dp)) {
        Text("Aujourd'hui", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Button(onClick = { showDialog = true }) {
            Text("Ajouter une entrée")
        }
        Spacer(Modifier.height(16.dp))

        LazyColumn {
            items(entries) { entry ->
                EntryCard(entry)
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showDialog) {
        NewEntryDialog(
            date = today,
            categories = categories, // <--- On les passe au dialogue
            nextId = entries.size + 1,
            onValidate = {
                onAddEntry(it)
                showDialog = false
            },
            onCancel = { showDialog = false }
        )
    }
}

// ==============================================
//  NEW ENTRY DIALOG
// ==============================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEntryDialog(
    date: String,
    categories: List<Category>, // <--- Reçoit la liste de la BDD
    nextId: Int,
    onValidate: (JournalEntry) -> Unit,
    onCancel: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // On stocke le NOM de la catégorie sélectionnée (String)
    var selectedCategoryName by remember { mutableStateOf<String?>(null) }

    var details by remember { mutableStateOf("") }
    var duree by remember { mutableStateOf("") }
    var quantite by remember { mutableStateOf("") }
    var qualiteText by remember { mutableStateOf("") }
    var commentaire by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Nouvelle entrée") },
        text = {
            Column {
                Text("Type", fontWeight = FontWeight.Bold)

                // SI AUCUNE CATÉGORIE N'EXISTE DANS LES SETTINGS
                if (categories.isEmpty()) {
                    Text("Aucune catégorie ! Allez dans Paramètres pour en créer.", color = MaterialTheme.colorScheme.error)
                } else {
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        TextField(
                            readOnly = true,
                            value = selectedCategoryName ?: "Choisir...",
                            onValueChange = {},
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            // --- BOUCLE SUR VOS VRAIES CATÉGORIES ---
                            categories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) }, // On suppose que votre objet Category a un champ 'name'
                                    onClick = {
                                        selectedCategoryName = category.name
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Affichage des champs si une catégorie est choisie
                if (selectedCategoryName != null) {
                    Spacer(Modifier.height(8.dp))
                    Text("Détails", fontWeight = FontWeight.Bold)
                    TextField(value = details, onValueChange = { details = it }, modifier = Modifier.fillMaxWidth())

                    // ... (Le reste des champs Durée, Quantité, etc. reste identique) ...
                    // Je ne remets pas tout le code des champs pour raccourcir,
                    // gardez ce que vous aviez pour Durée, Quantité, Qualité, Commentaire.
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val typeName = selectedCategoryName ?: return@TextButton
                val newEntry = JournalEntry(
                    id = nextId,
                    date = date,
                    type = typeName, // On sauvegarde le String
                    details = details,
                    duree = duree,
                    quantite = quantite,
                    qualite = qualiteText.toIntOrNull(),
                    commentaire = commentaire
                )
                onValidate(newEntry)
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Annuler") } }
    )
}


// ==============================================
//  OTHER SCREENS
// ==============================================

@Composable
fun HistoryScreen(entries: List<JournalEntry>) {
    Column(Modifier.padding(16.dp)) {
        Text("Historique", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        LazyColumn {
            items(entries) {
                EntryCard(it)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun StatsScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Stats (à venir)")
    }
}

@Composable
fun SettingsScreen(
    onManageCategories: () -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        Text("Paramètres", fontSize = 22.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(16.dp))

        Button(onClick = { /* TODO export CSV */ }) {
            Text("Exporter CSV")
        }

        Spacer(Modifier.height(12.dp))

        Button(onClick = onManageCategories) {
            Text("Gérer les catégories")
        }
    }
}

// ==============================================
//  ENTRY CARD
// ==============================================

@Composable
fun EntryCard(entry: JournalEntry) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            // On affiche directement entry.type car c'est maintenant un String
            Text("${entry.type} – ${entry.date}", fontWeight = FontWeight.Bold)
            Text(entry.details)
            // ... le reste ne change pas
        }
    }
}
