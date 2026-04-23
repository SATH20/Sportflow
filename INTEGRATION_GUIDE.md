# GNITS SportFlow - Integration Guide

## Quick Start: Integrating the New Registration System

This guide shows you how to integrate the newly implemented 3-step registration system into your existing screens.

---

## Step 1: Update HomeFeedScreen.kt

Replace the existing registration button logic with the new advanced registration flow.

### Add ViewModel Injection

```kotlin
@Composable
fun HomeFeedScreen(
    navController: NavHostController,
    currentUser: SportUser?,
    isAdmin: Boolean
) {
    val homeViewModel: HomeViewModel = hiltViewModel()
    val registrationViewModel: RegistrationViewModel = hiltViewModel()  // ADD THIS
    
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val registrationState by registrationViewModel.uiState.collectAsStateWithLifecycle()  // ADD THIS
    
    // ... rest of your code
}
```

### Update Match Card Button Logic

Find your existing match card and replace the registration button:

```kotlin
@Composable
fun MatchCard(
    match: Match,
    registrationViewModel: RegistrationViewModel,
    registrationState: RegistrationUiState,
    onNavigateToLive: () -> Unit
) {
    var showRegistrationSheet by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        onClick = { if (match.status == MatchStatus.LIVE) onNavigateToLive() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ... existing match info display ...
            
            // Registration Button
            val isRegistered = registrationViewModel.isRegisteredFor(match.id)
            val isFull = match.currentSquadCount >= match.maxSquadSize && match.maxSquadSize > 0
            
            Button(
                onClick = {
                    if (isRegistered) {
                        showCancelDialog = true
                    } else if (!isFull) {
                        showRegistrationSheet = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isFull || isRegistered,
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isRegistered -> Color(0xFF4CAF50)  // Green
                        isFull -> Color.Gray
                        else -> GnitsOrange
                    }
                )
            ) {
                Icon(
                    imageVector = when {
                        isRegistered -> Icons.Filled.CheckCircle
                        isFull -> Icons.Filled.Block
                        else -> Icons.Filled.PersonAdd
                    },
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when {
                        isRegistered -> "Registered ✓"
                        isFull -> "FULL (${match.currentSquadCount}/${match.maxSquadSize})"
                        else -> "Register"
                    }
                )
            }
            
            // Show eligibility text if restrictions exist
            if (match.allowedDepartments.isNotEmpty() || match.allowedYears.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = match.eligibilityText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
    
    // Registration Bottom Sheet
    if (showRegistrationSheet) {
        AdvancedRegistrationBottomSheet(
            match = match,
            currentUser = registrationState.currentUser,
            onDismiss = { 
                showRegistrationSheet = false
                registrationViewModel.clearMessages()
            },
            onRegister = { data ->
                registrationViewModel.registerForMatch(match, data)
                showRegistrationSheet = false
            }
        )
    }
    
    // Cancel Confirmation Dialog
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Registration?") },
            text = { 
                Text("Are you sure you want to cancel your registration for ${match.teamA} vs ${match.teamB}?") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        registrationViewModel.cancelRegistration(
                            matchId = match.id,
                            matchName = "${match.teamA} vs ${match.teamB}"
                        )
                        showCancelDialog = false
                    }
                ) {
                    Text("Yes, Cancel", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Keep Registration")
                }
            }
        )
    }
}
```

### Add Snackbar for Messages

Add this at the bottom of your HomeFeedScreen composable:

```kotlin
// Show success/error messages
val snackbarHostState = remember { SnackbarHostState() }

LaunchedEffect(registrationState.successMessage) {
    registrationState.successMessage?.let { message ->
        snackbarHostState.showSnackbar(message)
        registrationViewModel.clearMessages()
    }
}

LaunchedEffect(registrationState.error) {
    registrationState.error?.let { error ->
        snackbarHostState.showSnackbar(
            message = error,
            duration = SnackbarDuration.Long
        )
        registrationViewModel.clearMessages()
    }
}

Scaffold(
    snackbarHost = { SnackbarHost(snackbarHostState) }
) {
    // ... your existing content
}
```

---

## Step 2: Update MyMatchesScreen.kt

Show only the user's registered matches with cancel functionality.

```kotlin
@Composable
fun MyMatchesScreen(
    navController: NavHostController,
    currentUserRole: UserRole
) {
    val registrationViewModel: RegistrationViewModel = hiltViewModel()
    val registrationState by registrationViewModel.uiState.collectAsStateWithLifecycle()
    
    // Get all matches
    val repository: SportFlowRepository = // inject via hiltViewModel or parameter
    val myMatches by repository.observeMyRegisteredMatchesRealtime()
        .collectAsState(initial = emptyList())
    
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Upcoming", "Live", "Completed")
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SoftWhite,
            contentColor = GnitsOrange
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }
        
        // Filtered matches
        val filteredMatches = when (selectedTab) {
            0 -> myMatches.filter { it.status == MatchStatus.SCHEDULED }
            1 -> myMatches.filter { it.status == MatchStatus.LIVE || it.status == MatchStatus.HALFTIME }
            2 -> myMatches.filter { it.status == MatchStatus.COMPLETED }
            else -> emptyList()
        }
        
        if (filteredMatches.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.EventBusy,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.Gray
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "No ${tabs[selectedTab].lowercase()} matches",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredMatches) { match ->
                    MyMatchCard(
                        match = match,
                        onCancel = {
                            registrationViewModel.cancelRegistration(
                                matchId = match.id,
                                matchName = "${match.teamA} vs ${match.teamB}"
                            )
                        },
                        onNavigateToLive = {
                            navController.navigate(Screen.LiveMatch.route)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MyMatchCard(
    match: Match,
    onCancel: () -> Unit,
    onNavigateToLive: () -> Unit
) {
    var showCancelDialog by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (match.status == MatchStatus.LIVE) onNavigateToLive() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Sport badge
            Surface(
                color = GnitsOrangeLight,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = match.sportType,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = GnitsOrange,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(Modifier.height(8.dp))
            
            // Match info
            Text(
                text = "${match.teamA} vs ${match.teamB}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(Modifier.height(4.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.Gray
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = match.venue,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            
            Spacer(Modifier.height(4.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.Gray
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = match.scheduledTime?.toDate()?.toString() ?: "TBD",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            
            // Status badge
            Spacer(Modifier.height(8.dp))
            Surface(
                color = when (match.status) {
                    MatchStatus.LIVE -> Color(0xFF4CAF50)
                    MatchStatus.SCHEDULED -> Color(0xFF2196F3)
                    MatchStatus.COMPLETED -> Color.Gray
                    else -> Color.Gray
                },
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = match.status.name,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Cancel button (only for scheduled matches)
            if (match.status == MatchStatus.SCHEDULED) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { showCancelDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Red
                    )
                ) {
                    Icon(Icons.Filled.Cancel, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Cancel Registration")
                }
            }
        }
    }
    
    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text("Cancel Registration?") },
            text = { 
                Text("Are you sure? This will open up a spot for other students.") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onCancel()
                        showCancelDialog = false
                    }
                ) {
                    Text("Yes, Cancel", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Keep Registration")
                }
            }
        )
    }
}
```

---

## Step 3: Test the Complete Flow

### Test Scenario 1: Successful Registration
1. Open Home Screen
2. Find a match with available spots
3. Tap "Register" button
4. Complete Step 1: Enter roll number, email, select department and year
5. Tap "Next"
6. Complete Step 2: Select your sport role (e.g., "Bowler" for Cricket)
7. Tap "Next" (or "Register" if not a team sport)
8. Complete Step 3 (team sports only): Enter squad name, captain name, captain phone
9. Tap "Register"
10. Verify success message appears
11. Verify button changes to "Registered ✓"
12. Check Firestore:
    - `gnits_matches/{matchId}/registrations/{userId}` document created
    - `gnits_registrations/{userId}_{matchId}` document created (Admin Data Bridge)
    - `currentSquadCount` incremented on match document

### Test Scenario 2: Squad Full
1. Create a match with `maxSquadSize: 2`
2. Register 2 users
3. Try to register a 3rd user
4. Verify "FULL" badge appears
5. Verify registration button is disabled
6. Verify error message: "Squad is full (2/2 slots taken)"

### Test Scenario 3: Cancellation
1. Register for a match
2. Go to "My Matches" screen
3. Find the registered match
4. Tap "Cancel Registration"
5. Confirm cancellation
6. Verify success message
7. Verify match disappears from "My Matches"
8. Verify "Register" button reappears on Home Screen
9. Check Firestore:
    - Registration document deleted
    - `currentSquadCount` decremented
    - "Spot Opened" notification triggered

### Test Scenario 4: Eligibility Restrictions
1. Create a match with `allowedDepartments: ["CSE", "IT"]`
2. Try to register as an ECE student
3. Verify error: "Only CSE, IT department(s) may register"
4. Try to register as a CSE student
5. Verify registration succeeds

### Test Scenario 5: Real-Time Sync
1. Open app on Device A
2. Open app on Device B (same user)
3. Register on Device A
4. Verify button changes to "Registered ✓" on Device B instantly (no refresh needed)
5. Cancel on Device B
6. Verify button changes to "Register" on Device A instantly

---

## Step 4: Add Missing Repository Methods

Some methods referenced in the ViewModels need to be added to the repository. Add these to `SportFlowRepository.kt`:

```kotlin
// Add after the existing observeMyRegisteredMatchesRealtime() method

/**
 * Real-time stream of all registrations (Admin Data Bridge).
 * Used by Admin Dashboard to show all student registrations.
 */
fun observeAllRegistrations(): Flow<List<Registration>> = callbackFlow {
    val listener = firestore.collection(GNITS_REGISTRATIONS)
        .orderBy("registeredAt", Query.Direction.DESCENDING)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            val registrations = snapshot?.documents?.mapNotNull {
                it.toObject(Registration::class.java)?.copy(id = it.id)
            } ?: emptyList()
            trySend(registrations)
        }
    awaitClose { listener.remove() }
}

/**
 * Real-time count of unseen registrations (for Admin "New Entry" badge).
 */
fun observeNewRegistrationCount(): Flow<Int> = callbackFlow {
    val listener = firestore.collection(GNITS_REGISTRATIONS)
        .whereEqualTo("seen", false)
        .addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(0)
                return@addSnapshotListener
            }
            trySend(snapshot?.size() ?: 0)
        }
    awaitClose { listener.remove() }
}

/**
 * Mark a registration as seen by admin.
 */
suspend fun markRegistrationAsSeen(registrationId: String) {
    firestore.collection(GNITS_REGISTRATIONS)
        .document(registrationId)
        .update("seen", true)
        .await()
}

/**
 * Admin accepts a registration (no-op for now, already CONFIRMED).
 * Future: could change status from PENDING → CONFIRMED.
 */
suspend fun acceptRegistration(registrationId: String) {
    firestore.collection(GNITS_REGISTRATIONS)
        .document(registrationId)
        .update(mapOf(
            "status" to RegistrationStatus.CONFIRMED.name,
            "seen" to true
        ))
        .await()
}

/**
 * Admin denies a registration and deletes it.
 */
suspend fun denyRegistration(registrationId: String, reason: String) {
    // Parse registrationId to get userId and matchId
    val parts = registrationId.split("_")
    if (parts.size != 2) return
    
    val userId = parts[0]
    val matchId = parts[1]
    
    // Delete from both collections
    firestore.collection(GNITS_REGISTRATIONS)
        .document(registrationId)
        .delete()
        .await()
    
    firestore.collection(MATCHES_COLLECTION)
        .document(matchId)
        .collection(REGISTRATIONS_COLLECTION)
        .document(userId)
        .delete()
        .await()
    
    // Decrement counters
    firestore.collection(MATCHES_COLLECTION)
        .document(matchId)
        .update(mapOf(
            "currentSquadCount" to FieldValue.increment(-1),
            "registrationCount" to FieldValue.increment(-1)
        ))
        .await()
    
    // Send notification to user
    pushNotificationTrigger(
        type = "registration_denied",
        title = "Registration Denied",
        body = "Your registration was denied. Reason: $reason",
        matchId = matchId,
        topic = "user_$userId"
    )
}

/**
 * Apply a manual fixture edit (time/venue/team changes).
 */
suspend fun applyManualFixtureEdit(edit: ManualFixtureEdit) {
    val updates = mutableMapOf<String, Any>()
    
    edit.newScheduledTime?.let { updates["scheduledTime"] = it }
    edit.newVenue?.let { updates["venue"] = it }
    edit.newTeamA?.let { updates["teamA"] = it }
    edit.newTeamB?.let { updates["teamB"] = it }
    edit.newTeamADepartment?.let { updates["teamADepartment"] = it }
    edit.newTeamBDepartment?.let { updates["teamBDepartment"] = it }
    
    if (updates.isNotEmpty()) {
        firestore.collection(MATCHES_COLLECTION)
            .document(edit.matchId)
            .update(updates)
            .await()
        
        // Create an update log entry
        firestore.collection("gnits_updates").add(mapOf(
            "type" to "fixture_change",
            "title" to "Fixture Updated",
            "body" to "Match details have been changed by admin",
            "matchId" to edit.matchId,
            "timestamp" to com.google.firebase.Timestamp.now()
        )).await()
    }
}
```

---

## Step 5: Verify Firestore Structure

After testing, your Firestore should look like this:

```
gnits_matches/
  {matchId}/
    teamA: "CSE"
    teamB: "IT"
    sportType: "Cricket"
    maxSquadSize: 15
    currentSquadCount: 3
    allowedDepartments: ["CSE", "IT", "ECE"]
    allowedYears: ["SECOND_YEAR", "THIRD_YEAR"]
    ...
    
    registrations/
      {userId1}/
        rollNumber: "20CS001"
        email: "student@gnits.ac.in"
        department: "CSE"
        yearOfStudy: "SECOND_YEAR"
        sportRole: "Bowler"
        squadName: "CSE Strikers"
        captainName: "John Doe"
        captainPhone: "9876543210"
        status: "CONFIRMED"
        registeredAt: Timestamp
      
      {userId2}/
        ...

gnits_registrations/
  {userId1}_{matchId}/
    (same fields as above, plus:)
    matchName: "CSE vs IT"
    sportType: "Cricket"
    seen: false

gnits_users/
  {userId}/
    displayName: "John Doe"
    email: "student@gnits.ac.in"
    rollNumber: "20CS001"
    department: "CSE"
    yearOfStudy: "SECOND_YEAR"
    preferredSportRole: "Bowler"
    role: "PLAYER"
```

---

## Troubleshooting

### Issue: Button doesn't update after registration
**Solution**: Ensure you're using `observeMyRegisteredMatchIds()` Flow, not a one-shot query.

### Issue: Registration succeeds but button still shows "Register"
**Solution**: Check that the registration document is being created with the correct `matchId` field.

### Issue: Squad full check not working
**Solution**: Verify `maxSquadSize` and `currentSquadCount` fields exist on the match document.

### Issue: Eligibility check fails incorrectly
**Solution**: Ensure department/year codes match exactly (case-sensitive). Use enum names, not display names.

### Issue: Cancellation doesn't trigger "Spot Opened" notification
**Solution**: Check that `maxSquadSize > 0` and `previousCount >= maxSquadSize` in the cancellation logic.

---

## Next Steps

After integrating the registration system:

1. **Build Admin Registration Management Screen** - See IMPLEMENTATION_STATUS.md Phase 2
2. **Add Player Scorecard Tab** - See IMPLEMENTATION_STATUS.md Phase 4.3
3. **Complete Round Robin Algorithm** - See IMPLEMENTATION_STATUS.md Phase 3.1
4. **Build Notification Center** - See IMPLEMENTATION_STATUS.md Phase 5.1
5. **End-to-End Testing** - See IMPLEMENTATION_STATUS.md Phase 6

---

## Support

For detailed implementation specs, see:
- `GNITS_PRODUCTION_IMPLEMENTATION_PLAN.md` - Complete feature specifications
- `IMPLEMENTATION_STATUS.md` - Current progress and remaining work
- `Models.kt` - All data structures and enums
- `SportFlowRepository.kt` - All available repository methods

Happy coding! 🚀
