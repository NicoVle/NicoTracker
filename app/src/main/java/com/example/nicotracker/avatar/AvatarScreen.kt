package com.example.nicotracker.avatar

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.nicotracker.* // Pour NeonCyan, NeonRed, NeonGreen etc.
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

val NeonYellow = Color(0xFFFFEE00)
val NeonBolt = Color(0xFFFFFFAA) // Blanc jaunâtre pour les éclairs

// Enum pour définir le style de la barre
enum class BarStyle {
    LIQUID,   // Vagues + Bulles (HP)
    ELECTRIC  // Grésillement + Éclairs (SP)
}

@Composable
fun AvatarScreen(avatarViewModel: AvatarViewModel) {
    val avatarState by avatarViewModel.avatarState.collectAsState()
    val currentHP = avatarState.currentHp
    val currentSP = avatarState.currentSp

    var showCodex by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {

        // 1. L'AVATAR
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 60.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            GeometricAvatarCorrected(
                hpPercentage = currentHP / 100f,
                spPercentage = currentSP / 100f
            )
        }

        // 2. LE HUD DU HAUT
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 50.dp, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // BARRE HP -> STYLE LIQUIDE
            AdvancedTacticalBar(
                label = "HP STRUCTURE",
                value = currentHP,
                maxValue = 100f,
                mainColor = NeonGreen,
                style = BarStyle.LIQUID,
                isCritical = currentHP < 30
            )

            // BARRE SP -> STYLE ÉLECTRIQUE
            AdvancedTacticalBar(
                label = "SP ÉNERGIE",
                value = currentSP,
                maxValue = 100f,
                mainColor = NeonYellow,
                style = BarStyle.ELECTRIC,
                capValue = currentHP
            )
        }

        // 3. LE HUD DU BAS-DROITE
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text("NIVEAU 29", color = NeonCyan, fontWeight = FontWeight.Black, fontSize = 14.sp)
            Text("OP. NICO", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)

            Spacer(modifier = Modifier.height(16.dp))

            IconButton(
                onClick = { showCodex = true },
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .size(48.dp)
                    .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.MenuBook, contentDescription = "Codex", tint = NeonCyan, modifier = Modifier.size(24.dp))
            }
        }

        if (showCodex) {
            CodexDialog(onDismiss = { showCodex = false })
        }
    }
}

// --- DONNÉES D'ANIMATION ---
data class BubbleData(val x: Float, val y: Float, val size: Float, val speed: Float)
data class BoltData(val startX: Float, val startY: Float, val segments: List<Offset>)

// --- LE CŒUR DU SYSTÈME : LA BARRE AVANCÉE ---
@Composable
fun AdvancedTacticalBar(
    label: String,
    value: Float, // <--- C'est ce paramètre qui créait le conflit
    maxValue: Float,
    mainColor: Color,
    style: BarStyle,
    capValue: Float = maxValue,
    isCritical: Boolean = false
) {
    val barHeight = 26.dp
    val activeColor = if (isCritical) NeonRed else mainColor

    // --- ANIMATIONS ---

    // 1. Pour les vagues (Liquid)
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing), // Vague assez rapide
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    // 2. Pour le grésillement électrique (Electric)
    val flicker by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(100, easing = FastOutSlowInEasing), // Très rapide (10hz)
            repeatMode = RepeatMode.Reverse
        ),
        label = "flicker"
    )

    // --- OBJETS INTERNES (Bulles ou Éclairs) ---
    val bubbles = remember { List(10) { BubbleData(Random.nextFloat(), Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }

    // Pour les éclairs, on regénère la liste périodiquement
    val time by produceState(0L) {
        while (true) {
            delay(150)
            // CORRECTION ICI : On utilise "this.value" pour cibler l'état et non le paramètre "value"
            this.value = System.currentTimeMillis()
        }
    }

    val bolts = remember(time) {
        if (style == BarStyle.ELECTRIC) {
            List(3) {
                val startX = Random.nextFloat()
                val startY = 0.5f // Milieu
                val segs = List(4) {
                    Offset(Random.nextFloat() * 0.1f, (Random.nextFloat() - 0.5f) * 0.4f)
                }
                BoltData(startX, startY, segs)
            }
        } else emptyList()
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // LABELS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = activeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("${value.toInt()} / ${maxValue.toInt()}", color = Color.LightGray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(6.dp))

        // DESSIN CANVAS
        androidx.compose.foundation.Canvas(modifier = Modifier.height(barHeight).fillMaxWidth()) {
            val radius = size.height / 2
            val capsulePath = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(
                    left = 0f, top = 0f, right = size.width, bottom = size.height,
                    cornerRadius = CornerRadius(radius)
                ))
            }

            // ON COUPE TOUT DANS LA CAPSULE
            clipPath(path = capsulePath) {

                // A. FOND SOMBRE
                drawRect(color = Color(0xFF0F0F0F))

                // B. ZONE CONTAMINÉE (HP LIMITANT SP)
                if (capValue < maxValue) {
                    val capWidth = size.width * (capValue / maxValue)
                    drawRect(
                        color = Color(0xFF2B0000),
                        topLeft = Offset(capWidth, 0f),
                        size = Size(size.width - capWidth, size.height)
                    )
                    // Hachures
                    var x = capWidth
                    while(x < size.width + size.height) {
                        drawLine(color = Color.Black.copy(alpha = 0.5f), start = Offset(x, -10f), end = Offset(x - size.height, size.height + 10f), strokeWidth = 8f)
                        x += 20f
                    }
                }

                // C. REMPLISSAGE (LE CŒUR DE L'EFFET)
                val effectiveValue = value.coerceAtMost(capValue)
                val fillWidth = size.width * (effectiveValue / maxValue)

                if (fillWidth > 0) {

                    if (style == BarStyle.LIQUID) {
                        // --- STYLE 1 : LIQUIDE (VAGUE) ---

                        // Fond simple
                        drawRect(
                            color = activeColor.copy(alpha = 0.5f),
                            size = Size(fillWidth, size.height)
                        )

                        // Bulles
                        bubbles.forEachIndexed { index, bubble ->
                            val animatedX = (bubble.x + (wavePhase / 20f * bubble.speed)) % 1f
                            val wavyY = bubble.y + (sin(wavePhase + index) * 0.05f).toFloat()

                            val bx = animatedX * fillWidth
                            val by = (wavyY % 1f) * size.height

                            drawCircle(
                                color = activeColor.copy(alpha = 0.8f),
                                radius = bubble.size * 5f + 2f,
                                center = Offset(bx, by),
                                blendMode = BlendMode.Screen
                            )
                        }

                        // Vague de Surface (Bord Droit)
                        val surfacePath = Path()
                        val surfaceX = fillWidth
                        surfacePath.moveTo(surfaceX, 0f)
                        val wavePoints = 10
                        for (i in 0..wavePoints) {
                            val yRatio = i / wavePoints.toFloat()
                            val y = yRatio * size.height
                            val xOffset = sin((yRatio * 10f) + wavePhase) * 4.dp.toPx()
                            surfacePath.lineTo(surfaceX + xOffset, y)
                        }
                        surfacePath.lineTo(0f, size.height)
                        surfacePath.lineTo(0f, 0f)
                        surfacePath.close()

                        drawPath(path = surfacePath, color = activeColor.copy(alpha = 0.3f))

                    } else {
                        // --- STYLE 2 : ÉLECTRIQUE (GRÉSILLEMENT) ---

                        // Fond clignotant
                        val electricAlpha = 0.4f + (flicker * 0.3f)
                        drawRect(
                            color = activeColor.copy(alpha = electricAlpha),
                            size = Size(fillWidth, size.height)
                        )

                        // Éclairs
                        clipRect(right = fillWidth) {
                            bolts.forEach { bolt ->
                                val path = Path()
                                val boltX = (bolt.startX * fillWidth).coerceAtLeast(10f)
                                val boltY = bolt.startY * size.height

                                path.moveTo(boltX, boltY)
                                var currentX = boltX
                                var currentY = boltY

                                bolt.segments.forEach { offset ->
                                    currentX += offset.x * 200f
                                    currentY += offset.y * 50f
                                    path.lineTo(currentX, currentY)
                                }

                                drawPath(
                                    path = path,
                                    color = NeonBolt,
                                    style = Stroke(width = 2f)
                                )
                                drawPath(
                                    path = path,
                                    color = activeColor,
                                    style = Stroke(width = 4f, cap = StrokeCap.Round),
                                    alpha = 0.5f
                                )
                            }
                        }
                    }
                }

                // 4. REFLET TUBE
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent),
                        startY = 0f, endY = size.height * 0.5f
                    )
                )
            }

            // 5. BORDURE
            drawRoundRect(
                color = Color.DarkGray.copy(alpha = 0.6f),
                style = Stroke(width = 2f),
                size = Size(size.width, size.height),
                cornerRadius = CornerRadius(radius)
            )
        }
    }
}

// --- AVATAR GÉOMÉTRIQUE (Inchangé) ---
@Composable
fun GeometricAvatarCorrected(
    hpPercentage: Float,
    spPercentage: Float,
    modifier: Modifier = Modifier
) {
    val canvasHeight = 700.dp
    val canvasWidth = 350.dp
    val outlineColor = Color.White.copy(alpha = 0.9f)
    val energyColor = NeonYellow.copy(alpha = 0.5f + spPercentage * 0.5f)

    Box(
        modifier = modifier
            .height(canvasHeight)
            .width(canvasWidth),
        contentAlignment = Alignment.BottomCenter
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2
            val bottomY = size.height
            val strokeStyle = Stroke(width = 7f)
            val legLength = 330f
            val torsoLength = 260f
            val headRadius = 55f
            val hipY = bottomY - legLength
            val shoulderY = hipY - torsoLength
            val headY = shoulderY - headRadius - 15f

            drawLine(color = outlineColor, start = Offset(cx - 70f, bottomY), end = Offset(cx - 50f, hipY), strokeWidth = 9f)
            drawLine(color = outlineColor, start = Offset(cx + 70f, bottomY), end = Offset(cx + 50f, hipY), strokeWidth = 9f)

            val torsoPath = Path().apply {
                moveTo(cx - 60f, hipY)
                lineTo(cx + 60f, hipY)
                lineTo(cx + 95f, shoulderY)
                lineTo(cx - 95f, shoulderY)
                close()
            }
            drawPath(path = torsoPath, color = outlineColor, style = strokeStyle)

            val coreColor = if (hpPercentage > 0.5f) NeonCyan else NeonRed
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(coreColor, coreColor.copy(alpha = 0f)),
                    center = Offset(cx, hipY - torsoLength/2),
                    radius = 60f * hpPercentage + 20f
                ),
                center = Offset(cx, hipY - torsoLength/2),
                radius = 50f
            )

            drawCircle(color = outlineColor, center = Offset(cx, headY), radius = headRadius, style = strokeStyle)
            drawArc(
                color = energyColor,
                topLeft = Offset(cx - headRadius, headY - headRadius),
                size = Size(headRadius*2, headRadius*2),
                startAngle = 0f,
                sweepAngle = -180f,
                useCenter = false,
                style = Stroke(width = 8f)
            )

            drawLine(color = outlineColor, start = Offset(cx - 95f, shoulderY + 25f), end = Offset(cx - 140f, shoulderY + 240f), strokeWidth = 8f)
            drawLine(color = outlineColor, start = Offset(cx + 95f, shoulderY + 25f), end = Offset(cx + 140f, shoulderY + 240f), strokeWidth = 8f)
        }
    }
}