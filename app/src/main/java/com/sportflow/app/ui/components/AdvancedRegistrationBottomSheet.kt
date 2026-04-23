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
    var captainName by remember { mutableStateOf("") }
    var captainPhone by remember { mutableStateOf("") }
    
    val isTeamSport = when (SportType.fromString(match.sportType)) {
        SportType.FOOTBALL, SportType.CRICKET, SportType.BASKETBALL, 
        SportType.VOLLEYBALL, SportType.KABADDI -> true
        else -> false
    }
    
    val maxSteps = if (isTeamSport) 3 else 2
    
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
                when (step) {
                    1 -> Step1BasicInfo(
                        rollNumber = rollNumber,
                        onRollNumberChange = { rollNumber = it },
                        email = email,
                        onEmailChange = { email = it },
                        selectedDepartment = selectedDepartment,
                        onDepartmentChange = { selectedDepartment = it },
                        selectedYear = selectedYear,
                        onYearChange = { selectedYear = it }
                    )
                    2 -> Step2RoleSelection(
                        sportType = match.sportType,
                        selectedRole = selectedRole,
                        onRoleChange = { selectedRole = it }
                    )
                    3 -> Step3SquadDetails(
                        squadName = squadName,
                        onSquadNameChange = { squadName = it },
                        captainName = captainName,
                        onCaptainNameChange = { captainName = it },
                        captainPhone = captainPhone,
                        onCaptainPhoneChange = { captainPhone = it }
                    )
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
                            val data = RegistrationData(
                                rollNumber = rollNumber,
                                email = email,
                                department = selectedDepartment,
                                yearOfStudy = selectedYear,
                                sportRole = selectedRole,
                                squadName = squadName,
                                captainName = captainName,
                                captainPhone = captainPhone
                            )
                            onRegister(data)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = when (currentStep) {
                        1 -> rollNumber.isNotBlank() && email.isNotBlank() && 
                             selectedDepartment.isNotBlank() && selectedYear.isNotBlank()
                        2 -> selectedRole.isNotBlank()
                        3 -> squadName.isNotBlank() && captainName.isNotBlank() && 
                             captainPhone.isNotBlank()
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
    squadName: String,
    onSquadNameChange: (String) -> Unit,
    captainName: String,
    onCaptainNameChange: (String) -> Unit,
    captainPhone: String,
    onCaptainPhoneChange: (String) -> Unit
) {
    Column {
        Text(
            text = "Step 3: Squad Details",
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
    val captainName: String = "",
    val captainPhone: String = ""
)
