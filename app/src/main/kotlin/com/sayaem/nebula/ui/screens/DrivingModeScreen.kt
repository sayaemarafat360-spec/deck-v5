package com.sayaem.nebula.ui.screens

import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.sayaem.nebula.data.models.PlaybackState
import com.sayaem.nebula.ui.theme.*
import com.sayaem.nebula.ui.theme.LocalAppColors

@Composable
fun DrivingModeScreen(
    state: PlaybackState,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onExit: () -> Unit,
) {
    val song = state.currentSong

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF050510), LocalAppColors.current.bg)))
    ) {
        // Exit button — top right, small
        TextButton(
            onClick = onExit,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).statusBarsPadding()
        ) {
            Icon(Icons.Filled.ExitToApp, null, tint = LocalAppColors.current.textTertiary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Exit", style = MaterialTheme.typography.labelMedium, color = LocalAppColors.current.textTertiary)
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Song info — very large, readable at a glance
            Icon(Icons.Filled.MusicNote, null, tint = NebulaViolet,
                modifier = Modifier.size(72.dp).clip(CircleShape)
                    .background(NebulaViolet.copy(0.15f)).padding(16.dp))

            Spacer(Modifier.height(28.dp))

            Text(
                song?.title ?: "Nothing playing",
                style = MaterialTheme.typography.displaySmall,
                color = LocalAppColors.current.textPrimary, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            Text(
                song?.artist ?: "Tap a song to play",
                style = MaterialTheme.typography.headlineSmall,
                color = LocalAppColors.current.textSecondary, textAlign = TextAlign.Center, maxLines = 1
            )

            Spacer(Modifier.height(48.dp))

            // Progress bar — thick, easy to see
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = NebulaViolet, trackColor = LocalAppColors.current.border
            )

            Spacer(Modifier.height(48.dp))

            // Giant controls — designed for one-tap while driving
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous — large
                Box(
                    Modifier.size(88.dp).clip(CircleShape)
                        .background(LocalAppColors.current.card).border(1.dp, LocalAppColors.current.border, CircleShape)
                        .clickable(onClick = onPrev),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.SkipPrevious, null, tint = LocalAppColors.current.textPrimary,
                        modifier = Modifier.size(44.dp))
                }

                // Play/Pause — biggest button on screen
                Box(
                    Modifier.size(120.dp).clip(CircleShape)
                        .background(Brush.radialGradient(listOf(NebulaViolet, NebulaPink.copy(0.8f))))
                        .clickable(onClick = onTogglePlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        null, tint = Color.White, modifier = Modifier.size(60.dp)
                    )
                }

                // Next — large
                Box(
                    Modifier.size(88.dp).clip(CircleShape)
                        .background(LocalAppColors.current.card).border(1.dp, LocalAppColors.current.border, CircleShape)
                        .clickable(onClick = onNext),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.SkipNext, null, tint = LocalAppColors.current.textPrimary,
                        modifier = Modifier.size(44.dp))
                }
            }

            Spacer(Modifier.height(32.dp))

            // Driving mode indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DirectionsCar, null, tint = NebulaGreen, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Driving Mode", style = MaterialTheme.typography.labelSmall, color = NebulaGreen)
            }
        }
    }
}
