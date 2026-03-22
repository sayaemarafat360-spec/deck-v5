package com.sayaem.nebula.backend

import android.app.Application
import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseUser
import com.sayaem.nebula.R
import com.sayaem.nebula.data.local.LocalDataStore
import com.sayaem.nebula.data.models.Playlist
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class BackendViewModel(app: Application) : AndroidViewModel(app) {

    private val store = LocalDataStore(app)

    // ── UI-facing state ───────────────────────────────────────────────
    private val _user       = MutableStateFlow<FirebaseUser?>(DeckBackend.currentUser)
    val user = _user.asStateFlow()

    private val _isPremium  = MutableStateFlow(store.isLocalPremiumActive())
    val isPremium = _isPremium.asStateFlow()

    private val _premiumPlan = MutableStateFlow(store.getLocalPremiumPlan())
    val premiumPlan = _premiumPlan.asStateFlow()

    // Remote Config prices — shown in Premium screen
    private val _prices = MutableStateFlow(PriceConfig())
    val prices = _prices.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    // One-shot messages for Snackbar/Toast
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        // Watch Firebase auth state
        viewModelScope.launch {
            DeckBackend.authState.collect { fbUser ->
                _user.value = fbUser
                if (fbUser != null) {
                    refreshPremium()
                    pullCloudData()
                }
            }
        }

        // Fetch Remote Config in background
        viewModelScope.launch {
            DeckBackend.fetchRemoteConfig()
            _prices.value = PriceConfig(
                monthly         = DeckBackend.getMonthlyPrice(),
                yearly          = DeckBackend.getYearlyPrice(),
                lifetime        = DeckBackend.getLifetimePrice(),
                yearlySavings   = DeckBackend.getYearlySavings(),
                lifetimeYears   = DeckBackend.getLifetimeYears(),
                showPromoBanner = DeckBackend.showPromoBanner(),
                promoText       = DeckBackend.getPromoText(),
                promoDiscount   = DeckBackend.getPromoDiscount(),
            )
        }

        // Sign in anonymously if not signed in — gets a UID for FCM/Firestore
        // without forcing the user to create an account
        if (!DeckBackend.isSignedIn) {
            viewModelScope.launch {
                DeckBackend.signInAnonymously()
            }
        }
    }

    // ── Google Sign-In ────────────────────────────────────────────────
    // NOTE: You need to add a Web OAuth client in Firebase Console first:
    // Authentication → Sign-in method → Google → enable → copy Web client ID
    // Then add to strings.xml: <string name="default_web_client_id">YOUR_WEB_CLIENT_ID</string>
    fun getGoogleSignInIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getApplication<Application>()
                .getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        return GoogleSignIn.getClient(getApplication(), gso).signInIntent
    }

    fun handleGoogleSignInResult(result: ActivityResult) {
        if (result.resultCode != android.app.Activity.RESULT_OK) {
            if (result.resultCode == 0) {
                // resultCode 0 = RESULT_CANCELED = SHA-1 mismatch or user cancelled
                // Show nothing if user just pressed back
            }
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val idToken = account.idToken ?: run {
                    _message.value = "Sign-in failed — no ID token"
                    _isSyncing.value = false
                    return@launch
                }
                val res = DeckBackend.signInWithGoogle(idToken)
                if (res.isSuccess) {
                    refreshPremium()
                    pullCloudData()
                    _message.value = "Signed in as ${res.getOrNull()?.displayName}"
                } else {
                    _message.value = "Sign-in failed — check Firebase SHA-1"
                }
            } catch (e: ApiException) {
                when (e.statusCode) {
                    12501 -> { /* user cancelled — silent */ }
                    10    -> _message.value = "Sign-in error: add SHA-1 to Firebase"
                    else  -> _message.value = "Sign-in error (${e.statusCode})"
                }
            } catch (e: Exception) {
                _message.value = null
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            DeckBackend.signOut()
            _premiumPlan.value = store.getLocalPremiumPlan()
            _isPremium.value   = store.isLocalPremiumActive()
            _message.value     = "Signed out"
        }
    }

    // ── Premium ───────────────────────────────────────────────────────
    private suspend fun refreshPremium() {
        val status = DeckBackend.getPremiumStatus()
        val active = DeckBackend.isPremiumActive(status)
        if (active && status != null) {
            // Save to local so it works offline after this
            store.saveLocalPremium(status.plan, status.expiresAt)
        }
        _isPremium.value   = active || store.isLocalPremiumActive()
        _premiumPlan.value = status?.plan ?: store.getLocalPremiumPlan()
    }

    // Called after successful in-app purchase
    fun grantPremium(plan: String) {
        viewModelScope.launch {
            // Save to Firestore (cloud)
            DeckBackend.savePremiumStatus(plan)
            // Save locally (offline fallback)
            val expiresAt = when (plan) {
                "monthly"  -> System.currentTimeMillis() + 30L  * 86_400_000
                "yearly"   -> System.currentTimeMillis() + 365L * 86_400_000
                "lifetime" -> 4_102_444_800_000L
                else       -> 0L
            }
            store.saveLocalPremium(plan, expiresAt)
            _isPremium.value   = true
            _premiumPlan.value = plan
            _message.value     = "Welcome to Deck Premium! 🎉"
        }
    }

    // ── Cloud sync: push up ───────────────────────────────────────────
    fun syncFavoritesUp(ids: Set<Long>) {
        if (!DeckBackend.isSignedIn) return
        viewModelScope.launch { DeckBackend.pushFavorites(ids) }
    }

    fun syncPlaylistsUp(playlists: List<Playlist>) {
        if (!DeckBackend.isSignedIn) return
        viewModelScope.launch { DeckBackend.pushPlaylists(playlists) }
    }

    // ── Cloud sync: pull down ─────────────────────────────────────────
    private fun pullCloudData(
        onFavorites: ((Set<Long>) -> Unit)? = null,
        onPlaylists: ((List<Playlist>) -> Unit)? = null,
    ) {
        viewModelScope.launch {
            _isSyncing.value = true
            DeckBackend.pullFavorites()?.let { onFavorites?.invoke(it) }
            DeckBackend.pullPlaylists()?.let { onPlaylists?.invoke(it) }
            _isSyncing.value = false
        }
    }

    fun pullAndMerge(
        onFavorites: (Set<Long>) -> Unit,
        onPlaylists: (List<Playlist>) -> Unit,
    ) = pullCloudData(onFavorites, onPlaylists)

    // ── Remote Config feature flags ───────────────────────────────────
    fun isFeatureEnabled(key: String) = when (key) {
        "visualizer"  -> DeckBackend.isVisualizerOn()
        "tag_editor"  -> DeckBackend.isVisualizerOn()
        "smart_skip"  -> DeckBackend.isSmartSkipOn()
        "eq_paywall"  -> DeckBackend.isEqPremiumOnly()
        "maintenance" -> DeckBackend.isMaintenanceMode()
        else          -> true
    }

    fun clearMessage() { _message.value = null }
}

// All price/promo config in one object
data class PriceConfig(
    val monthly         : String  = "1.99",
    val yearly          : String  = "9.99",
    val lifetime        : String  = "24.99",
    val yearlySavings   : String  = "58",
    val lifetimeYears   : String  = "2.5",
    val showPromoBanner : Boolean = false,
    val promoText       : String  = "",
    val promoDiscount   : Long    = 0L,
)
