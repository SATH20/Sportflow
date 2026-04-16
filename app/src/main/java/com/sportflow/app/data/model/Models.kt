package com.sportflow.app.data.model

import com.google.firebase.Timestamp

// ── GNITS-Specific Constants ────────────────────────────────────────────────

/** All 8 departments of GNITS */
enum class GnitsDepartment(val displayName: String) {
    CSE("Computer Science & Engineering"),
    ECE("Electronics & Communication Engineering"),
    IT("Information Technology"),
    EEE("Electrical & Electronics Engineering"),
    ETM("Electronics & Telematics Engineering"),
    CSM("Computer Science & Engineering (AI & ML)"),
    CSD("Computer Science & Engineering (Data Science)"),
    AIDS("Artificial Intelligence & Data Science");

    companion object {
        fun fromCode(code: String): GnitsDepartment? =
            entries.find { it.name.equals(code, ignoreCase = true) }

        val allCodes: List<String> get() = entries.map { it.name }
    }
}

/** Actual GNITS campus venues */
enum class GnitsVenue(val displayName: String) {
    MAIN_GROUND("Main Ground"),
    BADMINTON_STADIUM("Badminton Stadium"),
    BASKETBALL_COURT("Basketball Court"),
    INDOOR_SPORTS_ROOM("Indoor Sports Room");

    companion object {
        val allNames: List<String> get() = entries.map { it.displayName }
    }
}

// ── Data Classes ────────────────────────────────────────────────────────────

data class Match(
    val id: String = "",
    val sportType: String = "",
    val teamA: String = "",
    val teamB: String = "",
    val teamADepartment: String = "",
    val teamBDepartment: String = "",
    val teamALogo: String = "",
    val teamBLogo: String = "",
    val scoreA: Int = 0,
    val scoreB: Int = 0,
    val status: MatchStatus = MatchStatus.SCHEDULED,
    val venue: String = "",
    val scheduledTime: Timestamp? = null,
    val currentPeriod: String = "",
    val elapsedTime: String = "",
    val tournamentId: String = "",
    val tournamentName: String = "",
    val round: String = "",
    val highlights: List<String> = emptyList(),
    val winnerId: String = "",
    val createdBy: String = ""
)

/** Match lifecycle: Scheduled → Live → Completed */
enum class MatchStatus {
    SCHEDULED,
    LIVE,
    HALFTIME,
    COMPLETED,
    CANCELLED
}

data class Team(
    val id: String = "",
    val name: String = "",
    val shortName: String = "",
    val logoUrl: String = "",
    val sport: String = "",
    val department: String = "",
    val players: List<String> = emptyList(),
    val wins: Int = 0,
    val losses: Int = 0,
    val draws: Int = 0
)

data class Tournament(
    val id: String = "",
    val name: String = "",
    val sport: String = "",
    val status: TournamentStatus = TournamentStatus.REGISTRATION,
    val startDate: Timestamp? = null,
    val endDate: Timestamp? = null,
    val teams: List<String> = emptyList(),
    val bracketGenerated: Boolean = false,
    val entryFee: Double = 0.0,
    val prizePool: String = "",
    val maxTeams: Int = 16,
    val venue: String = ""
)

enum class TournamentStatus {
    REGISTRATION,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

data class BracketNode(
    val matchId: String = "",
    val round: Int = 0,
    val position: Int = 0,
    val teamA: String? = null,
    val teamB: String? = null,
    val scoreA: Int? = null,
    val scoreB: Int? = null,
    val winner: String? = null,
    val nextMatchId: String? = null
)

data class Payment(
    val id: String = "",
    val teamId: String = "",
    val teamName: String = "",
    val tournamentId: String = "",
    val amount: Double = 0.0,
    val status: PaymentStatus = PaymentStatus.PENDING,
    val timestamp: Timestamp? = null,
    val transactionId: String = ""
)

enum class PaymentStatus {
    PENDING,
    VERIFIED,
    REJECTED
}

data class SportUser(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: UserRole = UserRole.PLAYER,
    val rollNumber: String = "",
    val department: String = "",
    val teamId: String = "",
    val photoUrl: String = ""
)

enum class UserRole {
    PLAYER,
    ADMIN,
    ORGANIZER
}
