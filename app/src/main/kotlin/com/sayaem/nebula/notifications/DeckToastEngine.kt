package com.sayaem.nebula.notifications

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// ── Toast types ───────────────────────────────────────────────────────────
enum class ToastType { SUCCESS, ERROR, WARNING, INFO }

data class ToastMessage(
    val message: String,
    val type: ToastType = ToastType.SUCCESS,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val durationMs: Long = 2800L,
    val id: Long = System.nanoTime(),
)

// ── Global toast state — single source of truth ───────────────────────────
object DeckToastEngine {

    private val _current = MutableStateFlow<ToastMessage?>(null)
    val current: StateFlow<ToastMessage?> = _current.asStateFlow()

    private var dismissJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── Show helpers — use these everywhere instead of Toast.makeText ─────

    fun success(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) =
        show(ToastMessage(message, ToastType.SUCCESS, actionLabel, onAction))

    fun error(message: String) =
        show(ToastMessage(message, ToastType.ERROR, durationMs = 4000L))

    fun warning(message: String) =
        show(ToastMessage(message, ToastType.WARNING, durationMs = 3500L))

    fun info(message: String) =
        show(ToastMessage(message, ToastType.INFO))

    // Specific action confirmations
    fun favAdded(songTitle: String)    = success("Added to favorites ♥", "Undo")
    fun favRemoved(songTitle: String)  = success("Removed from favorites")
    fun addedToQueue()                 = success("Added to queue")
    fun playingNext()                  = success("Playing next")
    fun addedToPlaylist(name: String)  = success("Added to \"$name\"")
    fun playlistCreated(name: String)  = success("Playlist \"$name\" created")
    fun songDeleted()                  = success("Deleted from device")
    fun ringtoneSet(title: String)     = success("\"$title\" set as ringtone ✓")
    fun tagsSaved()                    = success("Tags saved ✓")
    fun screenshotSaved()              = success("Screenshot saved to Gallery ✓")
    fun sleepTimerSet(mins: Int)       = success("Sleep timer: ${mins}m")
    fun sleepTimerCancelled()          = info("Sleep timer cancelled")
    fun speedChanged(speed: Float)     = info("Speed: ${speed}×")
    fun signedIn(name: String?)        = success("Signed in${name?.let { " as $it" } ?: ""} ✓")
    fun signedOut()                    = info("Signed out")
    fun synced()                       = success("Library synced ✓")
    fun deleteFailed()                 = error("Could not delete file")
    fun shareError()                   = error("Nothing to share right now")
    fun noSubtitles()                  = info("No subtitle tracks in this video")
    fun eqProfileSaved()               = success("EQ profile saved for this song ✓")
    fun copied()                       = success("Copied ✓")

    fun show(toast: ToastMessage) {
        dismissJob?.cancel()
        _current.value = toast
        dismissJob = scope.launch {
            delay(toast.durationMs)
            if (_current.value?.id == toast.id) _current.value = null
        }
    }

    fun dismiss() {
        dismissJob?.cancel()
        _current.value = null
    }
}

// ── Compose overlay — place this at the TOP LEVEL of DeckRoot ─────────────
@Composable
fun DeckToastOverlay() {
    val toast by DeckToastEngine.current.collectAsState()

    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = toast != null,
            enter   = slideInVertically { it / 2 } + fadeIn(tween(200)),
            exit    = slideOutVertically { it / 2 } + fadeOut(tween(180)),
            modifier = Modifier.padding(bottom = 100.dp, start = 16.dp, end = 16.dp)
        ) {
            toast?.let { t -> ToastCard(t) }
        }
    }
}

@Composable
private fun ToastCard(toast: ToastMessage) {
    val (bgColor, iconColor, icon) = when (toast.type) {
        ToastType.SUCCESS -> Triple(Color(0xFF1A2A1A), Color(0xFF4CAF50), Icons.Filled.CheckCircle)
        ToastType.ERROR   -> Triple(Color(0xFF2A1A1A), Color(0xFFE53935), Icons.Filled.Error)
        ToastType.WARNING -> Triple(Color(0xFF2A221A), Color(0xFFFFA726), Icons.Filled.Warning)
        ToastType.INFO    -> Triple(Color(0xFF1A1A2A), Color(0xFF42A5F5), Icons.Filled.Info)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(0.5.dp, iconColor.copy(0.3f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))

        Text(
            toast.message,
            style     = MaterialTheme.typography.bodyMedium,
            color     = Color.White,
            fontWeight = FontWeight.Medium,
            modifier  = Modifier.weight(1f)
        )

        if (toast.actionLabel != null && toast.onAction != null) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconColor.copy(0.18f))
                    .clickable { toast.onAction.invoke(); DeckToastEngine.dismiss() }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(toast.actionLabel, style = MaterialTheme.typography.labelMedium,
                    color = iconColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
