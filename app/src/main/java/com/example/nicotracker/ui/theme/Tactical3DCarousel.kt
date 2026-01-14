package com.example.nicotracker

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.example.nicotracker.data.JournalEntry
import com.example.nicotracker.data.SubCategory
import kotlin.math.absoluteValue

private val LocalCardDarkBg = Color(0xFF1E1E1E)

// --- PALETTE DE COULEURS CYCLIQUES ---
private val TacticalPalette = listOf(
    NeonCyan, NeonOrange, NeonGreen, ProdViolet, NeonPink,
    Color(0xFFFFD700), Color(0xFF00E676), Color(0xFF2979FF)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengeArmoryScreen(
    bankEntries: List<JournalEntry>,
    subCategories: List<SubCategory>,
    onActivate: (JournalEntry) -> Unit,
    onCardClick: (JournalEntry) -> Unit,
    onDeleteConfirm: (JournalEntry) -> Unit, // <--- Paramètre pour la suppression
    onClose: () -> Unit
) {
    // État pour gérer la popup de confirmation de suppression
    var entryToDelete by remember { mutableStateOf<JournalEntry?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize().padding(top = 40.dp)
        ) {
            Text("L'ARMURERIE", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Black, letterSpacing = 4.sp)
            Text("SÉLECTIONNE TA MISSION", style = MaterialTheme.typography.labelMedium, color = NeonCyan)

            Spacer(modifier = Modifier.height(20.dp))

            if (bankEntries.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("Aucun défi en banque.", color = Color.Gray)
                }
            } else {
                Tactical3DCarousel(
                    items = bankEntries,
                    subCategories = subCategories,
                    onItemSelected = { onActivate(it); onClose() },
                    onCardClick = onCardClick,
                    onDeleteRequest = { entryToDelete = it } // <--- On capture la demande de suppression
                )
            }

            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("FERMER", color = Color.Gray) }
            Spacer(modifier = Modifier.height(30.dp))
        }

        // --- DIALOGUE DE CONFIRMATION DE SUPPRESSION ---
        if (entryToDelete != null) {
            NeonHudDialog(
                title = "SUPPRIMER LE PROTOCOLE ?",
                onDismiss = { entryToDelete = null },
                confirmLabel = "DÉTRUIRE",
                onConfirm = {
                    onDeleteConfirm(entryToDelete!!)
                    entryToDelete = null
                }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = (entryToDelete?.challengeTitle ?: "Défi Inconnu").uppercase(),
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Cette action retirera définitivement ce défi de l'Armurerie.\nConfirmer la destruction ?",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tactical3DCarousel(
    items: List<JournalEntry>,
    subCategories: List<SubCategory>,
    onItemSelected: (JournalEntry) -> Unit,
    onCardClick: (JournalEntry) -> Unit,
    onDeleteRequest: (JournalEntry) -> Unit
) {
    val pagerState = rememberPagerState(initialPage = 0) { items.size }
    val density = LocalDensity.current

    HorizontalPager(
        state = pagerState,
        // MODIFICATION TAILLE : Padding réduit à 45.dp pour élargir les cartes
        contentPadding = PaddingValues(horizontal = 45.dp),
        modifier = Modifier.height(650.dp).fillMaxWidth()
    ) { page ->
        val entry = items[page]
        val linkedSubCat = remember(entry.subCategoryId, subCategories) { subCategories.find { it.id == entry.subCategoryId } }

        Box(
            modifier = Modifier
                .zIndex(1f - ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue)
                .graphicsLayer {
                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                    val absOffset = pageOffset.absoluteValue

                    // MODIFICATION ZOOM : Scale monte jusqu'à 1.25x pour agrandir la carte centrale
                    val scale = lerp(start = 0.85f, stop = 1.17f, fraction = 1f - absOffset.coerceIn(0f, 1f))
                    scaleX = scale; scaleY = scale

                    val translationFactor = with(density) { 170.dp.toPx() }
                    translationX = pageOffset * translationFactor
                    alpha = lerp(start = 0.6f, stop = 1f, fraction = 1f - absOffset.coerceIn(0f, 1f))
                    rotationY = -20f * pageOffset.coerceIn(-1f, 1f)
                    rotationX = -15f * absOffset.coerceIn(0f, 1f)
                    cameraDistance = 12 * density.density
                }
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            TacticalCard(
                entry = entry,
                subCategory = linkedSubCat,
                onActivate = { onItemSelected(entry) },
                onClick = { onCardClick(entry) },
                onDelete = { onDeleteRequest(entry) }
            )
        }
    }
}

@Composable
fun TacticalCard(
    entry: JournalEntry,
    subCategory: SubCategory?,
    onActivate: () -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    // Calcul de la couleur basé sur l'ID de la sous-catégorie
    val cardColor = if (subCategory == null) NeonCyan else TacticalPalette[subCategory.id % TacticalPalette.size]
    val categoryLabel = subCategory?.name?.uppercase() ?: "GÉNÉRAL"
    val techId = "CMD-${String.format("%02d", entry.id % 99)}"

    // Formatage Durée (ex: 90 min -> 1h 30min)
    val durationText = entry.challengeDurationMinutes?.let { mins ->
        val h = mins / 60
        val m = mins % 60
        if (h > 0) "${h}H ${m}MIN" else "${m} MIN"
    } ?: "NON DÉFINIE"

    val quantityText = entry.challengeQuantity?.toString() ?: "N/A"
    val commentText = if (entry.comment.isNullOrBlank()) "Aucune description tactique." else entry.comment

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.62f) // Légèrement élargi par rapport au 0.60f original
            .clickable { onClick() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = LocalCardDarkBg),
        border = BorderStroke(2.dp, Brush.verticalGradient(listOf(cardColor, Color.Transparent)))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {

            TechBackground(color = cardColor)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // --- HEADER + TITRE ---
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Label Catégorie
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Label, null, tint = cardColor, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(categoryLabel, color = cardColor, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                        }

                        // ID + BOUTON POUBELLE
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(techId, color = Color.Gray.copy(alpha = 0.5f), fontSize = 10.sp, fontWeight = FontWeight.Black)
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Supprimer",
                                    tint = Color.Gray.copy(alpha = 0.5f) // Discret par défaut
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = (entry.challengeTitle ?: "Défi Mystère").uppercase(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                        lineHeight = 32.sp
                    )

                    Spacer(Modifier.height(8.dp))

                    // Barre de séparation centrale
                    Box(modifier = Modifier.fillMaxWidth(0.6f).height(2.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, cardColor, Color.Transparent))))

                    Spacer(Modifier.height(8.dp))

                    DifficultyBars(level = entry.challengeDifficulty ?: 5, color = cardColor)
                }

                // --- LE DOSSIER MILITAIRE (Aligné Gauche) ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 24.dp),
                    verticalArrangement = Arrangement.Top
                ) {
                    // Durée
                    DossierRow(label = "DURÉE ESTIMÉE", value = durationText, color = cardColor)

                    Spacer(Modifier.height(12.dp))

                    // Quantité
                    DossierRow(label = "OBJECTIF QUANTITÉ", value = quantityText, color = cardColor)

                    Spacer(Modifier.height(12.dp))

                    // Commentaire / Description
                    Text("RAPPORT / NOTES :", color = cardColor, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = commentText,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(cardColor.copy(alpha = 0.2f)))
                }

                // --- BOUTON D'ACTION ---
                Button(
                    onClick = onActivate,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = cardColor.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, cardColor.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Bolt, null, tint = cardColor, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("INITIALISER", color = cardColor, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }
    }
}

// --- COMPOSANT "LIGNE DE DOSSIER" ---
@Composable
fun DossierRow(label: String, value: String, color: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
            Text(text = value, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(color.copy(alpha = 0.3f)))
    }
}

@Composable
fun TechBackground(color: Color) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val gridStep = 40.dp.toPx()

        for (x in 0..width.toInt() step gridStep.toInt()) {
            drawLine(color = Color.White.copy(alpha = 0.03f), start = Offset(x.toFloat(), 0f), end = Offset(x.toFloat(), height), strokeWidth = 1f)
        }
        for (y in 0..height.toInt() step gridStep.toInt()) {
            drawLine(color = Color.White.copy(alpha = 0.03f), start = Offset(0f, y.toFloat()), end = Offset(width, y.toFloat()), strokeWidth = 1f)
        }

        val bracketLength = 20.dp.toPx(); val bracketStroke = 2.dp.toPx(); val bracketColor = color.copy(alpha = 0.4f)
        drawLine(bracketColor, Offset(0f, 0f), Offset(bracketLength, 0f), bracketStroke); drawLine(bracketColor, Offset(0f, 0f), Offset(0f, bracketLength), bracketStroke)
        drawLine(bracketColor, Offset(width, 0f), Offset(width - bracketLength, 0f), bracketStroke); drawLine(bracketColor, Offset(width, 0f), Offset(width, bracketLength), bracketStroke)
        drawLine(bracketColor, Offset(0f, height), Offset(bracketLength, height), bracketStroke); drawLine(bracketColor, Offset(0f, height), Offset(0f, height - bracketLength), bracketStroke)
        drawLine(bracketColor, Offset(width, height), Offset(width - bracketLength, height), bracketStroke); drawLine(bracketColor, Offset(width, height), Offset(width, height - bracketLength), bracketStroke)
    }
}

@Composable
fun DifficultyBars(level: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text("DIFF", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(10) { index ->
                val isActive = index < level
                Box(modifier = Modifier.width(6.dp).height(10.dp).background(if (isActive) color else Color.DarkGray.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
            }
        }
    }
}