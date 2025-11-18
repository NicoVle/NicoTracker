package com.example.nicotracker

import com.example.nicotracker.data.AppDatabase
import com.example.nicotracker.data.CategoryRepository
import com.example.nicotracker.data.CategoryDao
import com.example.nicotracker.data.Category
import com.example.nicotracker.data.CategoryViewModel
import com.example.nicotracker.data.CategoryViewModelFactory


import android.os.Bundle
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



// ==============================================
//  DATA MODELS
// ==============================================

enum class EntryType(val label: String) {
    SOMMEIL("Sommeil"),
    SPORT("Sport"),
    REPAS("Repas"),
    HUMEUR("Humeur"),
    ACTION_PRODUCTIVE("Action productive")
}

data class JournalEntry(
    val id: Int,
    val date: String,
    val type: EntryType,
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
        // --- INITIALISATION ROOM + REPOSITORY + VIEWMODEL ---

        val database = AppDatabase.getDatabase(applicationContext)
        val categoryDao = database.categoryDao()
        val categoryRepository = CategoryRepository(categoryDao)
        val categoryViewModel = CategoryViewModelFactory(categoryRepository)
            .create(CategoryViewModel::class.java)


        setContent {
            MaterialTheme {
                NicoTrackerApp(categoryViewModel)
            }
        }
    }
}

// ==============================================
//  APP STRUCTURE WITH SCAFFOLD
// ==============================================

@Composable
fun NicoTrackerApp(categoryViewModel: CategoryViewModel) {

    // --- État : écran courant ---
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Today) }

    // --- État : liste des entrées du jour (pour l’instant en mémoire seulement) ---
    val entries = remember {
        mutableStateListOf(
            JournalEntry(
                id = 1,
                date = "04/11/2025",
                type = EntryType.SOMMEIL,
                details = "Couché 0h00, réveil 10h30",
                duree = "10h30",
                quantite = null,
                qualite = 10,
                commentaire = "Super nuit"
            )
        )
    }

    // --- Écrans visibles dans la barre du bas ---
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
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = screen.label
                            )
                        },
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
                    entries = entries,
                    onAddEntry = { newEntry ->
                        // Ajoute la nouvelle entrée en haut de la liste
                        entries.add(0, newEntry)
                    }
                )

                Screen.History -> HistoryScreen(
                    entries = entries
                )


                Screen.Stats -> StatsScreen()

                Screen.Settings -> SettingsScreen(
                    onManageCategories = {
                        currentScreen = Screen.ManageCategories
                    }
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
    nextId: Int,
    onValidate: (JournalEntry) -> Unit,
    onCancel: () -> Unit
) {
    // --- States internes pour les champs du formulaire ---
    var expanded by remember { mutableStateOf(false) }               // état d’ouverture du menu déroulant
    var selectedType by remember { mutableStateOf<EntryType?>(null) } // type sélectionné (null au début)

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

                // --- Champ Type sous forme de menu déroulant ---
                Text("Type", fontWeight = FontWeight.Bold)
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    TextField(
                        readOnly = true,
                        value = selectedType?.label ?: "Choisir un type",
                        onValueChange = {},
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded)
                        }
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        EntryType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.label) },
                                onClick = {
                                    selectedType = type
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                // On n’affiche le reste des champs QUE si un type est choisi
                if (selectedType != null) {

                    Spacer(Modifier.height(8.dp))

                    // Ici on met pour l’instant les champs génériques
                    // Plus tard on pourra faire un when(selectedType) { ... } pour adapter selon le type

                    // --- Champ Détails ---
                    Text("Détails", fontWeight = FontWeight.Bold)
                    TextField(
                        value = details,
                        onValueChange = { details = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))

                    // --- Champ Durée ---
                    Text("Durée", fontWeight = FontWeight.Bold)
                    TextField(
                        value = duree,
                        onValueChange = { duree = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))

                    // --- Champ Quantité ---
                    Text("Quantité", fontWeight = FontWeight.Bold)
                    TextField(
                        value = quantite,
                        onValueChange = { quantite = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))

                    // --- Champ Qualité ---
                    Text("Qualité (0-10)", fontWeight = FontWeight.Bold)
                    TextField(
                        value = qualiteText,
                        onValueChange = { qualiteText = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(Modifier.height(8.dp))

                    // --- Champ Commentaire ---
                    Text("Commentaire", fontWeight = FontWeight.Bold)
                    TextField(
                        value = commentaire,
                        onValueChange = { commentaire = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp),
                        singleLine = false,
                        maxLines = 4
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Si aucun type n’est choisi, on ne valide pas
                val type = selectedType ?: return@TextButton

                val qualite = qualiteText.toIntOrNull()

                val newEntry = JournalEntry(
                    id = nextId,
                    date = date,
                    type = type,
                    details = details.ifBlank { "(Sans détails)" },
                    duree = duree.ifBlank { null },
                    quantite = quantite.ifBlank { null },
                    qualite = qualite,
                    commentaire = commentaire.ifBlank { null }
                )

                onValidate(newEntry)
            }) {
                Text("Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("Annuler")
            }
        }
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

            Text("${entry.type.label} – ${entry.date}", fontWeight = FontWeight.Bold)
            Text(entry.details)

            entry.qualite?.let {
                Text("Qualité : ${it}/10", fontSize = 12.sp)
            }

            entry.commentaire?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, fontSize = 12.sp)
            }
        }
    }
}
