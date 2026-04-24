package com.sportflow.app.data.model

import com.google.firebase.Timestamp

// ── GNITS-Specific Constants ────────────────────────────────────────────────

/** Academic years for GNITS students (B.Tech 4-year programme) */
enum class GnitsYear(val displayName: String) {
    FIRST_YEAR("1st Year"),
    SECOND_YEAR("2nd Year"),
    THIRD_YEAR("3rd Year"),
    FOURTH_YEAR("4th Year");

    companion object {
        fun fromCode(code: String): GnitsYear? =
            entries.find { it.name.equals(code, ignoreCase = true) }
        val allCodes: List<String> get() = entries.map { it.name }
        val allNames: List<String> get() = entries.map { it.displayName }
    }
}

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
    MAIN_GROUND("GNITS Main Ground"),
    BADMINTON_STADIUM("GNITS Badminton Stadium"),
    BASKETBALL_COURT("GNITS Basketball Court"),
    INDOOR_SPORTS_ROOM("GNITS Indoor Stadium");

    companion object {
        val allNames: List<String> get() = entries.map { it.displayName }
    }
}

// ── Sport Scoring Engine ─────────────────────────────────────────────────────

/**
 * Defines the scoring rules and display format for each GNITS sport.
 * Used by Admin scoring panel and all score display UI.
 */
enum class SportType(val display: String) {
    FOOTBALL("Football"),
    CRICKET("Cricket"),
    BASKETBALL("Basketball"),
    BADMINTON("Badminton"),
    VOLLEYBALL("Volleyball"),
    TABLE_TENNIS("Table Tennis"),
    KABADDI("Kabaddi"),
    ATHLETICS("Athletics");

    companion object {
        fun fromString(s: String): SportType =
            entries.firstOrNull { it.display.equals(s, ignoreCase = true) || it.name.equals(s, ignoreCase = true) }
                ?: FOOTBALL
        val displayList get() = entries.map { it.display }
    }
}

// ── Match Model — Extended with sport-specific score fields ──────────────────

data class Match(
    val id: String = "",
    val sportType: String = "",
    val teamA: String = "",
    val teamB: String = "",
    val teamADepartment: String = "",
    val teamBDepartment: String = "",
    val teamALogo: String = "",
    val teamBLogo: String = "",

    // ── Universal score (used for football, kabaddi, athletics) ──────
    val scoreA: Int = 0,
    val scoreB: Int = 0,

    // ── Cricket specific ─────────────────────────────────────────────
    // Team A batting: runs/wickets/overs while team B fields
    val wicketsA: Int = 0,          // wickets fallen for team A
    val wicketsB: Int = 0,
    val oversA: String = "0.0",     // "18.3" format
    val oversB: String = "0.0",
    val extrasA: Int = 0,           // wides + no-balls + byes
    val extrasB: Int = 0,
    val currentInning: Int = 1,     // 1 = Team A batting, 2 = Team B batting
    val cricketFormat: String = "T20", // "T20" | "ODI" | "Test"
    val targetRuns: Int = 0,        // set after 1st innings

    // ── Basketball specific ──────────────────────────────────────────
    val q1A: Int = 0, val q1B: Int = 0,
    val q2A: Int = 0, val q2B: Int = 0,
    val q3A: Int = 0, val q3B: Int = 0,
    val q4A: Int = 0, val q4B: Int = 0,
    val currentQuarter: Int = 1,

    // ── Badminton / Volleyball / Table Tennis (set-based) ────────────
    val setsWonA: Int = 0,          // sets won by team A (primary "score" for set sports)
    val setsWonB: Int = 0,
    val currentSetScoreA: Int = 0,  // points in the current live set
    val currentSetScoreB: Int = 0,
    val currentSet: Int = 1,
    // Completed set history stored as "21-18,18-21,21-15" 
    val completedSets: String = "", // CSV of "scoreA-scoreB" per completed set

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
    val createdBy: String = "",

    // ── Registration & Feed Metadata ──────────────────────────────────
    /** Human-readable eligibility rule, e.g. "IT students only" or "All departments" */
    val eligibilityText: String = "All GNITS students",
    /** Maximum registrations allowed for this match/event (0 = unlimited) */
    val maxRegistrations: Int = 0,
    /** Total registered count (denormalized from registrations sub-collection) */
    val registrationCount: Int = 0,
    /** Next match ID in the bracket — set by FixtureGenerator */
    val nextMatchId: String = "",

    // ── Squad Slot Management ─────────────────────────────────────────────
    /** Maximum players allowed in the squad (0 = unlimited squad size) */
    val maxSquadSize: Int = 0,
    /** Current number of confirmed squad members (denormalized counter) */
    val currentSquadCount: Int = 0,
    /** Departments allowed to register (empty = all departments eligible) */
    val allowedDepartments: List<String> = emptyList(),
    /** Academic years allowed to register e.g. ["FIRST_YEAR","SECOND_YEAR"] (empty = all) */
    val allowedYears: List<String> = emptyList()
)

// ── Sport Rules Engine ───────────────────────────────────────────────────────

data class ScoringAction(
    val label: String,       // Button label e.g. "+1 Run"
    val emoji: String,       // Visual icon e.g. "🏏"
    val field: String,       // Firestore field to update  e.g. "scoreA"
    val delta: Int = 1,      // How much to add
    val team: String,        // "A" or "B"
    val actionType: String = "increment", // "increment" | "special"
    val color: String = "primary"  // "primary" | "danger" | "warning"
)

/** Defines all scoring actions and display format per sport */
object SportScoreEngine {

    fun getActionsForTeam(sportType: String, team: String): List<ScoringAction> {
        val t = if (team == "A") "A" else "B"
        val scoreField = "score$t"
        val wktField = "wickets$t"
        val setField = "currentSetScore$t"

        return when (SportType.fromString(sportType)) {

            SportType.FOOTBALL -> listOf(
                ScoringAction("Goal", "⚽", scoreField, 1, t),
                ScoringAction("Penalty", "🥅", scoreField, 1, t, color = "warning"),
                ScoringAction("Own Goal", "😬", "score${flip(t)}", 1, flip(t), color = "danger")
            )

            SportType.CRICKET -> listOf(
                ScoringAction("1 Run", "🏏", "score$t", 1, t),
                ScoringAction("4 Boundary", "🔵", "score$t", 4, t),
                ScoringAction("6 SIX!", "🔴", "score$t", 6, t, color = "warning"),
                ScoringAction("Wide (+1)", "🟡", "score$t", 1, t, color = "warning"),
                ScoringAction("No Ball (+1)", "⚠️", "extras$t", 1, t, color = "warning"),
                ScoringAction("Wicket 🏏", "❌", "wickets$t", 1, t, color = "danger"),
                ScoringAction("Bye/Leg Bye", "🟠", "score$t", 1, t),
                ScoringAction("Dot Ball", "•", "score$t", 0, t, actionType = "special")
            )

            SportType.BASKETBALL -> listOf(
                ScoringAction("+1 Free Throw", "🎯", "score$t", 1, t),
                ScoringAction("+2 Points", "🏀", "score$t", 2, t),
                ScoringAction("+3 Points", "🌟", "score$t", 3, t, color = "warning"),
                ScoringAction("Foul", "🟨", "score$t", 0, t, actionType = "special", color = "danger")
            )

            SportType.BADMINTON -> listOf(
                ScoringAction("+1 Point", "🏸", setField, 1, t),
                ScoringAction("Ace 🌟", "⭐", setField, 1, t, color = "warning"),
                ScoringAction("Fault", "🚫", setField, 0, t, actionType = "special", color = "danger")
            )

            SportType.VOLLEYBALL -> listOf(
                ScoringAction("+1 Point", "🏐", setField, 1, t),
                ScoringAction("Ace", "⭐", setField, 1, t, color = "warning"),
                ScoringAction("Block Point", "🧱", setField, 1, t)
            )

            SportType.TABLE_TENNIS -> listOf(
                ScoringAction("+1 Point", "🏓", setField, 1, t),
                ScoringAction("Ace", "⭐", setField, 1, t, color = "warning")
            )

            SportType.KABADDI -> listOf(
                ScoringAction("Raid Point", "🤸", scoreField, 1, t),
                ScoringAction("Tackle Point", "💪", scoreField, 1, t, color = "warning"),
                ScoringAction("Bonus Point", "⭐", scoreField, 2, t),
                ScoringAction("All Out (+2)", "🔥", scoreField, 2, t, color = "danger")
            )

            SportType.ATHLETICS -> listOf(
                ScoringAction("1st Place", "🥇", scoreField, 5, t),
                ScoringAction("2nd Place", "🥈", scoreField, 3, t, color = "warning"),
                ScoringAction("3rd Place", "🥉", scoreField, 1, t),
                ScoringAction("Record", "⭐", scoreField, 5, t, color = "warning")
            )
        }
    }

    /** Format the score for display in cards and live screen */
    fun formatScore(match: Match): SportScore {
        return when (SportType.fromString(match.sportType)) {
            SportType.CRICKET -> {
                val teamADisplay = if (match.currentInning == 1)
                    "${match.scoreA}/${match.wicketsA} (${match.oversA})"
                else
                    "${match.scoreA}/${match.wicketsA} (${match.oversA})"

                val teamBDisplay = if (match.currentInning == 1)
                    "Yet to bat"
                else
                    "${match.scoreB}/${match.wicketsB} (${match.oversB})"

                val chasing = if (match.currentInning == 2 && match.targetRuns > 0) {
                    val need = match.targetRuns - match.scoreB
                    "Need: $need"
                } else ""

                SportScore(
                    displayA = teamADisplay,
                    displayB = teamBDisplay,
                    centerText = if (match.currentInning == 1) "1st Inn." else "2nd Inn.",
                    subText = chasing,
                    extras = "Extras A: ${match.extrasA} | B: ${match.extrasB}"
                )
            }

            SportType.BASKETBALL -> {
                val totalA = match.q1A + match.q2A + match.q3A + match.q4A
                val totalB = match.q1B + match.q2B + match.q3B + match.q4B
                SportScore(
                    displayA = "$totalA",
                    displayB = "$totalB",
                    centerText = "Q${match.currentQuarter}",
                    subText = "Q1: ${match.q1A}-${match.q1B} | Q2: ${match.q2A}-${match.q2B}" +
                            if (match.currentQuarter > 2) " | Q3: ${match.q3A}-${match.q3B}" else ""
                )
            }

            SportType.BADMINTON, SportType.TABLE_TENNIS, SportType.VOLLEYBALL -> {
                val maxSets = when (SportType.fromString(match.sportType)) {
                    SportType.VOLLEYBALL -> 5
                    else -> 3
                }
                val sets = if (match.completedSets.isNotBlank())
                    match.completedSets.split(",").mapNotNull { s ->
                        val p = s.split("-")
                        if (p.size == 2) "${p[0]}-${p[1]}" else null
                    } else emptyList()

                val currentSetStr = "⟨${match.currentSetScoreA}-${match.currentSetScoreB}⟩"
                val allSets = (sets + currentSetStr).joinToString("  ")

                SportScore(
                    displayA = "${match.setsWonA}",
                    displayB = "${match.setsWonB}",
                    centerText = "Set ${match.currentSet}/$maxSets",
                    subText = allSets,
                    isSetBased = true,
                    currentSetA = match.currentSetScoreA,
                    currentSetB = match.currentSetScoreB
                )
            }

            SportType.FOOTBALL -> SportScore(
                displayA = "${match.scoreA}",
                displayB = "${match.scoreB}",
                centerText = match.currentPeriod.ifEmpty { "FT" }
            )

            SportType.KABADDI -> SportScore(
                displayA = "${match.scoreA}",
                displayB = "${match.scoreB}",
                centerText = match.currentPeriod.ifEmpty { "Raid" }
            )

            SportType.ATHLETICS -> SportScore(
                displayA = "${match.scoreA} pts",
                displayB = "${match.scoreB} pts",
                centerText = "Athletics"
            )
        }
    }

    private fun flip(team: String) = if (team == "A") "B" else "A"
}

/** Formatted score ready for display */
data class SportScore(
    val displayA: String,         // What to show for Team A
    val displayB: String,         // What to show for Team B
    val centerText: String = "",  // Period/inning/set label
    val subText: String = "",     // Quarter breakdown, chase target etc.
    val extras: String = "",      // Cricket extras
    val isSetBased: Boolean = false,
    val currentSetA: Int = 0,
    val currentSetB: Int = 0
)

// ── Remaining unchanged models ───────────────────────────────────────────────

enum class MatchStatus {
    SCHEDULED, LIVE, HALFTIME, COMPLETED, CANCELLED
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
    val venue: String = "",
    /** Human-readable eligibility text for the tournament */
    val eligibilityText: String = "All GNITS students",
    /** Maximum teams per department (0 = no restriction) */
    val deptQuota: Int = 0,
    /** Allowed departments list (empty = all) */
    val allowedDepartments: List<String> = emptyList(),
    /** Allowed academic years list (empty = all years eligible) */
    val allowedYears: List<String> = emptyList(),
    val createdBy: String = "",
    val createdAt: Timestamp? = null
)

enum class TournamentStatus { REGISTRATION, IN_PROGRESS, COMPLETED, CANCELLED }

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

enum class PaymentStatus { PENDING, VERIFIED, REJECTED }

// ── Registration Model — stored in gnits_matches/{matchId}/registrations ───────

enum class RegistrationStatus { PENDING, CONFIRMED, CANCELLED }

enum class RegistrationKind {
    INDIVIDUAL,
    TEAM,
    BADMINTON_SINGLES,
    BADMINTON_DOUBLES
}

data class SquadPlayer(
    val name: String = "",
    val rollNumber: String = "",
    val role: String = ""
)

data class RegistrationPayload(
    val rollNumber: String = "",
    val email: String = "",
    val department: String = "",
    val yearOfStudy: String = "",
    val sportRole: String = "",
    val registrationKind: RegistrationKind = RegistrationKind.INDIVIDUAL,
    val teamName: String = "",
    val captainName: String = "",
    val captainPhone: String = "",
    val roster: List<SquadPlayer> = emptyList(),
    val partnerName: String = "",
    val partnerRollNumber: String = "",
    val partnerRole: String = ""
)

data class Registration(
    val id: String = "",
    val uid: String = "",
    val matchId: String = "",
    val tournamentId: String = "",
    val userName: String = "",
    val email: String = "",             // Student email — populated from FirebaseAuth
    val department: String = "",
    val yearOfStudy: String = "",        // GnitsYear code e.g. "SECOND_YEAR"
    val rollNumber: String = "",
    val status: RegistrationStatus = RegistrationStatus.CONFIRMED,
    val registeredAt: Timestamp? = null,
    /** Admin "New Entry" badge flag — false until admin views this registration */
    val seen: Boolean = false,

    // ── Advanced Squad Registration Fields ────────────────────────────
    val squadName: String = "",
    val captainName: String = "",
    val captainPhone: String = "",
    val squadSize: Int = 1,

    // ── Sport-Specific Role (e.g. Bowler, Defender, Point Guard) ──────
    val sportRole: String = "",
    /** Sport type of the event — denormalized for admin quick-view */
    val sportType: String = "",
    /** Match name — denormalized for admin list display */
    val matchName: String = "",
    val registrationKind: RegistrationKind = RegistrationKind.INDIVIDUAL,
    val teamName: String = "",
    val roster: List<SquadPlayer> = emptyList(),
    val partnerName: String = "",
    val partnerRollNumber: String = "",
    val partnerRole: String = "",
    val fixtureUnitName: String = ""
)

data class SportUser(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: UserRole = UserRole.PLAYER,
    val rollNumber: String = "",
    val department: String = "",
    val yearOfStudy: String = "",        // GnitsYear code e.g. "SECOND_YEAR"
    val teamId: String = "",
    val photoUrl: String = "",
    /** Default sport-specific role chosen by the player */
    val preferredSportRole: String = ""
)

enum class UserRole { PLAYER, ADMIN, ORGANIZER }

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TOURNAMENT TYPE — for Dual-Mode Fixture Engine
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

enum class TournamentType(val displayName: String) {
    SINGLE_ELIMINATION("Single Elimination"),
    ROUND_ROBIN("Round Robin");

    companion object {
        fun fromString(s: String): TournamentType =
            entries.firstOrNull { it.name.equals(s, ignoreCase = true) || it.displayName.equals(s, ignoreCase = true) }
                ?: SINGLE_ELIMINATION
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// PLAYER SCORECARD — Sport-Specific Personal Performance (Strategy Pattern)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Personal scorecard stored at:
 *   gnits_matches/{matchId}/scorecards/{playerId}
 *
 * The [sportData] map holds sport-specific attributes.
 * The Strategy Pattern is implemented via [PlayerScorecardStrategy] which knows
 * how to interpret this map for each sport type.
 */
data class PlayerScorecard(
    val id: String = "",
    val matchId: String = "",
    val playerId: String = "",
    val playerName: String = "",
    val department: String = "",
    val team: String = "",               // "A" or "B"
    val sportType: String = "",         // SportType enum name
    /** Sport-specific key-value stats — interpreted by the SportStrategy */
    val sportData: Map<String, Any> = emptyMap(),
    val updatedAt: com.google.firebase.Timestamp? = null
)

/**
 * Strategy Pattern — returns typed attribute labels and values for UI display,
 * plus the Firestore field keys for the Admin Referee Panel scoring buttons.
 */
object PlayerScorecardStrategy {

    data class StatLabel(
        val key: String,         // Firestore field key inside sportData map
        val label: String,       // Display label e.g. "Runs Scored"
        val emoji: String,       // Visual emoji
        val defaultValue: Any = 0 // Default value for initialization
    )

    /**
     * Returns the ordered list of stat attributes for a given sport.
     * The Admin Referee Panel renders exactly these fields.
     * The Player Performance tab displays them in this order.
     */
    fun getAttributes(sportType: String): List<StatLabel> {
        return when (SportType.fromString(sportType)) {

            SportType.CRICKET -> listOf(
                StatLabel("runsScored", "Runs Scored", "🏏"),
                StatLabel("ballsFaced", "Balls Faced", "⚾"),
                StatLabel("fours", "Fours", "4️⃣"),
                StatLabel("sixes", "Sixes", "6️⃣"),
                StatLabel("wicketsTaken", "Wickets Taken", "🎯"),
                StatLabel("oversBowled", "Overs Bowled", "🔄", defaultValue = "0.0"),
                StatLabel("runsConceded", "Runs Conceded", "📊"),
                StatLabel("economyRate", "Economy Rate", "📈", defaultValue = "0.00"),
                StatLabel("catches", "Catches", "🤲"),
                StatLabel("runOuts", "Run Outs", "🏃")
            )

            SportType.VOLLEYBALL -> listOf(
                StatLabel("setsWon", "Sets Won", "🏐"),
                StatLabel("aces", "Aces", "⭐"),
                StatLabel("kills", "Kills", "💥"),
                StatLabel("blocks", "Blocks", "🧱"),
                StatLabel("digs", "Digs", "🖐️"),
                StatLabel("serviceErrors", "Service Errors", "❌"),
                StatLabel("attackErrors", "Attack Errors", "🚫")
            )

            SportType.BADMINTON -> listOf(
                StatLabel("setsWon", "Sets Won", "🏸"),
                StatLabel("aces", "Aces", "⭐"),
                StatLabel("smashes", "Smashes", "💨"),
                StatLabel("drops", "Drop Shots", "🎯"),
                StatLabel("netPoints", "Net Points", "🕸️"),
                StatLabel("serviceErrors", "Service Errors", "❌"),
                StatLabel("unforcedErrors", "Unforced Errors", "🚫")
            )

            SportType.TABLE_TENNIS -> listOf(
                StatLabel("setsWon", "Sets Won", "🏓"),
                StatLabel("aces", "Aces", "⭐"),
                StatLabel("winners", "Winners", "💥"),
                StatLabel("serviceErrors", "Service Errors", "❌"),
                StatLabel("unforcedErrors", "Unforced Errors", "🚫")
            )

            SportType.BASKETBALL -> listOf(
                StatLabel("points", "Points", "🏀"),
                StatLabel("rebounds", "Rebounds", "🔄"),
                StatLabel("assists", "Assists", "🤝"),
                StatLabel("steals", "Steals", "🤏"),
                StatLabel("blocks", "Blocks", "🧱"),
                StatLabel("turnovers", "Turnovers", "❌"),
                StatLabel("freeThrowsMade", "Free Throws Made", "🎯"),
                StatLabel("freeThrowsAttempted", "Free Throws Attempted", "🏹"),
                StatLabel("threePointers", "3-Pointers Made", "🌟")
            )

            SportType.FOOTBALL -> listOf(
                StatLabel("goals", "Goals", "⚽"),
                StatLabel("assists", "Assists", "🤝"),
                StatLabel("shots", "Shots", "🎯"),
                StatLabel("shotsOnTarget", "Shots on Target", "🥅"),
                StatLabel("passes", "Passes", "📦"),
                StatLabel("tackles", "Tackles", "💪"),
                StatLabel("fouls", "Fouls", "🟨"),
                StatLabel("yellowCards", "Yellow Cards", "🟡"),
                StatLabel("redCards", "Red Cards", "🔴")
            )

            SportType.KABADDI -> listOf(
                StatLabel("raidPoints", "Raid Points", "🤸"),
                StatLabel("tacklePoints", "Tackle Points", "💪"),
                StatLabel("bonusPoints", "Bonus Points", "⭐"),
                StatLabel("superRaids", "Super Raids", "🔥"),
                StatLabel("superTackles", "Super Tackles", "🛡️"),
                StatLabel("emptyRaids", "Empty Raids", "❌")
            )

            SportType.ATHLETICS -> listOf(
                StatLabel("position", "Position", "🏅"),
                StatLabel("timeTaken", "Time / Distance", "⏱️", defaultValue = ""),
                StatLabel("personalBest", "Personal Best", "🌟", defaultValue = ""),
                StatLabel("points", "Points", "📊")
            )
        }
    }

    /** Creates a default sportData map with zero/empty values for all attributes */
    fun createDefault(sportType: String): Map<String, Any> {
        return getAttributes(sportType).associate { it.key to it.defaultValue }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// MANUAL FIXTURE EDIT — For Admin drag-and-drop override
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Represents a manual edit to an existing fixture (match).
 * The Admin can change the scheduled time, venue, or swap team names
 * via the Manual Fixture Editor in the Admin Dashboard.
 *
 * Each edit triggers an immediate Firestore update + StateFlow emission
 * so all student Home Screens reactively re-sort the "Upcoming Matches" list.
 */
data class ManualFixtureEdit(
    val matchId: String,
    val newScheduledTime: com.google.firebase.Timestamp? = null,
    val newVenue: String? = null,
    val newTeamA: String? = null,
    val newTeamB: String? = null,
    val newTeamADepartment: String? = null,
    val newTeamBDepartment: String? = null
)

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// FIXTURE GENERATION CONFIG — Input for the AI Fixture Engine
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

data class FixtureConfig(
    val tournamentId: String = "",
    val tournamentName: String = "",
    val tournamentType: TournamentType = TournamentType.SINGLE_ELIMINATION,
    val sportType: String = "",
    val teams: List<String> = emptyList(),
    val startTime: com.google.firebase.Timestamp? = null,
    val venue: String = GnitsVenue.MAIN_GROUND.displayName,
    val intervalMinutes: Long = 60,
    val eligibilityText: String = "All GNITS students"
)

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// SPORT-SPECIFIC ROLES — Used during Registration for Admin Data Bridge
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

object SportRoles {
    fun getRoles(sportType: String): List<String> {
        return when (SportType.fromString(sportType)) {
            SportType.CRICKET    -> listOf("Batsman", "Bowler", "All-Rounder", "Wicket Keeper", "Fielder")
            SportType.FOOTBALL   -> listOf("Goalkeeper", "Defender", "Midfielder", "Forward", "Striker")
            SportType.BASKETBALL -> listOf("Point Guard", "Shooting Guard", "Small Forward", "Power Forward", "Center")
            SportType.BADMINTON  -> listOf("Singles Player", "Doubles Player", "Mixed Doubles")
            SportType.VOLLEYBALL -> listOf("Setter", "Outside Hitter", "Middle Blocker", "Libero", "Opposite Hitter")
            SportType.TABLE_TENNIS -> listOf("Singles Player", "Doubles Player")
            SportType.KABADDI    -> listOf("Raider", "Defender", "All-Rounder")
            SportType.ATHLETICS  -> listOf("Sprinter", "Long Distance", "Jumper", "Thrower", "Relay")
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// NOTIFICATION ITEM — Seen/Unseen tracking for in-app Notification Center
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Stored at: gnits_users/{uid}/notifications/{notificationId}
 * Each notification received by the user (via FCM or Firestore triggers) is
 * persisted here. The [seen] flag drives the bell icon badge count on the Home Screen.
 */
data class NotificationItem(
    val id: String = "",
    val type: String = "",              // "score_update" | "match_start" | "spot_opened" | etc.
    val title: String = "",
    val body: String = "",
    val matchId: String = "",
    val timestamp: Timestamp? = null,
    val seen: Boolean = false
)

enum class AnnouncementCategory {
    GENERAL,
    URGENT,
    VENUE_SHIFT,
    SCHEDULE_CHANGE,
    RESULT,
    FIXTURE_UPDATE
}

data class Announcement(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val category: AnnouncementCategory = AnnouncementCategory.GENERAL,
    val matchId: String = "",
    val tournamentId: String = "",
    val createdAt: Timestamp? = null,
    val createdBy: String = ""
)
