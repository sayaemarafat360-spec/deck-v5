package com.sayaem.nebula.backend

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.sayaem.nebula.data.models.Playlist
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object DeckBackend {

    private val auth    get() = FirebaseAuth.getInstance()
    private val db      get() = FirebaseFirestore.getInstance()
    private val config  get() = FirebaseRemoteConfig.getInstance()
    private val fcm     get() = FirebaseMessaging.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser
    val uid: String?               get() = auth.currentUser?.uid
    val isSignedIn: Boolean        get() = uid != null

    // ── Auth state as Flow ────────────────────────────────────────────
    val authState: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    // ── Google Sign-In ────────────────────────────────────────────────
    // NOTE: Requires Web Client OAuth ID in Firebase console
    // Go to: Firebase Console → Authentication → Sign-in method → Google →
    // copy the "Web client ID" and set it in strings.xml as default_web_client_id
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result     = auth.signInWithCredential(credential).await()
            val user       = result.user ?: throw Exception("Sign-in failed")
            createOrUpdateUserDoc(user)
            saveFcmToken()
            Result.success(user)
        } catch (e: Exception) { Result.failure(e) }
    }

    // ── Sign in anonymously (no account needed, still gets UID) ───────
    suspend fun signInAnonymously(): Result<FirebaseUser> {
        return try {
            val result = auth.signInAnonymously().await()
            val user   = result.user ?: throw Exception("Anonymous sign-in failed")
            createOrUpdateUserDoc(user)
            saveFcmToken()
            Result.success(user)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun signOut() { auth.signOut() }

    // ── User document ─────────────────────────────────────────────────
    private suspend fun createOrUpdateUserDoc(user: FirebaseUser) {
        val data = hashMapOf(
            "email"       to (user.email ?: ""),
            "displayName" to (user.displayName ?: "Anonymous"),
            "photoUrl"    to (user.photoUrl?.toString() ?: ""),
            "isAnonymous" to user.isAnonymous,
            "lastSeen"    to com.google.firebase.Timestamp.now(),
        )
        db.collection("users").document(user.uid)
            .set(data, SetOptions.merge()).await()
    }

    // ── Premium ───────────────────────────────────────────────────────
    suspend fun getPremiumStatus(): PremiumStatus? {
        val uid = uid ?: return null
        return try {
            val doc     = db.collection("users").document(uid).get().await()
            val premium = doc.get("premium") as? Map<*, *> ?: return null
            PremiumStatus(
                plan        = premium["plan"] as? String ?: "none",
                expiresAt   = (premium["expiresAt"] as? com.google.firebase.Timestamp)
                    ?.toDate()?.time ?: 0L,
                purchasedAt = (premium["purchasedAt"] as? com.google.firebase.Timestamp)
                    ?.toDate()?.time ?: 0L,
            )
        } catch (_: Exception) { null }
    }

    suspend fun savePremiumStatus(plan: String): Boolean {
        val uid = uid ?: return false
        return try {
            val expiresAt = when (plan) {
                "monthly"  -> System.currentTimeMillis() + 30L  * 86_400_000
                "yearly"   -> System.currentTimeMillis() + 365L * 86_400_000
                "lifetime" -> 4_102_444_800_000L // year 2100
                else       -> 0L
            }
            val data = mapOf(
                "premium" to mapOf(
                    "plan"        to plan,
                    "purchasedAt" to com.google.firebase.Timestamp.now(),
                    "expiresAt"   to com.google.firebase.Timestamp(expiresAt / 1000, 0),
                )
            )
            db.collection("users").document(uid).set(data, SetOptions.merge()).await()
            true
        } catch (_: Exception) { false }
    }

    fun isPremiumActive(status: PremiumStatus?): Boolean {
        if (status == null) return false
        return status.plan == "lifetime" ||
            (status.plan in listOf("monthly", "yearly") &&
             status.expiresAt > System.currentTimeMillis())
    }

    // ── Favorites sync ────────────────────────────────────────────────
    suspend fun pushFavorites(ids: Set<Long>): Boolean {
        val uid = uid ?: return false
        return try {
            db.collection("users").document(uid)
                .set(mapOf("favorites" to ids.toList()), SetOptions.merge()).await()
            true
        } catch (_: Exception) { false }
    }

    suspend fun pullFavorites(): Set<Long>? {
        val uid = uid ?: return null
        return try {
            val doc = db.collection("users").document(uid).get().await()
            @Suppress("UNCHECKED_CAST")
            (doc.get("favorites") as? List<Long>)?.toSet()
        } catch (_: Exception) { null }
    }

    // ── Playlists sync ────────────────────────────────────────────────
    suspend fun pushPlaylists(playlists: List<Playlist>): Boolean {
        val uid = uid ?: return false
        return try {
            val data = playlists.map { pl ->
                mapOf("id" to pl.id, "name" to pl.name,
                      "songIds" to pl.songIds, "createdAt" to pl.createdAt)
            }
            db.collection("users").document(uid)
                .set(mapOf("playlists" to data), SetOptions.merge()).await()
            true
        } catch (_: Exception) { false }
    }

    suspend fun pullPlaylists(): List<Playlist>? {
        val uid = uid ?: return null
        return try {
            val doc = db.collection("users").document(uid).get().await()
            @Suppress("UNCHECKED_CAST")
            (doc.get("playlists") as? List<Map<String, Any>>)?.map { m ->
                @Suppress("UNCHECKED_CAST")
                Playlist(
                    id        = m["id"] as? String ?: "",
                    name      = m["name"] as? String ?: "",
                    songIds   = (m["songIds"] as? List<Long>) ?: emptyList(),
                    createdAt = m["createdAt"] as? Long ?: 0L,
                )
            }
        } catch (_: Exception) { null }
    }

    // ── FCM token ─────────────────────────────────────────────────────
    suspend fun saveFcmToken() {
        val uid = uid ?: return
        try {
            val token = fcm.token.await()
            db.collection("users").document(uid)
                .set(mapOf("fcmToken" to token), SetOptions.merge()).await()
        } catch (_: Exception) {}
    }

    // ── Remote Config ─────────────────────────────────────────────────
    suspend fun fetchRemoteConfig() {
        try {
            config.setConfigSettingsAsync(
                FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(3600)
                    .build()
            ).await()
            config.setDefaultsAsync(mapOf(
                // Pricing
                "premium_monthly_usd"        to "1.99",
                "premium_yearly_usd"         to "9.99",
                "premium_lifetime_usd"       to "24.99",
                "yearly_savings_pct"         to "58",
                "lifetime_equivalent_years"  to "2.5",
                // Feature flags
                "eq_premium_only"            to false,
                "smart_skip_enabled"         to true,
                "visualizer_enabled"         to true,
                "tag_editor_enabled"         to true,
                // Promotions
                "show_promo_banner"          to false,
                "promo_text"                 to "",
                "promo_discount_pct"         to 0L,
                // App behavior
                "force_update_version"       to 0L,
                "min_supported_version"      to 1L,
                "maintenance_mode"           to false,
                "crossfade_max_seconds"      to 10L,
            )).await()
            config.fetchAndActivate().await()
        } catch (_: Exception) {}
    }

    // Config getters — fall back to defaults if fetch fails
    fun getMonthlyPrice()   : String  = config.getString("premium_monthly_usd").ifBlank { "1.99" }
    fun getYearlyPrice()    : String  = config.getString("premium_yearly_usd").ifBlank { "9.99" }
    fun getLifetimePrice()  : String  = config.getString("premium_lifetime_usd").ifBlank { "24.99" }
    fun getYearlySavings()  : String  = config.getString("yearly_savings_pct").ifBlank { "58" }
    fun getLifetimeYears()  : String  = config.getString("lifetime_equivalent_years").ifBlank { "2.5" }
    fun isEqPremiumOnly()   : Boolean = try { config.getBoolean("eq_premium_only") }   catch (_: Exception) { false }
    fun isSmartSkipOn()     : Boolean = try { config.getBoolean("smart_skip_enabled") } catch (_: Exception) { true }
    fun isVisualizerOn()    : Boolean = try { config.getBoolean("visualizer_enabled") } catch (_: Exception) { true }
    fun showPromoBanner()   : Boolean = try { config.getBoolean("show_promo_banner") }  catch (_: Exception) { false }
    fun getPromoText()      : String  = config.getString("promo_text")
    fun getPromoDiscount()  : Long    = try { config.getLong("promo_discount_pct") }    catch (_: Exception) { 0L }
    fun isMaintenanceMode() : Boolean = try { config.getBoolean("maintenance_mode") }   catch (_: Exception) { false }
    fun forceUpdateVersion(): Long    = try { config.getLong("force_update_version") }  catch (_: Exception) { 0L }
}

data class PremiumStatus(
    val plan: String,
    val expiresAt: Long,
    val purchasedAt: Long,
)
