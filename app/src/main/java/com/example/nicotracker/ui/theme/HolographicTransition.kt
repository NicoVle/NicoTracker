package com.example.nicotracker

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun HolographicReveal(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = isVisible

    val transition = updateTransition(transitionState, label = "PrecisionPopTransition")

    // 1. ÉCHELLE (Le Zoom)
    // De 0.8x (légèrement en retrait) à 1.0x (plein écran)
    val scale by transition.animateFloat(
        transitionSpec = {
            if (targetState) {
                // Ouverture vive : Démarrage rapide, atterrissage précis (250ms)
                tween(durationMillis = 250, easing = LinearOutSlowInEasing)
            } else {
                // Fermeture très rapide
                tween(durationMillis = 200)
            }
        },
        label = "Scale"
    ) { state -> if (state) 1f else 0.8f }

    // 2. OPACITÉ (Le Fade)
    // De 0% à 100%
    val alpha by transition.animateFloat(
        transitionSpec = {
            if (targetState) tween(250) else tween(200)
        },
        label = "Alpha"
    ) { state -> if (state) 1f else 0f }

    // On affiche l'overlay tant qu'il est visible ou en cours d'animation
    if (isVisible || alpha > 0f) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // On bloque les clics pour ne pas interagir avec le dashboard derrière
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {

            // FOND NOIR (Apparaît avec l'opacité)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = alpha))
            )

            // LE CONTENU (L'Armurerie)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Transformation GPU ultra-rapide
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
            ) {
                content()
            }
        }
    }
}