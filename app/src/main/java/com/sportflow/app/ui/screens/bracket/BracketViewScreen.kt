package com.sportflow.app.ui.screens.bracket

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.sportflow.app.data.model.*
import com.sportflow.app.ui.components.*
import com.sportflow.app.ui.theme.*
import com.sportflow.app.ui.viewmodel.BracketViewModel

@Composable
fun BracketViewScreen(
    tournamentId: String,
    navController: NavHostController,
    viewModel: BracketViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(tournamentId, uiState.tournaments) {
        val requestedTournament = uiState.tournaments.firstOrNull { it.id == tournamentId }
        when {
            requestedTournament != null &&
                requestedTournament.id != uiState.selectedTournament?.id -> {
                viewModel.selectTournament(requestedTournament)
            }
            tournamentId.isBlank() &&
                uiState.selectedTournament == null &&
                uiState.tournaments.isNotEmpty() -> {
                viewModel.selectTournament(uiState.tournaments.first())
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PureWhite)
                .padding(20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(OffWhite, CircleShape)
                ) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Tournament Bracket",
                        style = SportFlowTheme.typography.displayMedium,
                        color = TextPrimary
                    )
                    uiState.selectedTournament?.let {
                        Text(
                            text = "${it.name} · ${it.sport}",
                            style = SportFlowTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Tournament Selector
        if (uiState.tournaments.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PureWhite)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.tournaments.forEach { tournament ->
                    val isSelected = tournament.id == uiState.selectedTournament?.id
                    FilterChip(
                        onClick = { viewModel.selectTournament(tournament) },
                        label = { Text(tournament.name) },
                        selected = isSelected,
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = OffWhite,
                            selectedContainerColor = GnitsOrangeLight,
                            labelColor = TextSecondary,
                            selectedLabelColor = GnitsOrangeDark
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = CardBorder,
                            selectedBorderColor = GnitsOrange,
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }
        }

        // Loading
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = GnitsOrange)
            }
        } else {
            // Zoom Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pinch to zoom",
                    style = SportFlowTheme.typography.bodySmall,
                    color = TextTertiary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { scale = (scale + 0.2f).coerceAtMost(3f) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.ZoomIn, "Zoom In", tint = TextSecondary)
                }
                IconButton(
                    onClick = { scale = (scale - 0.2f).coerceAtLeast(0.5f) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.ZoomOut, "Zoom Out", tint = TextSecondary)
                }
                IconButton(
                    onClick = { scale = 1f; offsetX = 0f; offsetY = 0f },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Filled.FitScreen, "Reset", tint = TextSecondary)
                }
            }

            // Tournament Tree
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 3f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offsetX,
                        translationY = offsetY
                    )
            ) {
                if (uiState.bracketNodes.isNotEmpty()) {
                    BracketTree(nodes = uiState.bracketNodes)
                } else {
                    EmptyTournamentState()
                }
            }
        }
    }
}

// ── Tournament Tree ─────────────────────────────────────────────────────────────

@Composable
private fun BracketTree(nodes: List<BracketNode>) {
    val rounds = nodes.groupBy { node: BracketNode -> node.round }.toSortedMap()
    val roundLabels = mapOf(
        1 to "Quarter Finals",
        2 to "Semi Finals",
        3 to "Final"
    )

    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        rounds.forEach { (round, matchNodes) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxHeight()
            ) {
                // Round label
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (round == rounds.keys.maxOrNull()) GnitsOrangeLight else OffWhite,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (round == rounds.keys.maxOrNull()) GnitsOrange.copy(alpha = 0.5f) else CardBorder
                    )
                ) {
                    Text(
                        text = roundLabels[round] ?: "Round $round",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        style = SportFlowTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (round == rounds.keys.maxOrNull()) GnitsOrangeDark else TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(
                        when (round) {
                            1 -> 12.dp
                            2 -> 64.dp
                            3 -> 120.dp
                            else -> 12.dp
                        }
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    matchNodes.forEach { node ->
                        BracketMatchNode(
                            node = node,
                            isFinal = round == rounds.keys.maxOrNull()
                        )
                    }
                }
            }

            // Connector lines
            if (round < (rounds.keys.maxOrNull() ?: 0)) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .fillMaxHeight()
                        .drawBehind {
                            val lineColor = BracketLine
                            val step = size.height / (matchNodes.size + 1)
                            matchNodes.forEachIndexed { index, _ ->
                                val y = step * (index + 1)
                                drawLine(
                                    color = lineColor,
                                    start = Offset(0f, y),
                                    end = Offset(size.width, y),
                                    strokeWidth = 2f
                                )
                            }
                        }
                )
            }
        }
    }
}

// ── Tournament Node ─────────────────────────────────────────────────────────────

@Composable
private fun BracketMatchNode(
    node: BracketNode,
    isFinal: Boolean
) {
    val borderColor = when {
        isFinal -> GnitsOrange
        node.winner != null -> GnitsOrange.copy(alpha = 0.5f)
        else -> CardBorder
    }

    SportCard(
        modifier = Modifier
            .width(180.dp)
            .border(
                width = if (isFinal || node.winner != null) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(24.dp)
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (isFinal) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.EmojiEvents,
                        contentDescription = null,
                        tint = GnitsOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "FINAL",
                        style = SportFlowTheme.typography.labelSmall,
                        color = GnitsOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            TournamentTeamRow(
                teamName = node.teamA ?: "TBD",
                score = node.scoreA,
                isWinner = node.winner == node.teamA,
                isTBD = node.teamA == null
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = CardBorder,
                thickness = 0.5.dp
            )

            TournamentTeamRow(
                teamName = node.teamB ?: "TBD",
                score = node.scoreB,
                isWinner = node.winner == node.teamB,
                isTBD = node.teamB == null
            )
        }
    }
}

@Composable
private fun TournamentTeamRow(
    teamName: String,
    score: Int?,
    isWinner: Boolean,
    isTBD: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            if (isWinner) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(BracketWinnerPath, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = teamName,
                style = SportFlowTheme.typography.labelMedium,
                color = when {
                    isTBD -> TextTertiary
                    isWinner -> GnitsOrangeDark
                    else -> TextPrimary
                },
                fontWeight = if (isWinner) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (score != null) {
            Text(
                text = "$score",
                style = SportFlowTheme.typography.headlineSmall,
                color = if (isWinner) GnitsOrangeDark else TextTertiary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ── Empty State ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyTournamentState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        SportCard(
            modifier = Modifier.padding(40.dp)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .background(GnitsOrangeLight, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.AccountTree,
                        contentDescription = null,
                        tint = GnitsOrange,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Tournament Yet",
                    style = SportFlowTheme.typography.headlineLarge,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "The Tournament hasn't been\ngenerated yet.",
                    style = SportFlowTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
