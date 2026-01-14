package com.example.nicotracker

import android.graphics.Color as AndroidColor // Alias pour éviter les conflits
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge // Import magique
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nicotracker.data.*
import com.example.nicotracker.stats.*
import com.example.nicotracker.ui.theme.NicoTrackerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.nicotracker.avatar.AvatarScreen
import com.example.nicotracker.avatar.AvatarViewModel
import com.example.nicotracker.avatar.AvatarViewModelFactory
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

// --- COULEUR DE FOND GLOBALE ---
val AppBackgroundColor = Color(0xFF121212)

class MainActivity : ComponentActivity() {

    private lateinit var entryStorage: EntryStorage
    private val journalEntryViewModel: JournalEntryViewModel by viewModels {
        JournalEntryViewModelFactory(entryStorage)
    }

    private lateinit var exportJsonLauncher: ActivityResultLauncher<String>
    private lateinit var exportCsvLauncher: ActivityResultLauncher<String>

    private var onEntriesImported: (() -> Unit)? = null
    private var onCategoriesImported: (() -> Unit)? = null
    private var onSubCategoriesImported: (() -> Unit)? = null

    private val importJsonLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val jsonString = contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }

                    if (!jsonString.isNullOrBlank()) {
                        entryStorage.importFromJson(jsonString)
                        lifecycleScope.launch(Dispatchers.Main) {
                            onEntriesImported?.invoke()
                            onCategoriesImported?.invoke()
                            onSubCategoriesImported?.invoke()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private var jsonToExport: String = ""
    private var csvToExport: String = ""

    private lateinit var healthConnectManager: HealthConnectManager


    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        // On vérifie si les permissions sont accordées
        val readStepsPerm = androidx.health.connect.client.permission.HealthPermission.getReadPermission(
            androidx.health.connect.client.records.StepsRecord::class
        )
        val readNutritionPerm = androidx.health.connect.client.permission.HealthPermission.getReadPermission(
            androidx.health.connect.client.records.NutritionRecord::class
        )

        if (granted[readStepsPerm] == true || granted[readNutritionPerm] == true) {
            lifecycleScope.launch {
                // On lance la synchro immédiatement si on a l'une des permissions
                kotlinx.coroutines.delay(500)

                // On synchronise les pas si permis
                if (granted[readStepsPerm] == true) {
                    val steps = healthConnectManager.getTodaySteps()
                    journalEntryViewModel.syncStepsForToday(steps)
                }

                android.widget.Toast.makeText(this@MainActivity, "✅ Permissions Health Connect accordées", android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            android.widget.Toast.makeText(this@MainActivity, "❌ Permissions refusées.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // --- ACTIVATION DU MODE IMMERSIF (Edge-to-Edge) ---
        // Cela permet au fond d'écran de passer derrière la barre d'état et de navigation
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT), // Barre du haut transparente, icônes claires
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT) // Barre du bas transparente
        )
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(this)
        entryStorage = EntryStorage(database)

        exportCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
            if (uri != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    saveUriToPrefs("saved_csv_uri", uri)
                    writeToUri(uri, csvToExport)

                    // AJOUT : Petit message de confirmation
                    runOnUiThread {
                        android.widget.Toast.makeText(this@MainActivity, "Export CSV terminé !", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        exportJsonLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    saveUriToPrefs("saved_json_uri", uri)
                    if (writeToUri(uri, jsonToExport)) {
                        startCsvExport()
                    }
                }
            }
        }

        setContent {
            NicoTrackerTheme {
                val categoryViewModel: CategoryViewModel = viewModel(factory = CategoryViewModelFactory(database.categoryDao()))
                val subCategoryViewModel: SubCategoryViewModel = viewModel(factory = SubCategoryViewModelFactory(database.subCategoryDao()))
                val avatarViewModel: AvatarViewModel = viewModel(factory = AvatarViewModelFactory(database.avatarDao()))

                healthConnectManager = HealthConnectManager(this)
                val screenTimeManager = remember { ScreenTimeManager(this) }

                val pendingNutrition = remember { mutableStateListOf<RawNutritionItem>() }
                fun launchSmartSync() {
                    lifecycleScope.launch { // <--- DÉBUT DE LA COROUTINE UNIQUE

                        // --- PARTIE 1 : HEALTH CONNECT (Pas & Nutrition) ---
                        if (healthConnectManager.isHealthConnectAvailable()) {
                            if (healthConnectManager.hasPermissions()) {
                                val today = java.util.Date()
                                val stepsToday = healthConnectManager.getTodaySteps()
                                journalEntryViewModel.syncStepsForToday(stepsToday)

                                val cal = java.util.Calendar.getInstance()
                                cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                                val yesterday = cal.time
                                val stepsYesterday = healthConnectManager.getStepsForDate(yesterday)
                                journalEntryViewModel.syncStepsForDate(stepsYesterday, yesterday)

                                // SYNCHRO NUTRITION
                                // On attend ici aussi que les sous-catégories soient prêtes si besoin
                                var subCats = subCategoryViewModel.allSubCategories.value
                                if (subCats.isEmpty()) {
                                    try {
                                        subCats = subCategoryViewModel.allSubCategories.filter { it.isNotEmpty() }.first()
                                    } catch (e: Exception) { }
                                }

                                fun findId(name: String): Int? = subCats.find { it.name.trim() == name.trim() }?.id

                                // On récupère la liste brute du jour
                                val rawItems = healthConnectManager.getRawDailyNutrition(java.util.Date())

// On récupère les IDs déjà triés (stockés dans le champ 'tags' de tes repas existants)
                                val entries = journalEntryViewModel.allEntries.value
                                val processedIds = entries.filter { it.categoryName == "Repas" && it.tags != null }
                                    .flatMap { it.tags!!.split(",") }

// On n'ajoute que les aliments vraiment nouveaux
                                val newItems = rawItems.filter { item ->
                                    !processedIds.contains(item.id) && !pendingNutrition.any { it.id == item.id }
                                }
                                pendingNutrition.addAll(newItems)
                            }
                        }

                        // --- PARTIE 2 : TEMPS D'ÉCRAN (Maintenant DANS la coroutine) ---
                        if (screenTimeManager.hasPermission()) {

                            // Récupération sécurisée des sous-catégories (suspend function .first() est OK ici)
                            var subCats = subCategoryViewModel.allSubCategories.value
                            if (subCats.isEmpty()) {
                                try {
                                    subCats = subCategoryViewModel.allSubCategories.filter { it.isNotEmpty() }.first()
                                } catch (e: Exception) { }
                            }

                            val socialId = subCats.find {
                                it.name.trim().equals("Réseaux Sociaux", ignoreCase = true)
                            }?.id

                            if (socialId != null) {
                                // Mise à jour Hier
                                val instaTimeYesterday = screenTimeManager.getInstagramTimeYesterday()
                                val cal = java.util.Calendar.getInstance()
                                cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
                                val dateYesterday = cal.time
                                journalEntryViewModel.syncScreenTime(dateYesterday, socialId, instaTimeYesterday)

                                // Mise à jour Aujourd'hui
                                val instaTimeToday = screenTimeManager.getInstagramTimeToday()
                                journalEntryViewModel.syncScreenTime(java.util.Date(), socialId, instaTimeToday)

                                println("DEBUG SYNC: Instagram ID=$socialId | Hier=${instaTimeYesterday}m | Auj=${instaTimeToday}m")
                            }
                        }

                    } // <--- FIN DE LA COROUTINE UNIQUE
                }

                LaunchedEffect(Unit) {
                    // 1. Gestion des Permissions Health Connect (Prioritaire mais non bloquant pour le reste)
                    if (healthConnectManager.isHealthConnectAvailable() && !healthConnectManager.hasPermissions()) {
                        requestPermissions.launch(
                            arrayOf(
                                androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.StepsRecord::class),
                                androidx.health.connect.client.permission.HealthPermission.getWritePermission(androidx.health.connect.client.records.StepsRecord::class),
                                androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.NutritionRecord::class)
                            )
                        )
                    } else {
                        // 2. Si on a les permissions OU si Health Connect n'est pas dispo,
                        // on lance quand même la synchro (qui contient le Temps d'écran)
                        launchSmartSync()
                    }
                }

                var isRefreshing by remember { mutableStateOf(false) }
                val refreshData: () -> Unit = {
                    lifecycleScope.launch {
                        isRefreshing = true
                        launchSmartSync()
                        journalEntryViewModel.reload()
                        kotlinx.coroutines.delay(1000)
                        isRefreshing = false
                    }
                }

                onEntriesImported = { journalEntryViewModel.reload() }
                onCategoriesImported = { categoryViewModel.reload() }
                onSubCategoriesImported = { subCategoryViewModel.reload() }

                LaunchedEffect(Unit) {
                    categoryViewModel.insertAllIfEmpty(
                        listOf(
                            Category(name = "Sport"), Category(name = "Repas"), Category(name = "Sommeil"),
                            Category(name = "Action productive"), Category(name = "Temps d'écran"),
                            Category(name = "Nombre de pas"), Category(name = "Humeur"),
                            Category(name = "Dépense"), Category(name = "Défis"), Category(name = "Revenus")
                        )
                    )
                }

                NicoTrackerApp(
                    categoryViewModel = categoryViewModel,
                    journalEntryViewModel = journalEntryViewModel,
                    subCategoryViewModel = subCategoryViewModel,
                    avatarViewModel = avatarViewModel,
                    onExportNow = {
                        lifecycleScope.launch(Dispatchers.IO) {
                            jsonToExport = entryStorage.exportAsJson()
                            csvToExport = entryStorage.exportAsCsv()
                            val savedJsonUri = getSavedUri("saved_json_uri")
                            val jsonSuccess = if (savedJsonUri != null) writeToUri(savedJsonUri, jsonToExport) else false
                            if (jsonSuccess) {
                                runOnUiThread { android.widget.Toast.makeText(this@MainActivity, "Sauvegarde mise à jour !", android.widget.Toast.LENGTH_SHORT).show() }
                                startCsvExport()
                            } else {
                                exportJsonLauncher.launch("nico_tracker_backup.json")
                            }
                        }
                    },
                    onImportJson = { importJsonLauncher.launch("application/json") },
                    onReloadEntries = { journalEntryViewModel.reload() },
                    isRefreshing = isRefreshing,
                    onRefresh = refreshData,
                    pendingNutrition = pendingNutrition
                )
            }
        }
    }

    private fun startCsvExport() {
        val savedUri = getSavedUri("saved_csv_uri")

        // On tente d'écrire directement si on a une adresse en mémoire
        val success = if (savedUri != null) {
            writeToUri(savedUri, csvToExport)
        } else {
            false
        }

        // SI on n'avait pas d'adresse OU SI l'écriture a échoué -> On ouvre le sélecteur
        if (!success) {
            exportCsvLauncher.launch("nico_tracker_backup.csv")
        } else {
            // Si l'écriture silencieuse a marché, on prévient quand même que c'est fini
            runOnUiThread {
                android.widget.Toast.makeText(this, "CSV mis à jour avec succès !", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveUriToPrefs(key: String, uri: android.net.Uri) {
        val prefs = getSharedPreferences("NicoTrackerPrefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString(key, uri.toString()).apply()
        try {
            contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun getSavedUri(key: String): android.net.Uri? {
        val prefs = getSharedPreferences("NicoTrackerPrefs", android.content.Context.MODE_PRIVATE)
        val uriString = prefs.getString(key, null) ?: return null
        return android.net.Uri.parse(uriString)
    }

    private fun writeToUri(uri: android.net.Uri, content: String): Boolean {
        return try {
            contentResolver.openOutputStream(uri, "wt")?.use { out -> out.write(content.toByteArray()) }
            true
        } catch (e: Exception) { e.printStackTrace(); false }
    }
}

// -------------------- NAVIGATION & UI ------------------------

sealed class Screen(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Today : Screen("Aujourd'hui", Icons.Filled.Home)
    object History : Screen("Historique", Icons.Filled.List)
    object Stats : Screen("Stats", Icons.Filled.Star)
    object Avatar : Screen("Avatar", Icons.Filled.Person)
    object Settings : Screen("Paramètres", Icons.Filled.Settings)
    object ManageSubCategories : Screen("Gérer sous-catégories", Icons.Filled.Settings)
    object ProductivityAnalytics : Screen("Prod Détails", Icons.Filled.Star)
    object SleepAnalytics : Screen("Sommeil Détails", Icons.Filled.Hotel)
    object SportAnalytics : Screen("Sport Détails", Icons.Filled.FitnessCenter)
    object NutritionAnalytics : Screen("Nutrition Détails", Icons.Filled.Add)
    object ScreenTimeAnalytics : Screen("Écran Détails", Icons.Filled.Star)
    object ExpenseAnalytics : Screen("Dépenses Détails", Icons.Filled.Star)
    object MoodAnalytics : Screen("Humeur Détails", Icons.Filled.Star)
    object StepsAnalytics : Screen("Pas Détails", Icons.Filled.Star)
    object CopySettings : Screen("Config Copy", Icons.Filled.Edit)
}

// --- NOUVEAU COMPOSANT : FOND "TACTICAL GRID" ---
@Composable
fun TacticalGridBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize().background(AppBackgroundColor)) {
        val gridSize = 40.dp.toPx()
        val gridColor = Color.White.copy(alpha = 0.03f) // Lignes très subtiles (3% opacité)

        // Lignes Verticales
        for (x in 0..size.width.toInt() step gridSize.toInt()) {
            drawLine(
                color = gridColor,
                start = Offset(x.toFloat(), 0f),
                end = Offset(x.toFloat(), size.height),
                strokeWidth = 1f
            )
        }

        // Lignes Horizontales
        for (y in 0..size.height.toInt() step gridSize.toInt()) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y.toFloat()),
                end = Offset(size.width, y.toFloat()),
                strokeWidth = 1f
            )
        }

        // Effet Vignette (Coins assombris)
        val brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
            center = center,
            radius = size.minDimension / 0.7f
        )
        drawRect(brush = brush)
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun NicoTrackerApp(
    categoryViewModel: CategoryViewModel,
    journalEntryViewModel: JournalEntryViewModel,
    subCategoryViewModel: SubCategoryViewModel,
    avatarViewModel: AvatarViewModel,
    onExportNow: () -> Unit,
    onImportJson: () -> Unit,
    onReloadEntries: () -> Unit,
    isRefreshing: Boolean,
    pendingNutrition: MutableList<RawNutritionItem>,
    onRefresh: () -> Unit
) {

    var currentScreen by remember { mutableStateOf<Screen>(Screen.Today) }
    var showAddDialog by remember { mutableStateOf(false) }
    var isFullScreenOverlayActive by remember { mutableStateOf(false) }


    BackHandler(enabled = currentScreen !is Screen.Today) {
        currentScreen = when (currentScreen) {
            Screen.SportAnalytics, Screen.NutritionAnalytics, Screen.ExpenseAnalytics,
            Screen.ScreenTimeAnalytics, Screen.SleepAnalytics, Screen.ProductivityAnalytics,
            Screen.MoodAnalytics, Screen.StepsAnalytics -> Screen.Stats
            Screen.ManageSubCategories -> Screen.Settings
            else -> Screen.Today
        }
    }

    val bottomScreens = listOf(Screen.Today, Screen.History, Screen.Stats, Screen.Avatar)
    val detailScreens = listOf(
        Screen.SportAnalytics, Screen.NutritionAnalytics, Screen.ExpenseAnalytics,
        Screen.ScreenTimeAnalytics, Screen.SleepAnalytics, Screen.ProductivityAnalytics,
        Screen.MoodAnalytics, Screen.StepsAnalytics, Screen.ManageSubCategories
    )

    LaunchedEffect(currentScreen) {
        isFullScreenOverlayActive = false
    }

    // ON UTILISE UNE BOX POUR SUPERPOSER LE FOND ET LE SCAFFOLD
    Box(modifier = Modifier.fillMaxSize()) {

        // 1. LE FOND GRILLE (Derrière tout)
        TacticalGridBackground()

        // 2. L'INTERFACE (Scaffold transparent)
        Scaffold(
            containerColor = Color.Transparent, // IMPORTANT : Transparent pour voir la grille
            bottomBar = {


                if (currentScreen !in detailScreens && !isFullScreenOverlayActive) {
                    NavigationBar(
                        containerColor = Color(0xCC121212)
                    ) {
                        bottomScreens.forEach { screen ->
                            NavigationBarItem(
                                selected = (screen == currentScreen),
                                onClick = { currentScreen = screen },
                                icon = { Icon(screen.icon, contentDescription = screen.label) },
                                label = { Text(screen.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                if (currentScreen is Screen.Today) {
                    FloatingActionButton(onClick = { showAddDialog = true }, containerColor = Color(0xFF00E5FF)) {
                        Icon(Icons.Filled.Add, contentDescription = "Ajouter", tint = Color.Black)
                    }
                }
            }
        ) { padding ->

            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    val isEnteringDetail = targetState in detailScreens && initialState !in detailScreens
                    val isExitingDetail = initialState in detailScreens && targetState !in detailScreens

                    if (isEnteringDetail) {
                        (scaleIn(initialScale = 0.85f, animationSpec = tween(350)) + fadeIn(tween(350)))
                            .togetherWith(scaleOut(targetScale = 1.1f, animationSpec = tween(350)) + fadeOut(tween(300)))
                    } else if (isExitingDetail) {
                        (scaleIn(initialScale = 1.1f, animationSpec = tween(350)) + fadeIn(tween(350)))
                            .togetherWith(scaleOut(targetScale = 0.85f, animationSpec = tween(350)) + fadeOut(tween(300)))
                    } else {
                        fadeIn(tween(200)).togetherWith(fadeOut(tween(200)))
                    }
                },
                label = "ScreenTransition",
                modifier = Modifier.padding(padding)
            ) { targetScreen ->

                when (targetScreen) {
                    Screen.Today -> TodayScreen(
                        categoryViewModel = categoryViewModel,
                        journalEntryViewModel = journalEntryViewModel,
                        subCategoryViewModel = subCategoryViewModel,
                        avatarViewModel = avatarViewModel,
                        showAddDialog = showAddDialog,
                        onDismissDialog = { showAddDialog = false },
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        modifier = Modifier.background(Color.Transparent),
                        onNavigateToSettings = { currentScreen = Screen.Settings },
                        pendingNutrition = pendingNutrition
                    )

                    Screen.Avatar -> AvatarScreen(
                        avatarViewModel = avatarViewModel
                    )

                    Screen.History -> HistoryScreen(
                        journalEntryViewModel = journalEntryViewModel,
                        categoryViewModel = categoryViewModel,
                        subCategoryViewModel = subCategoryViewModel,
                        modifier = Modifier.background(Color.Transparent)
                    )

                    Screen.Stats -> StatsDashboardScreen(
                        journalEntryViewModel = journalEntryViewModel,
                        subCategoryViewModel = subCategoryViewModel,
                        categoryViewModel = categoryViewModel,
                        onNavigateToSport = { currentScreen = Screen.SportAnalytics },
                        onNavigateToNutrition = { currentScreen = Screen.NutritionAnalytics },
                        onNavigateToProductivity = { currentScreen = Screen.ProductivityAnalytics },
                        onNavigateToSleep = { currentScreen = Screen.SleepAnalytics },
                        onNavigateToScreenTime = { currentScreen = Screen.ScreenTimeAnalytics },
                        onNavigateToExpense = { currentScreen = Screen.ExpenseAnalytics },
                        onNavigateToMood = { currentScreen = Screen.MoodAnalytics },
                        onNavigateToSteps = { currentScreen = Screen.StepsAnalytics },
                        onToggleFullScreen = { isFull -> isFullScreenOverlayActive = isFull },
                        modifier = Modifier.background(Color.Transparent)
                    )

                    Screen.Settings -> SettingsScreen(
                        onManageSubCategories = { currentScreen = Screen.ManageSubCategories },
                        onExportNow = onExportNow,
                        onImportJson = { onImportJson(); onReloadEntries() },
                        onConfigureCopywriting = { currentScreen = Screen.CopySettings }
                    )

                    Screen.ManageSubCategories -> ManageSubCategoriesScreen(
                        categoryViewModel = categoryViewModel,
                        subCategoryViewModel = subCategoryViewModel,
                        onBack = { currentScreen = Screen.Settings }
                    )

                    Screen.CopySettings -> CopywritingSettingsScreen(onBack = { currentScreen = Screen.Settings })

                    Screen.SportAnalytics -> SportAnalyticsScreen(journalEntryViewModel, subCategoryViewModel, Color(0xFFFF8C00)) { currentScreen = Screen.Stats }
                    Screen.NutritionAnalytics -> NutritionAnalyticsScreen(journalEntryViewModel, subCategoryViewModel) { currentScreen = Screen.Stats }
                    Screen.ProductivityAnalytics -> ProductivityAnalyticsScreen(journalEntryViewModel, subCategoryViewModel) { currentScreen = Screen.Stats }
                    Screen.SleepAnalytics -> SleepAnalyticsScreen(journalEntryViewModel, subCategoryViewModel) { currentScreen = Screen.Stats }
                    Screen.ScreenTimeAnalytics -> ScreenTimeAnalyticsScreen(journalEntryViewModel, subCategoryViewModel) { currentScreen = Screen.Stats }
                    Screen.ExpenseAnalytics -> ExpenseAnalyticsScreen(journalEntryViewModel, subCategoryViewModel) { currentScreen = Screen.Stats }
                    Screen.MoodAnalytics -> MoodAnalyticsScreen(journalEntryViewModel) { currentScreen = Screen.Stats }
                    Screen.StepsAnalytics -> StepAnalyticsScreen(journalEntryViewModel) { currentScreen = Screen.Stats }
                }
            }
        }
    }
}