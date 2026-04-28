@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sportflow.app.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sportflow.app.data.model.*
import com.sportflow.app.ui.theme.*
import com.sportflow.app.ui.components.StepProgressBar

/**
 * 3-Step Advanced Registration Bottom Sheet
 * Step 1: Basic Info (Roll Number, Email, Department, Year)
 * Step 2: Sport-Specific Role Selection
 * Step 3: Squad Details (for team sports)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedRegistrationBottomSheet(
    match: Match,
    currentUser: SportUser?,
    onDismiss: () -> Unit,
    onRegister: (RegistrationData) -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) }
    
    // Step 1 data
    var rollNumber by remember { mutableStateOf(currentUser?.rollNumber ?: "") }
    var email by remember { mutableStateOf(currentUser?.email ?: "") }
    var selectedDepartment by remember { mutableStateOf(currentUser?.department ?: "") }
    var selectedYear by remember { mutableStateOf(currentUser?.yearOfStudy ?: "") }
    
    // Step 2 data
    var selectedRole by remember { mutableStateOf("") }
    
    // Step 3 data (team sports only)
    var squadName by remember { mutableStateOf("") }
    var captainName by remember { mutableStateOf(currentUser?.displayName ?: "") }
    var captainPhone by remember { mutableStateOf("") }
    var roster by remember {
        mutableStateOf(
            listOf(
                SquadPlayer(
                    name = currentUser?.displayName ?: "",
                    rollNumber = currentUser?.rollNumber ?: "",
                    role = ""
                )
            )
        )
    }
    var badmintonMode by remember { mutableStateOf(RegistrationKind.BADMINTON_SINGLES) }
    var partnerName by remember { mutableStateOf("") }
    var partnerRollNumber by remember { mutableStateOf("") }
    
    val sportType = SportType.fromString(match.sportType)
    val isTeamSport = when (sportType) {
        SportType.FOOTBALL, SportType.CRICKET, SportType.BASKETBALL, 
        SportType.VOLLEYBALL, SportType.KABADDI -> true
        else -> false
    }
    val isPairSport = sportType == SportType.BADMINTON || sportType == SportType.TABLE_TENNIS
    
    // For pair sports, skip role selection in Step 2 (it's handled in Step 3)
    val maxSteps = when {
        isPairSport -> 2  // Step 1: Basic Info, Step 2: Singles/Doubles selection
        isTeamSport -> 3  // Step 1: Basic Info, Step 2: Role, Step 3: Squad
        else -> 2         // Step 1: Basic Info, Step 2: Role
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SoftWhite,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Register for ${match.sportType}",
                    style = SportFlowTheme.typography.headlineMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = TextSecondary
                    )
                }
            }
            
            Text(
                text = "${match.teamA} vs ${match.teamB}",
                style = SportFlowTheme.typography.bodyMedium,
                color = TextSecondary
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Step progress bar with indicators
            StepProgressBar(
                currentStep = currentStep,
                totalSteps = maxSteps,
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Step content
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                },
                label = "step_transition"
            ) { step ->
                when {
                    step == 1 -> Step1BasicInfo(
                        rollNumber = rollNumber,
                        onRollNumberChange = { rollNumber = it },
                        email = email,
                        onEmailChange = { email = it },
                        selectedDepartment = selectedDepartment,
                        onDepartmentChange = { selectedDepartment = it },
                        selectedYear = selectedYear,
                        onYearChange = { selectedYear = it }
                    )
                    step == 2 && isPairSport -> {
                        // For pair sports, Step 2 is Singles/Doubles selection
                        Step2PairSportSelection(
                            sportType = match.sportType,
                            mode = badmintonMode,
                            onModeChange = { badmintonMode = it },
                            partnerName = partnerName,
                            onPartnerNameChange = { partnerName = it },
                            partnerRollNumber = partnerRollNumber,
                            onPartnerRollNumberChange = { partnerRollNumber = it }
                        )
                    }
                    step == 2 && !isPairSport -> {
                        // For team/individual sports, Step 2 is role selection
                        Step2RoleSelection(
                            sportType = match.sportType,
                            selectedRole = selectedRole,
                            onRoleChange = { selectedRole = it }
                        )
                    }
                    step == 3 && isTeamSport -> {
                        Step3SquadDetails(
                            sportType = match.sportType,
                            squadName = squadName,
                            onSquadNameChange = { squadName = it },
                            captainName = captainName,
                            onCaptainNameChange = { captainName = it },
                            captainPhone = captainPhone,
                            onCaptainPhoneChange = { captainPhone = it },
                            roster = roster,
                            onRosterChange = { roster = it }
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Navigation buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentStep > 1) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = GnitsOrange
                        )
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Back")
                    }
                    Spacer(Modifier.width(12.dp))
                }
                
                Button(
                    onClick = {
                        if (currentStep < maxSteps) {
                            currentStep++
                        } else {
                            // Submit registration
                            val normalizedRoster = roster.mapIndexed { index, player ->
                                player.copy(
                                    name = player.name.ifBlank { if (index == 0) captainName else player.name },
                                    rollNumber = player.rollNumber.ifBlank { if (index == 0) rollNumber else player.rollNumber },
                                    role = player.role.ifBlank { if (index == 0) selectedRole else player.role }
                                )
                            }.filter { it.name.isNotBlank() || it.rollNumber.isNotBlank() || it.role.isNotBlank() }

                            val data = RegistrationData(
                                rollNumber = rollNumber,
                                email = email,
                                department = selectedDepartment,
                                yearOfStudy = selectedYear,
                                sportRole = selectedRole,
                                squadName = squadName,
                                teamName = squadName,
                                captainName = captainName,
                                captainPhone = captainPhone,
                                registrationKind = when {
                                    isTeamSport -> RegistrationKind.TEAM
        isPairSport -> badmintonMode
                                    else -> RegistrationKind.INDIVIDUAL
                                },
                                roster = if (isTeamSport) normalizedRoster else emptyList(),
                                partnerName = partnerName,
                                partnerRollNumber = partnerRollNumber
                            )
                            onRegister(data)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = when {
                        currentStep == 1 -> rollNumber.isNotBlank() && email.isNotBlank() &&
                             selectedDepartment.isNotBlank() && selectedYear.isNotBlank()
                        currentStep == 2 && isPairSport -> {
                            badmintonMode == RegistrationKind.BADMINTON_SINGLES ||
                                (partnerName.isNotBlank() && partnerRollNumber.isNotBlank())
                        }
                        currentStep == 2 && !isPairSport -> selectedRole.isNotBlank()
                        currentStep == 3 && isTeamSport -> {
                            squadName.isNotBlank() && captainName.isNotBlank() &&
                                captainPhone.isNotBlank() && roster.isNotEmpty() &&
                                roster.all { player ->
                                    player.name.isNotBlank() &&
                                        player.rollNumber.isNotBlank() &&
                                        (player.role.isNotBlank() || (roster.indexOf(player) == 0 && selectedRole.isNotBlank()))
                                }
                        }
                        else -> false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GnitsOrange,
                        contentColor = SoftWhite
                    )
                ) {
                    Text(if (currentStep < maxSteps) "Next" else "Register")
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = if (currentStep < maxSteps) Icons.Filled.ArrowForward 
                                      else Icons.Filled.Check,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Step1BasicInfo(
    rollNumber: String,
    onRollNumberChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    selectedDepartment: String,
    onDepartmentChange: (String) -> Unit,
    selectedYear: String,
    onYearChange: (String) -> Unit
) {
    Column {
        Text(
            text = "Step 1: Basic Information",
            style = SportFlowTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        
        OutlinedTextField(
            value = rollNumber,
            onValueChange = onRollNumberChange,
            label = { Text("Roll Number") },
            leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GnitsOrange,
                focusedLabelColor = GnitsOrange,
                cursorColor = GnitsOrange
            )
        )
        
        Spacer(Modifier.height(12.dp))
        
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text("Email") },
            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GnitsOrange,
                focusedLabelColor = GnitsOrange,
                cursorColor = GnitsOrange
            )
        )
        
        Spacer(Modifier.height(12.dp))
        
        // Department dropdown
        var departmentExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = departmentExpanded,
            onExpandedChange = { departmentExpanded = it }
        ) {
            OutlinedTextField(
                value = selectedDepartment,
                onValueChange = {},
                readOnly = true,
                label = { Text("Department") },
                leadingIcon = { Icon(Icons.Filled.School, contentDescription = null) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = departmentExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GnitsOrange,
                    focusedLabelColor = GnitsOrange
                )
            )
            ExposedDropdownMenu(
                expanded = departmentExpanded,
                onDismissRequest = { departmentExpanded = false }
            ) {
                GnitsDepartment.entries.forEach { dept ->
                    DropdownMenuItem(
                        text = { Text(dept.displayName) },
                        onClick = {
                            onDepartmentChange(dept.name)
                            departmentExpanded = false
                        }
                    )
                }
            }
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Year dropdown
        var yearExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = yearExpanded,
            onExpandedChange = { yearExpanded = it }
        ) {
            OutlinedTextField(
                value = GnitsYear.fromCode(selectedYear)?.displayName ?: selectedYear,
                onValueChange = {},
                readOnly = true,
                label = { Text("Year of Study") },
                leadingIcon = { Icon(Icons.Filled.CalendarToday, contentDescription = null) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = yearExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GnitsOrange,
                    focusedLabelColor = GnitsOrange
                )
            )
            ExposedDropdownMenu(
                expanded = yearExpanded,
                onDismissRequest = { yearExpanded = false }
            ) {
                GnitsYear.entries.forEach { year ->
                    DropdownMenuItem(
                        text = { Text(year.displayName) },
                        onClick = {
                            onYearChange(year.name)
                            yearExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Step2RoleSelection(
    sportType: String,
    selectedRole: String,
    onRoleChange: (String) -> Unit
) {
    val roles = SportRoles.getRoles(sportType)

    Column {
        Text(
            text = "Step 2: Select Your Role",
            style = SportFlowTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Choose your playing position for $sportType",
            style = SportFlowTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(16.dp))
        
        roles.forEach { role ->
            RoleSelectionCard(
                role = role,
                isSelected = selectedRole == role,
                onClick = { onRoleChange(role) }
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RoleSelectionCard(
    role: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) GnitsOrangeLight else SoftWhite
        ),
        border = if (isSelected) 
            androidx.compose.foundation.BorderStroke(2.dp, GnitsOrange)
        else 
            androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = role,
                style = SportFlowTheme.typography.bodyLarge,
                color = if (isSelected) GnitsOrange else TextPrimary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = GnitsOrange
                )
            }
        }
    }
}

@Composable
private fun Step3SquadDetails(
    sportType: String,
    squadName: String,
    onSquadNameChange: (String) -> Unit,
    captainName: String,
    onCaptainNameChange: (String) -> Unit,
    captainPhone: String,
    onCaptainPhoneChange: (String) -> Unit,
    roster: List<SquadPlayer>,
    onRosterChange: (List<SquadPlayer>) -> Unit
) {
    val roles = SportRoles.getRoles(sportType)
    Column {
        Text(
            text = "Step 3: Create Team",
            style = SportFlowTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Provide your team information",
            style = SportFlowTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(16.dp))
        
        OutlinedTextField(
            value = squadName,
            onValueChange = onSquadNameChange,
            label = { Text("Squad/Team Name") },
            leadingIcon = { Icon(Icons.Filled.Groups, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GnitsOrange,
                focusedLabelColor = GnitsOrange,
                cursorColor = GnitsOrange
            )
        )
        
        Spacer(Modifier.height(12.dp))
        
        OutlinedTextField(
            value = captainName,
            onValueChange = onCaptainNameChange,
            label = { Text("Captain Name") },
            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GnitsOrange,
                focusedLabelColor = GnitsOrange,
                cursorColor = GnitsOrange
            )
        )
        
        Spacer(Modifier.height(12.dp))
        
        OutlinedTextField(
            value = captainPhone,
            onValueChange = onCaptainPhoneChange,
            label = { Text("Captain Phone Number") },
            leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = GnitsOrange,
                focusedLabelColor = GnitsOrange,
                cursorColor = GnitsOrange
            )
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Squad Roster",
            style = SportFlowTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        roster.forEachIndexed { index, player ->
            SquadPlayerEditor(
                index = index,
                player = player,
                roles = roles,
                canRemove = roster.size > 1,
                onChange = { updated ->
                    onRosterChange(roster.toMutableList().also { it[index] = updated })
                },
                onRemove = {
                    onRosterChange(roster.toMutableList().also { it.removeAt(index) })
                }
            )
            Spacer(Modifier.height(10.dp))
        }

        OutlinedButton(
            onClick = {
                onRosterChange(roster + SquadPlayer())
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = GnitsOrange)
        ) {
            Icon(Icons.Filled.PersonAdd, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add Player")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SquadPlayerEditor(
    index: Int,
    player: SquadPlayer,
    roles: List<String>,
    canRemove: Boolean,
    onChange: (SquadPlayer) -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = OffWhite,
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorder)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Player ${index + 1}",
                    style = SportFlowTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                if (canRemove) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove player", tint = ErrorRed)
                    }
                }
            }
            OutlinedTextField(
                value = player.name,
                onValueChange = { onChange(player.copy(name = it)) },
                label = { Text("Name") },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GnitsOrange)
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = player.rollNumber,
                onValueChange = { onChange(player.copy(rollNumber = it)) },
                label = { Text("Roll Number") },
                leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GnitsOrange)
            )
            Spacer(Modifier.height(8.dp))
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = it }
            ) {
                OutlinedTextField(
                    value = player.role,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Role") },
                    leadingIcon = { Icon(Icons.Filled.Sports, contentDescription = null) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GnitsOrange)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    roles.forEach { role ->
                        DropdownMenuItem(
                            text = { Text(role) },
                            onClick = {
                                onChange(player.copy(role = role))
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Step2PairSportSelection(
    sportType: String,
    mode: RegistrationKind,
    onModeChange: (RegistrationKind) -> Unit,
    partnerName: String,
    onPartnerNameChange: (String) -> Unit,
    partnerRollNumber: String,
    onPartnerRollNumberChange: (String) -> Unit
) {
    Column {
        Text(
            text = "Step 2: $sportType Entry",
            style = SportFlowTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = mode == RegistrationKind.BADMINTON_SINGLES,
                onClick = { onModeChange(RegistrationKind.BADMINTON_SINGLES) },
                label = { Text("Singles") },
                leadingIcon = if (mode == RegistrationKind.BADMINTON_SINGLES) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
            FilterChip(
                selected = mode == RegistrationKind.BADMINTON_DOUBLES,
                onClick = { onModeChange(RegistrationKind.BADMINTON_DOUBLES) },
                label = { Text("Doubles Pair") },
                leadingIcon = if (mode == RegistrationKind.BADMINTON_DOUBLES) {
                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }

        if (mode == RegistrationKind.BADMINTON_DOUBLES) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = partnerName,
                onValueChange = onPartnerNameChange,
                label = { Text("Partner Name") },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GnitsOrange)
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = partnerRollNumber,
                onValueChange = onPartnerRollNumberChange,
                label = { Text("Partner Roll Number") },
                leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GnitsOrange)
            )
        }
    }
}

/**
 * Data class to hold all registration form data
 */
data class RegistrationData(
    val rollNumber: String,
    val email: String,
    val department: String,
    val yearOfStudy: String,
    val sportRole: String,
    val squadName: String = "",
    val teamName: String = "",
    val captainName: String = "",
    val captainPhone: String = "",
    val registrationKind: RegistrationKind = RegistrationKind.INDIVIDUAL,
    val roster: List<SquadPlayer> = emptyList(),
    val partnerName: String = "",
    val partnerRollNumber: String = "",
    val partnerRole: String = ""
)
