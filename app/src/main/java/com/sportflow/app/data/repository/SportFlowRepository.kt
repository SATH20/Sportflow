package com.sportflow.app.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.sportflow.app.data.model.*
import kotlin.math.pow
import kotlin.math.ceil
import kotlin.math.log2
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SportFlowRepository @Inject constructor(
    private val firestore: FirebaseFirestore?,
    private val auth: FirebaseAuth?
) {
    val isFirebaseAvailable: Boolean get() = firestore != null

    // ── Matches ───────────────────────────────────────────────────────────────
    fun getUpcomingMatches(): Flow<List<Match>> {
        val db = firestore ?: return flowOf(emptyList())
        return callbackFlow {
            val listener = db.collection("matches")
                .whereEqualTo("status", MatchStatus.UPCOMING.name)
                .orderBy("scheduledTime", Query.Direction.ASCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val matches = snapshot?.documents?.mapNotNull {
                        it.toObject(Match::class.java)?.copy(id = it.id)
                    } ?: emptyList()
                    trySend(matches)
                }
            awaitClose { listener.remove() }
        }
    }

    fun getLiveMatches(): Flow<List<Match>> {
        val db = firestore ?: return flowOf(emptyList())
        return callbackFlow {
            val listener = db.collection("matches")
                .whereIn("status", listOf(MatchStatus.LIVE.name, MatchStatus.HALFTIME.name))
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val matches = snapshot?.documents?.mapNotNull {
                        it.toObject(Match::class.java)?.copy(id = it.id)
                    } ?: emptyList()
                    trySend(matches)
                }
            awaitClose { listener.remove() }
        }
    }

    fun getMatchById(matchId: String): Flow<Match?> {
        val db = firestore ?: return flowOf(null)
        return callbackFlow {
            val listener = db.collection("matches")
                .document(matchId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val match = snapshot?.toObject(Match::class.java)?.copy(id = snapshot.id)
                    trySend(match)
                }
            awaitClose { listener.remove() }
        }
    }

    fun getAllMatches(): Flow<List<Match>> {
        val db = firestore ?: return flowOf(emptyList())
        return callbackFlow {
            val listener = db.collection("matches")
                .orderBy("scheduledTime", Query.Direction.DESCENDING)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val matches = snapshot?.documents?.mapNotNull {
                        it.toObject(Match::class.java)?.copy(id = it.id)
                    } ?: emptyList()
                    trySend(matches)
                }
            awaitClose { listener.remove() }
        }
    }

    // ── Tournaments ──────────────────────────────────────────────────────────
    fun getTournaments(): Flow<List<Tournament>> {
        val db = firestore ?: return flowOf(emptyList())
        return callbackFlow {
            val listener = db.collection("tournaments")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val tournaments = snapshot?.documents?.mapNotNull {
                        it.toObject(Tournament::class.java)?.copy(id = it.id)
                    } ?: emptyList()
                    trySend(tournaments)
                }
            awaitClose { listener.remove() }
        }
    }

    fun getBracket(tournamentId: String): Flow<List<BracketNode>> {
        val db = firestore ?: return flowOf(emptyList())
        return callbackFlow {
            val listener = db.collection("tournaments")
                .document(tournamentId)
                .collection("bracket")
                .orderBy("round")
                .orderBy("position")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val nodes = snapshot?.documents?.mapNotNull {
                        it.toObject(BracketNode::class.java)?.copy(matchId = it.id)
                    } ?: emptyList()
                    trySend(nodes)
                }
            awaitClose { listener.remove() }
        }
    }

    // ── Payments (Admin) ─────────────────────────────────────────────────────
    fun getPendingPayments(): Flow<List<Payment>> {
        val db = firestore ?: return flowOf(emptyList())
        return callbackFlow {
            val listener = db.collection("payments")
                .whereEqualTo("status", PaymentStatus.PENDING.name)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        close(error)
                        return@addSnapshotListener
                    }
                    val payments = snapshot?.documents?.mapNotNull {
                        it.toObject(Payment::class.java)?.copy(id = it.id)
                    } ?: emptyList()
                    trySend(payments)
                }
            awaitClose { listener.remove() }
        }
    }

    suspend fun verifyPayment(paymentId: String) {
        val db = firestore ?: return
        db.collection("payments")
            .document(paymentId)
            .update("status", PaymentStatus.VERIFIED.name)
            .await()
    }

    suspend fun rejectPayment(paymentId: String) {
        val db = firestore ?: return
        db.collection("payments")
            .document(paymentId)
            .update("status", PaymentStatus.REJECTED.name)
            .await()
    }

    // ── Bracket Generation ───────────────────────────────────────────────────
    suspend fun generateBracket(tournamentId: String) {
        val db = firestore ?: return
        val tournament = db.collection("tournaments")
            .document(tournamentId)
            .get()
            .await()
            .toObject(Tournament::class.java) ?: return

        val teams = tournament.teams.shuffled()
        if (teams.isEmpty()) return
        val totalRounds = ceil(log2(teams.size.toDouble())).toInt()
        val bracketRef = db.collection("tournaments")
            .document(tournamentId)
            .collection("bracket")

        // Clear old bracket
        val oldDocs = bracketRef.get().await()
        for (doc in oldDocs) {
            doc.reference.delete().await()
        }

        // Generate first round
        var position = 0
        for (i in teams.indices step 2) {
            val node = BracketNode(
                round = 1,
                position = position,
                teamA = teams.getOrNull(i),
                teamB = teams.getOrNull(i + 1)
            )
            bracketRef.add(node).await()
            position++
        }

        // Generate empty subsequent rounds
        for (round in 2..totalRounds) {
            val matchesInRound = ceil(teams.size / 2.0.pow(round.toDouble())).toInt()
            for (pos in 0 until matchesInRound) {
                val node = BracketNode(round = round, position = pos)
                bracketRef.add(node).await()
            }
        }

        // Mark bracket as generated
        db.collection("tournaments")
            .document(tournamentId)
            .update("bracketGenerated", true)
            .await()
    }

    // ── Auth ─────────────────────────────────────────────────────────────────
    fun getCurrentUser(): FirebaseUser? = auth?.currentUser

    suspend fun signIn(email: String, password: String) {
        auth?.signInWithEmailAndPassword(email, password)?.await()
            ?: throw Exception("Firebase Auth not available")
    }

    suspend fun signUp(email: String, password: String, name: String) {
        val a = auth ?: throw Exception("Firebase Auth not available")
        val db = firestore ?: throw Exception("Firebase Firestore not available")
        val result = a.createUserWithEmailAndPassword(email, password).await()
        result.user?.let { user ->
            val sportUser = SportUser(
                uid = user.uid,
                email = email,
                displayName = name,
                role = UserRole.PLAYER
            )
            db.collection("users").document(user.uid).set(sportUser).await()
        }
    }

    fun signOut() = auth?.signOut()
}
