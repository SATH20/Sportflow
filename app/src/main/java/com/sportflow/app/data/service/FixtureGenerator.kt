package com.sportflow.app.data.service

import com.google.firebase.Timestamp
import com.sportflow.app.data.model.GnitsVenue
import com.sportflow.app.data.model.Match
import com.sportflow.app.data.model.MatchStatus
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.log2
import kotlin.math.pow

/**
 * FixtureGenerator — Admin Automation Engine
 *
 * Generates a complete bracket fixture list with proper [Match.nextMatchId] links
 * so that [SportFlowRepository.advanceWinnerInBracket] can walk the tree.
 *
 * Usage:
 *   val generator = FixtureGenerator()
 *   val fixtures  = generator.generateSingleElimination(
 *       teams       = shuffledTeamList,
 *       matchType   = "Cricket",
 *       startTime   = Timestamp.now(),
 *       tournamentId = "t001",
 *       tournamentName = "GNITS T20 Cup"
 *   )
 *   // Then write fixtures to Firestore via repository.createMatch()
 */
class FixtureGenerator {

    /**
     * Single-elimination bracket — the most common format for GNITS events.
     *
     * @param teams          Ordered or pre-seeded team names (will be shuffled internally)
     * @param matchType      Sport display name, e.g. "Cricket", "Basketball"
     * @param startTime      Timestamp for the first round's matches
     * @param tournamentId   Parent tournament document ID
     * @param tournamentName Human-readable tournament name
     * @param venueName      GNITS venue, defaults to Indoor Stadium
     * @param eligibilityText Department restriction, e.g. "IT students only"
     * @param intervalMinutes Minutes between successive rounds
     * @return A list of [Match] objects, each with [Match.nextMatchId] wired.
     *         IDs are synthetic UUIDs — Firestore document IDs should be set from
     *         [Match.id] when writing.
     */
    fun generateSingleElimination(
        teams: List<String>,
        matchType: String,
        startTime: Timestamp,
        tournamentId: String,
        tournamentName: String,
        venueName: String = GnitsVenue.INDOOR_SPORTS_ROOM.displayName,
        eligibilityText: String = "All GNITS students",
        intervalMinutes: Long = 60L
    ): List<Match> {
        require(teams.size >= 2) { "Need at least 2 teams to generate fixtures" }

        val shuffled    = teams.shuffled()
        val totalRounds = ceil(log2(shuffled.size.toDouble())).toInt()

        // ── Phase 1: Assign IDs to every slot in the bracket ──────────────────

        // bracket[round][position] = matchId
        val bracket: Array<Array<String>> = Array(totalRounds + 1) { round ->
            val slots = if (round == 0) 0
            else ceil(shuffled.size / 2.0.pow(round.toDouble())).toInt()
            Array(slots) { UUID.randomUUID().toString().replace("-", "").take(20) }
        }

        // ── Phase 2: Build first-round matches ────────────────────────────────

        val allMatches = mutableListOf<Match>()
        var timeOffset  = 0L // in minutes from startTime

        for (pos in bracket[1].indices) {
            val teamA = shuffled.getOrNull(pos * 2) ?: "TBD"
            val teamB = shuffled.getOrNull(pos * 2 + 1) ?: "BYE"

            val nextRound = 2
            val nextPos   = pos / 2
            val nextId    = if (totalRounds >= nextRound) bracket[nextRound].getOrElse(nextPos) { "" } else ""

            allMatches.add(
                Match(
                    id             = bracket[1][pos],
                    sportType      = matchType,
                    teamA          = teamA,
                    teamB          = teamB,
                    status         = MatchStatus.SCHEDULED,
                    venue          = venueName,
                    scheduledTime  = Timestamp(startTime.seconds + timeOffset * 60, 0),
                    tournamentId   = tournamentId,
                    tournamentName = tournamentName,
                    round          = "Round 1",
                    nextMatchId    = nextId,
                    eligibilityText = eligibilityText
                )
            )
            timeOffset += intervalMinutes
        }

        // ── Phase 3: Build subsequent round placeholder matches ───────────────

        for (round in 2..totalRounds) {
            val roundName = when (round) {
                totalRounds     -> "Final"
                totalRounds - 1 -> "Semi-Final"
                totalRounds - 2 -> "Quarter-Final"
                else            -> "Round $round"
            }

            for (pos in bracket[round].indices) {
                val nextRound = round + 1
                val nextPos   = pos / 2
                val nextId    = if (totalRounds >= nextRound)
                    bracket[nextRound].getOrElse(nextPos) { "" }
                else ""

                allMatches.add(
                    Match(
                        id             = bracket[round][pos],
                        sportType      = matchType,
                        teamA          = "TBD",
                        teamB          = "TBD",
                        status         = MatchStatus.SCHEDULED,
                        venue          = venueName,
                        scheduledTime  = Timestamp(startTime.seconds + timeOffset * 60, 0),
                        tournamentId   = tournamentId,
                        tournamentName = tournamentName,
                        round          = roundName,
                        nextMatchId    = nextId,
                        eligibilityText = eligibilityText
                    )
                )
                timeOffset += intervalMinutes
            }
        }

        return allMatches
    }

    /**
     * Round-robin group stage fixture generator.
     * Each team plays every other team exactly once.
     *
     * @param teams     Participant teams
     * @param matchType Sport name
     * @param startTime Start time for the first match
     * @param tournamentId Parent tournament
     * @param tournamentName Tournament display name
     * @param venueName GNITS venue
     * @param eligibilityText Eligibility rule
     * @param intervalMinutes Gap between matches
     */
    fun generateRoundRobin(
        teams: List<String>,
        matchType: String,
        startTime: Timestamp,
        tournamentId: String,
        tournamentName: String,
        venueName: String = GnitsVenue.MAIN_GROUND.displayName,
        eligibilityText: String = "All GNITS students",
        intervalMinutes: Long = 45L
    ): List<Match> {
        require(teams.size >= 2) { "Need at least 2 teams" }

        val allMatches  = mutableListOf<Match>()
        var timeOffset  = 0L

        for (i in teams.indices) {
            for (j in i + 1 until teams.size) {
                allMatches.add(
                    Match(
                        id             = UUID.randomUUID().toString().replace("-", "").take(20),
                        sportType      = matchType,
                        teamA          = teams[i],
                        teamB          = teams[j],
                        status         = MatchStatus.SCHEDULED,
                        venue          = venueName,
                        scheduledTime  = Timestamp(startTime.seconds + timeOffset * 60, 0),
                        tournamentId   = tournamentId,
                        tournamentName = tournamentName,
                        round          = "Group Stage",
                        nextMatchId    = "",   // Round-robin has no bracket propagation
                        eligibilityText = eligibilityText
                    )
                )
                timeOffset += intervalMinutes
            }
        }
        return allMatches
    }

    /**
     * Compute the total number of rounds for a given team count
     * (single-elimination).
     */
    fun totalRounds(teamCount: Int): Int =
        ceil(log2(teamCount.toDouble())).toInt()
}
