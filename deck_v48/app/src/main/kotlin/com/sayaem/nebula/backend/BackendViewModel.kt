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
        // NOTE: Anonymous sign-in is intentionally DISABLED here.
        // The anonymous→Google linkWithCredential path causes DEVELOPER_ERROR (10)
        // when the anonymous session is stale or the Google account was already linked
        // to a different UID. We skip anonymous auth entirely and go straight to
        // Google sign-in when the user requests it.
        // Uncomment below ONLY if you need anonymous UID for analytics before sign-in.
        // if (!DeckBackend.isSignedIn) {
        //     viewModelScope.launch { DeckBackend.signInAnonymously() }
        // }
    }

    // ── Google Sign-In ────────────────────────────────────────────────
    // NOTE: You need to add a Web OAuth client in Firebase Console first:
    // Authentication → Sign-in method → Google → enable → copy Web client ID
    // Then add to strings.xml: <string name="default_web_client_id">YOUR_WEB_CLIENT_ID</string>
    // activity must be the calling Activity — GoogleSignIn.getClient() requires
    // an Activity context, NOT Application context (causes ClassCastException otherwise)
    // Kept as a reference — actual launch is now done via launchGoogleSignIn
    fun getGoogleSignInIntent(activity: android.app.Activity): Intent {
        val webClientId = try {
            activity.getString(R.string.default_web_client_id)
        } catch (_: android.content.res.Resources.NotFoundException) {
            _message.value = "Config error: google-services.json missing type-3 client. " +
                "Re-download it from Firebase Console."
            return GoogleSignIn.getClient(
                activity, GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
            ).signInIntent
        }
        // FIX: sign out from GMS first to clear any stale cached state that causes error 10
        // on repeated sign-in attempts. This is required when a previous attempt failed.
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()
        val client = GoogleSignIn.getClient(activity, gso)
        // Eagerly sign out so GMS doesn't serve a cached error-10 state.
        // The callback is fire-and-forget; we don't need to wait for it.
        client.signOut()
        return client.signInIntent
    }

    fun handleGoogleSignInResult(result: ActivityResult) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                // Always call getSignedInAccountFromIntent regardless of resultCode.
                // When GMS fails it returns resultCode=0 but the intent still carries
                // the ApiException. Checking only resultCode swallows all GMS errors silently.
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)

                val idToken = account?.idToken
                if (idToken == null) {
                    _message.value = "Sign-in failed: no ID token received.\n" +
                        "Did you re-download google-services.json after adding SHA-1?"
                    return@launch
                }

                val res = DeckBackend.signInWithGoogle(idToken)
                if (res.isSuccess) {
                    refreshPremium()
                    pullCloudData()
                    _message.value = "Signed in as ${res.getOrNull()?.displayName ?: "user"} ✓"
                } else {
                    _message.value = "Firebase sign-in failed. Check SHA-1 in Firebase Console."
                }

            } catch (e: ApiException) {
                // Map every known GMS status code to a human-readable, actionable message
                _message.value = when (e.statusCode) {
                    // 12501 = user pressed Back on the account picker — truly silent
                    12501 -> null

                    // 12502 = sign-in interrupted (activity destroyed mid-flow)
                    12502 -> "Sign-in interrupted. Please try again."

                    // 7 = NETWORK_ERROR — GMS could not reach Google's servers.
                    // Your logcat shows exactly this: AuthPII getToken() -> NETWORK_ERROR
                    // Causes: (a) no internet, (b) stale google-services.json after SHA-1 was added
                    7 -> "Sign-in failed: network error.\n" +
                         "1. Check internet connection.\n" +
                         "2. Re-download google-services.json from Firebase Console " +
                         "after adding SHA-1, then rebuild."

                    // 10 = DEVELOPER_ERROR from GMS (thrown before Firebase is reached).
                    // Most common causes in order:
                    // 1. OAuth consent screen not configured in Google Cloud Console
                    //    (separate from Firebase Console — many developers miss this)
                    // 2. SHA-1 fingerprint mismatch
                    // 3. Package name wrong in Firebase
                    10 -> "Sign-in error 10. Fix: Go to " +
                          "console.cloud.google.com → APIs & Services → " +
                          "OAuth consent screen → configure it (app name + support email) " +
                          "→ set to External → Save. " +
                          "This is separate from Firebase Console."

                    // 4 = SIGN_IN_CANCELLED — cancelled programmatically or timed out
                    4 -> "Sign-in cancelled. Try again."

                    // 8 = INTERNAL_ERROR — Play Services internal issue
                    8 -> "Google Play Services internal error. Restart the app and try again."

                    else -> "Sign-in error (code ${e.statusCode}). Try again."
                }
            } catch (e: Exception) {
                val msg = e.message ?: "unknown"
                _message.value = when {
                    // Null intent — user pressed Back before picking account
                    msg.contains("null", ignoreCase = true) -> null

                    msg.contains("NETWORK_ERROR", ignoreCase = true) ->
                        "Sign-in network error. Check internet & ensure google-services.json " +
                        "was re-downloaded after adding SHA-1 to Firebase."

                    else -> "Sign-in failed: $msg"
                }
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
