package com.sportflow.app.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sportflow.app.data.model.*
import com.sportflow.app.ui.theme.*

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// ADVANCED 3-STEP REGISTRATION BOTTOM SHEET
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

enum class RegistrationStep {
    ELIGIBILITY_CHECK,
    SQUAD_DETAILS,
    CONFIRMATION
}

data class RegistrationFormState(
    val currentStep: RegistrationStep = RegistrationStep.ELIGIBILITY_CHECK,
    val squadName: String = "",
    val captainName: String = "",
    val captainPhone: String = "",
    val squadSize: Int = 1,
    val isEligible: Boolean = false,
    val eligibilityMessage: String = "",
    val isSubmitting: Boolean = false
)

/**
 * Advanced 3-Step Registration BottomSheet with:
 * - Step 1: Eligibility validation (department + academic year)
 * - Step 2: Squad details form
 * - Step 3: Confirmation with capacity indicator
 * 
 * Uses Ice & Action White Theme with GNITS Orange accents
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationBottomSheet(
    match: Match,
    currentUser: SportUser?,
    currentSquadCount: Int,
    maxSquads: Int,
    onDismiss: () -> Unit,
    onRegister: (squadName: String, captainName: String, captainPhone: String, squadSize: Int) -> Unit
) {
    var formState by remember {
        mutableStateOf(
            RegistrationFormState(
                captainName = currentUser?.displayName ?: "",
                squadName = "${currentUser?.department ?: "GNITS"} Squad"
            )
        )
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    // Validate eligibility on mount
    LaunchedEffect(Unit) {
        val eligibility = validateEligibility(match, currentUser)
        formState = formState.copy(
            isEligible = eligibility.first,
            eligibilityMessage = eligibility.second
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PureWhite,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(CardBorder)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header with progress
            RegistrationHeader(
                match = match,
                currentStep = formState.currentStep,
                currentSquadCount = currentSquadCount,
                maxSquads = maxSquads
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Step Content
            AnimatedContent(
                targetState = formState.currentStep,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                },
                label = "stepTransition"
            ) { step ->
                when (step) {
                    RegistrationStep.ELIGIBILITY_CHECK -> {
                        EligibilityCheckStep(
                            isEligible = formState.isEligible,
                            message = formState.eligibilityMessage,
                            currentUser = currentUser,
                            match = match,
                            onContinue = {
                                formState = formState.copy(currentStep = RegistrationStep.SQUAD_DETAILS)
                            },
                            onCancel = onDismiss
                        )
                    }

                    RegistrationStep.SQUAD_DETAILS -> {
                        SquadDetailsStep(
                            squadName = formState.squadName,
                            captainName = formState.captainName,
                            captainPhone = formState.captainPhone,
                            squadSize = formState.squadSize,
                            onSquadNameChange = { formState = formState.copy(squadName = it) },
                            onCaptainNameChange = { formState = formState.copy(captainName = it) },
                            onCaptainPhoneChange = { formState = formState.copy(captainPhone = it) },
                            onSquadSizeChange = { formState = formState.copy(squadSize = it) },
                            onBack = {
                                formState = formState.copy(currentStep = RegistrationStep.ELIGIBILITY_CHECK)
                            },
                            onContinue = {
                                formState = formState.copy(currentStep = RegistrationStep.CONFIRMATION)
                            }
                        )
                    }

                    RegistrationStep.CONFIRMATION -> {
                        ConfirmationStep(
                            match = match,
                            squadName = formState.squadName,
                            captainName = formState.captainName,
                            captainPhone = formState.captainPhone,
                            squadSize = formState.squadSize,
                            currentSquadCount = currentSquadCount,
                            maxSquads = maxSquads,
                            isSubmitting = formState.isSubmitting,
                            onBack = {
                                formState = formState.copy(currentStep = RegistrationStep.SQUAD_DETAILS)
                            },
                            onConfirm = {
                                formState = formState.copy(isSubmitting = true)
                                onRegister(
                                    formState.squadName,
                                    formState.captainName,
                                    formState.captainPhone,
                                    formState.squadSize
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// HEADER WITH PROGRESS
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun RegistrationHeader(
    match: Match,
    currentStep: RegistrationStep,
    currentSquadCount: Int,
    maxSquads: Int
) {
    Column {
        // Match Info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Register for Event",
                    style = SportFlowTheme.typography.displayMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${match.teamA} vs ${match.teamB}",
                    style = SportFlowTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            SportTypeChip(sportType = match.sportType)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Squad Capacity Progress Bar
        SquadCapacityIndicator(
            currentCount = currentSquadCount,
            maxCount = maxSquads
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Step Progress Indicator
        StepProgressIndicator(currentStep = currentStep)
    }
}

@Composable
private fun SquadCapacityIndicator(
    currentCount: Int,
    maxCount: Int
) {
    val progress = if (maxCount > 0) currentCount.toFloat() / maxCount.toFloat() else 0f
    val isFull = currentCount >= maxCount && maxCount > 0

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Groups,
                    contentDescription = null,
                    tint = if (isFull) ErrorRed else GnitsOrange,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Squad Capacity",
                    style = SportFlowTheme.typography.labelMedium,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = if (maxCount > 0) "$currentCount / $maxCount" else "$currentCount squads",
                style = SportFlowTheme.typography.labelLarge,
                color = if (isFull) ErrorRed else GnitsOrange,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Linear Progress Bar
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = if (isFull) ErrorRed else GnitsOrange,
            trackColor = OffWhite,
        )

        if (isFull) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = LiveRedBg
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Squad capacity reached",
                        style = SportFlowTheme.typography.bodySmall,
                        color = ErrorRed,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun StepProgressIndicator(currentStep: RegistrationStep) {
    val steps = listOf(
        RegistrationStep.ELIGIBILITY_CHECK to "Eligibility",
        RegistrationStep.SQUAD_DETAILS to "Squad Info",
        RegistrationStep.CONFIRMATION to "Confirm"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, (step, label) ->
            val isActive = currentStep == step
            val isCompleted = currentStep.ordinal > step.ordinal

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Step Circle
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            when {
                                isCompleted -> SuccessGreen
                                isActive -> GnitsOrange
                                else -> OffWhite
                            },
                            CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = when {
                                isCompleted -> SuccessGreen
                                isActive -> GnitsOrange
                                else -> CardBorder
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isCompleted) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = "${index + 1}",
                            style = SportFlowTheme.typography.labelSmall,
                            color = if (isActive) Color.White else TextTertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (index < steps.size - 1) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp)
                            .padding(horizontal = 4.dp)
                            .background(
                                if (isCompleted) SuccessGreen else CardBorder,
                                RoundedCornerShape(1.dp)
                            )
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        steps.forEach { (step, label) ->
            Text(
                text = label,
                style = SportFlowTheme.typography.labelSmall,
                color = if (currentStep == step) TextPrimary else TextTertiary,
                fontWeight = if (currentStep == step) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
                textAlign = when (step) {
                    RegistrationStep.ELIGIBILITY_CHECK -> TextAlign.Start
                    RegistrationStep.SQUAD_DETAILS -> TextAlign.Center
                    RegistrationStep.CONFIRMATION -> TextAlign.End
                }
            )
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// STEP 1: ELIGIBILITY CHECK
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun EligibilityCheckStep(
    isEligible: Boolean,
    message: String,
    currentUser: SportUser?,
    match: Match,
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Eligibility Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    if (isEligible) SuccessGreen.copy(alpha = 0.1f)
                    else LiveRedBg,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isEligible) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                contentDescription = null,
                tint = if (isEligible) SuccessGreen else ErrorRed,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (isEligible) "You're Eligible! ✓" else "Eligibility Check Failed",
            style = SportFlowTheme.typography.headlineLarge,
            color = if (isEligible) SuccessGreen else ErrorRed,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = message,
            style = SportFlowTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // User Info Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = OffWhite,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                InfoRow(
                    icon = Icons.Outlined.Person,
                    label = "Student",
                    value = currentUser?.displayName ?: "Unknown"
                )
                Spacer(modifier = Modifier.height(10.dp))
                InfoRow(
                    icon = Icons.Outlined.Badge,
                    label = "Roll Number",
                    value = currentUser?.rollNumber?.ifEmpty { "Not set" } ?: "Not set"
                )
                Spacer(modifier = Modifier.height(10.dp))
                InfoRow(
                    icon = Icons.Outlined.School,
                    label = "Department",
                    value = currentUser?.department?.ifEmpty { "Not set" } ?: "Not set"
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Event Requirements
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = GnitsOrangeLight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = GnitsOrangeDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Event Requirements",
                        style = SportFlowTheme.typography.labelLarge,
                        color = GnitsOrangeDark,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = match.eligibilityText.ifEmpty { "Open to all GNITS students" },
                    style = SportFlowTheme.typography.bodySmall,
                    color = GnitsOrangeDark.copy(alpha = 0.9f)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CardBorder)
            ) {
                Text(
                    "Cancel",
                    style = SportFlowTheme.typography.labelLarge,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = onContinue,
                enabled = isEligible,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GnitsOrange,
                    disabledContainerColor = CardBorder
                )
            ) {
                Text(
                    "Continue",
                    style = SportFlowTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = SportFlowTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Text(
            text = value,
            style = SportFlowTheme.typography.bodyMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// ELIGIBILITY VALIDATION LOGIC
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/**
 * Validates if the current user is eligible for the event based on:
 * - Department restrictions
 * - Academic year requirements (if implemented)
 * - Custom eligibility rules
 * 
 * @return Pair<Boolean, String> - (isEligible, message)
 */
fun validateEligibility(match: Match, currentUser: SportUser?): Pair<Boolean, String> {
    if (currentUser == null) {
        return false to "You must be signed in to register for events"
    }

    // Check if user has required profile fields
    if (currentUser.department.isBlank()) {
        return false to "Please update your department in Profile settings before registering"
    }

    if (currentUser.rollNumber.isBlank()) {
        return false to "Please update your roll number in Profile settings before registering"
    }

    // Parse eligibility text for department restrictions
    val eligibilityText = match.eligibilityText.lowercase()
    
    // Check for "all" or empty eligibility (open to everyone)
    if (eligibilityText.contains("all") || eligibilityText.isBlank()) {
        return true to "This event is open to all GNITS students. You meet all requirements!"
    }

    // Check for specific department restrictions
    val userDept = currentUser.department.lowercase()
    
    // Check if user's department is mentioned in eligibility text
    if (eligibilityText.contains(userDept)) {
        return true to "Great! Your department (${currentUser.department}) is eligible for this event."
    }

    // Check for "only" restrictions (e.g., "IT students only")
    if (eligibilityText.contains("only")) {
        // Extract department from eligibility text
        val restrictedDepts = GnitsDepartment.entries.filter { dept ->
            eligibilityText.contains(dept.name.lowercase()) ||
            eligibilityText.contains(dept.displayName.lowercase())
        }

        if (restrictedDepts.isNotEmpty() && restrictedDepts.none { it.name.equals(currentUser.department, ignoreCase = true) }) {
            val deptNames = restrictedDepts.joinToString(", ") { it.name }
            return false to "This event is restricted to $deptNames students only. Your department (${currentUser.department}) is not eligible."
        }
    }

    // Check for inter-department restrictions
    if (eligibilityText.contains("inter") && eligibilityText.contains("dept")) {
        return true to "This is an inter-department event. All GNITS students are welcome!"
    }

    // Default: if no specific restrictions found, allow registration
    return true to "You meet the eligibility requirements for this event."
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// STEP 2: SQUAD DETAILS FORM
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun SquadDetailsStep(
    squadName: String,
    captainName: String,
    captainPhone: String,
    squadSize: Int,
    onSquadNameChange: (String) -> Unit,
    onCaptainNameChange: (String) -> Unit,
    onCaptainPhoneChange: (String) -> Unit,
    onSquadSizeChange: (Int) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit
) {
    val isFormValid = squadName.isNotBlank() &&
            captainName.isNotBlank() &&
            captainPhone.length >= 10 &&
            squadSize > 0

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Squad Information",
            style = SportFlowTheme.typography.headlineLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Provide your squad details to complete registration",
            style = SportFlowTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Squad Name
        OutlinedTextField(
            value = squadName,
            onValueChange = onSquadNameChange,
            label = { Text("Squad Name") },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Groups,
                    contentDescription = null,
                    tint = GnitsOrange
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GnitsOrange,
                unfocusedBorderColor = CardBorder,
                focusedLabelColor = GnitsOrange,
                cursorColor = GnitsOrange
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Captain Name
        OutlinedTextField(
            value = captainName,
            onValueChange = onCaptainNameChange,
            label = { Text("Captain Name") },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    tint = GnitsOrange
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GnitsOrange,
                unfocusedBorderColor = CardBorder,
                focusedLabelColor = GnitsOrange,
                cursorColor = GnitsOrange
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Captain Phone
        OutlinedTextField(
            value = captainPhone,
            onValueChange = { if (it.length <= 10) onCaptainPhoneChange(it) },
            label = { Text("Captain Phone") },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Phone,
                    contentDescription = null,
                    tint = GnitsOrange
                )
            },
            supportingText = {
                Text(
                    text = "${captainPhone.length}/10",
                    style = SportFlowTheme.typography.bodySmall,
                    color = if (captainPhone.length >= 10) SuccessGreen else TextTertiary
                )
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GnitsOrange,
                unfocusedBorderColor = CardBorder,
                focusedLabelColor = GnitsOrange,
                cursorColor = GnitsOrange
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Squad Size Selector
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = OffWhite,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Group,
                            contentDescription = null,
                            tint = GnitsOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Squad Size",
                            style = SportFlowTheme.typography.labelLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "$squadSize ${if (squadSize == 1) "player" else "players"}",
                        style = SportFlowTheme.typography.headlineSmall,
                        color = GnitsOrange,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 2, 3, 4, 5, 6).forEach { size ->
                        val isSelected = squadSize == size
                        Surface(
                            onClick = { onSquadSizeChange(size) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) GnitsOrange else PureWhite,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.5.dp,
                                color = if (isSelected) GnitsOrange else CardBorder
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$size",
                                    style = SportFlowTheme.typography.headlineSmall,
                                    color = if (isSelected) Color.White else TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CardBorder)
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Back",
                    style = SportFlowTheme.typography.labelLarge,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = onContinue,
                enabled = isFormValid,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GnitsOrange,
                    disabledContainerColor = CardBorder
                )
            ) {
                Text(
                    "Continue",
                    style = SportFlowTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    Icons.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// STEP 3: CONFIRMATION
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

@Composable
private fun ConfirmationStep(
    match: Match,
    squadName: String,
    captainName: String,
    captainPhone: String,
    squadSize: Int,
    currentSquadCount: Int,
    maxSquads: Int,
    isSubmitting: Boolean,
    onBack: () -> Unit,
    onConfirm: () -> Unit
) {
    val isCapacityFull = maxSquads > 0 && currentSquadCount >= maxSquads

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Success Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    Brush.linearGradient(
                        colors = listOf(GnitsOrange, GnitsOrangeDark)
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Confirm Registration",
            style = SportFlowTheme.typography.headlineLarge,
            color = TextPrimary,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Review your details before submitting",
            style = SportFlowTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Match Details Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = GnitsOrangeLight,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Event Details",
                        style = SportFlowTheme.typography.labelLarge,
                        color = GnitsOrangeDark,
                        fontWeight = FontWeight.Bold
                    )
                    SportTypeChip(sportType = match.sportType)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${match.teamA} vs ${match.teamB}",
                    style = SportFlowTheme.typography.headlineSmall,
                    color = GnitsOrangeDark,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.LocationOn,
                        contentDescription = null,
                        tint = GnitsOrangeDark.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = match.venue,
                        style = SportFlowTheme.typography.bodySmall,
                        color = GnitsOrangeDark.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Squad Details Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = OffWhite,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Your Squad Details",
                    style = SportFlowTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(14.dp))

                ConfirmationRow(
                    icon = Icons.Outlined.Groups,
                    label = "Squad Name",
                    value = squadName
                )
                Spacer(modifier = Modifier.height(10.dp))
                ConfirmationRow(
                    icon = Icons.Outlined.Person,
                    label = "Captain",
                    value = captainName
                )
                Spacer(modifier = Modifier.height(10.dp))
                ConfirmationRow(
                    icon = Icons.Outlined.Phone,
                    label = "Phone",
                    value = captainPhone
                )
                Spacer(modifier = Modifier.height(10.dp))
                ConfirmationRow(
                    icon = Icons.Outlined.Group,
                    label = "Squad Size",
                    value = "$squadSize ${if (squadSize == 1) "player" else "players"}"
                )
            }
        }

        if (isCapacityFull) {
            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = LiveRedBg,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = ErrorRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Squad capacity is full. Registration may not be possible.",
                        style = SportFlowTheme.typography.bodySmall,
                        color = ErrorRed,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                enabled = !isSubmitting,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, CardBorder)
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Back",
                    style = SportFlowTheme.typography.labelLarge,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = onConfirm,
                enabled = !isSubmitting && !isCapacityFull,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessGreen,
                    disabledContainerColor = CardBorder
                )
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Submitting...",
                        style = SportFlowTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        Icons.Filled.HowToReg,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Confirm & Register",
                        style = SportFlowTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmationRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon,
                contentDescription = null,
                tint = GnitsOrange,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                style = SportFlowTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        Text(
            text = value,
            style = SportFlowTheme.typography.bodyMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold
        )
    }
}
