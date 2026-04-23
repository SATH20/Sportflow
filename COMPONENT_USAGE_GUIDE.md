# GNITS SPORTFLOW — COMPONENT USAGE GUIDE

## Quick Reference for Premium Components

### 1. GlassmorphicCard

**Purpose**: Modern frosted glass effect card for all content containers

**Basic Usage**:
```kotlin
GlassmorphicCard(
    modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Card Content")
    }
}
```

**With Click Handler**:
```kotlin
GlassmorphicCard(
    modifier = Modifier.fillMaxWidth(),
    onClick = { 
        // Handle click
        navController.navigate("details")
    }
) {
    // Content
}
```

**Real Example - Match Card**:
```kotlin
GlassmorphicCard(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
) {
    Column(modifier = Modifier.padding(16.dp)) {
        // Sport badge
        Surface(color = GnitsOrangeLight, shape = RoundedCornerShape(8.dp)) {
            Text(match.sportType, modifier = Modifier.padding(8.dp))
        }
        
        Spacer(Modifier.height(12.dp))
        
        // Match info
        Text("${match.teamA} vs ${match.teamB}", 
             style = SportFlowTheme.typography.headlineMedium)
        
        // Register button
        StateAwareButton(
            state = buttonState,
            onClick = { /* register */ }
        )
    }
}
```

---

### 2. PremiumButton

**Purpose**: High-intent action button with GNITS Orange branding

**Basic Usage**:
```kotlin
PremiumButton(
    text = "Register",
    onClick = { /* handle click */ },
    modifier = Modifier.fillMaxWidth()
)
```

**With Icon**:
```kotlin
PremiumButton(
    text = "Accept",
    onClick = { /* accept */ },
    icon = Icons.Filled.Check,
    containerColor = SuccessGreen,
    modifier = Modifier.fillMaxWidth()
)
```

**Loading State**:
```kotlin
PremiumButton(
    text = "Submitting...",
    onClick = { /* disabled during loading */ },
    isLoading = true,
    enabled = false,
    modifier = Modifier.fillMaxWidth()
)
```

**Real Example - Admin Accept Button**:
```kotlin
PremiumButton(
    text = "Accept",
    onClick = { viewModel.acceptRegistration(registration.id) },
    icon = Icons.Filled.Check,
    containerColor = SuccessGreen,
    modifier = Modifier
        .weight(1f)
        .height(44.dp)
)
```

---

### 3. StateAwareButton

**Purpose**: Smart button that changes appearance based on registration state

**Basic Usage**:
```kotlin
StateAwareButton(
    state = when {
        isRegistered -> ButtonState.REGISTERED
        isFull -> ButtonState.FULL
        !isEligible -> ButtonState.INELIGIBLE
        else -> ButtonState.REGISTER
    },
    onClick = { /* handle click */ },
    modifier = Modifier.fillMaxWidth()
)
```

**Real Example - Home Feed**:
```kotlin
StateAwareButton(
    state = when {
        isRegistered -> ButtonState.REGISTERED
        isFull -> ButtonState.FULL
        !isEligible -> ButtonState.INELIGIBLE
        else -> ButtonState.REGISTER
    },
    onClick = {
        if (isRegistered) {
            matchToCancel = match
            showCancelDialog = true
        } else {
            selectedMatch = match
            showRegistrationSheet = true
        }
    },
    modifier = Modifier.fillMaxWidth()
)
```

---

### 4. PulseAnimation

**Purpose**: Draw attention to cards when a spot becomes available

**Basic Usage**:
```kotlin
PulseAnimation(
    modifier = Modifier.fillMaxWidth(),
    color = GnitsOrange
) {
    GlassmorphicCard {
        // Card content
    }
}
```

**Real Example - Squad Full Notification**:
```kotlin
val shouldPulse = isFull && !isRegistered && isEligible

if (shouldPulse) {
    PulseAnimation(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = GnitsOrange
    ) {
        GlassmorphicCard {
            // Match card content
        }
    }
} else {
    GlassmorphicCard(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
        // Match card content
    }
}
```

---

### 5. StepProgressBar

**Purpose**: Show progress through multi-step registration form

**Basic Usage**:
```kotlin
StepProgressBar(
    currentStep = currentStep,
    totalSteps = 3,
    modifier = Modifier.fillMaxWidth()
)
```

**Real Example - Registration Bottom Sheet**:
```kotlin
Column(modifier = Modifier.padding(24.dp)) {
    // Header
    Text("Register for ${match.sportType}")
    
    Spacer(Modifier.height(8.dp))
    
    // Step progress bar
    StepProgressBar(
        currentStep = currentStep,
        totalSteps = maxSteps,
        modifier = Modifier.fillMaxWidth()
    )
    
    Spacer(Modifier.height(24.dp))
    
    // Step content with animation
    AnimatedContent(
        targetState = currentStep,
        transitionSpec = {
            slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it } + fadeOut()
        }
    ) { step ->
        when (step) {
            1 -> Step1BasicInfo(...)
            2 -> Step2RoleSelection(...)
            3 -> Step3SquadDetails(...)
        }
    }
}
```

---

### 6. CricketScorecardChip

**Purpose**: Display cricket-specific scorecard data

**Basic Usage**:
```kotlin
CricketScorecardChip(
    runs = 45,
    wickets = 3,
    overs = "12.4"
)
```

**Real Example - Player Profile**:
```kotlin
Column {
    Text("Match Statistics", style = SportFlowTheme.typography.headlineSmall)
    
    CricketScorecardChip(
        runs = playerScorecard.runs,
        wickets = playerScorecard.wickets,
        overs = playerScorecard.overs,
        modifier = Modifier.fillMaxWidth()
    )
}
```

---

### 7. BadmintonScorecardChip

**Purpose**: Display badminton set-by-set scores

**Basic Usage**:
```kotlin
BadmintonScorecardChip(
    set1 = "21-18",
    set2 = "19-21",
    set3 = "21-15"
)
```

**Real Example - Match Result**:
```kotlin
Column {
    Text("Final Score", style = SportFlowTheme.typography.headlineSmall)
    
    BadmintonScorecardChip(
        set1 = match.set1Score,
        set2 = match.set2Score,
        set3 = match.set3Score,
        modifier = Modifier.fillMaxWidth()
    )
}
```

---

### 8. AdminDecisionPanel

**Purpose**: Display student info with Accept/Deny buttons for admin approval

**Basic Usage**:
```kotlin
AdminDecisionPanel(
    rollNumber = "21A91A0123",
    email = "student@gnits.ac.in",
    department = "CSE",
    onAccept = { viewModel.acceptRegistration(registration.id) },
    onDeny = { showDenyDialog = true }
)
```

**Real Example - Admin Approval Screen**:
```kotlin
if (registration.status == RegistrationStatus.PENDING) {
    Spacer(Modifier.height(8.dp))
    AdminDecisionPanel(
        rollNumber = registration.rollNumber,
        email = registration.email,
        department = registration.department,
        onAccept = {
            viewModel.acceptRegistration(registration.id)
            viewModel.markRegistrationAsSeen(registration.id)
        },
        onDeny = {
            selectedRegistration = registration
            showDenyDialog = true
        },
        modifier = Modifier.fillMaxWidth()
    )
}
```

---

### 9. TimeBlockSchedule

**Purpose**: Display day's match schedule for admin fixture editing

**Basic Usage**:
```kotlin
TimeBlockSchedule(
    timeSlots = listOf(
        TimeSlot("09:00 AM", "CSE vs IT", "Main Ground"),
        TimeSlot("11:00 AM", "ECE vs EEE", "Indoor Stadium"),
        TimeSlot("02:00 PM", "CSM vs AI&DS", "Main Ground")
    ),
    onTimeSlotClick = { slot ->
        // Show edit dialog
        selectedSlot = slot
        showEditDialog = true
    }
)
```

**Real Example - Fixture Editor**:
```kotlin
Column {
    Text("Today's Schedule", style = SportFlowTheme.typography.headlineSmall)
    
    TimeBlockSchedule(
        timeSlots = uiState.allMatches.map { match ->
            TimeSlot(
                time = match.scheduledTime?.toDate()?.toString() ?: "TBD",
                match = "${match.teamA} vs ${match.teamB}",
                venue = match.venue
            )
        },
        onTimeSlotClick = { slot ->
            selectedSlot = slot
            showEditDialog = true
        },
        modifier = Modifier.fillMaxWidth()
    )
}
```

---

## Color Usage Examples

### Using GNITS Orange
```kotlin
// Primary action button
PremiumButton(
    text = "Register",
    onClick = { /* ... */ },
    containerColor = GnitsOrange  // Default
)

// Text accent
Text(
    "Available Spots",
    color = GnitsOrange,
    fontWeight = FontWeight.Bold
)

// Icon accent
Icon(
    Icons.Filled.Star,
    tint = GnitsOrange
)

// Background
Surface(
    color = GnitsOrangeLight,  // Light background
    shape = RoundedCornerShape(8.dp)
) {
    Text("Featured Match")
}
```

### Using Semantic Colors
```kotlin
// Success state
Button(
    colors = ButtonDefaults.buttonColors(
        containerColor = SuccessGreen
    )
) {
    Text("Confirmed")
}

// Live state
Surface(color = LiveRed) {
    Text("LIVE", color = Color.White)
}

// Warning state
Surface(color = WarningAmber) {
    Text("Squad Nearly Full")
}

// Disabled state
Button(
    enabled = false,
    colors = ButtonDefaults.buttonColors(
        disabledContainerColor = Color.Gray.copy(alpha = 0.5f)
    )
) {
    Text("Unavailable")
}
```

---

## Typography Usage Examples

### Display Styles
```kotlin
// Screen title
Text(
    "GNITS Sports",
    style = SportFlowTheme.typography.displayLarge
)

// Section header
Text(
    "Upcoming Matches",
    style = SportFlowTheme.typography.displayMedium
)
```

### Headline Styles
```kotlin
// Card title
Text(
    "CSE vs IT",
    style = SportFlowTheme.typography.headlineLarge
)

// Subsection
Text(
    "Match Details",
    style = SportFlowTheme.typography.headlineSmall
)
```

### Body Styles
```kotlin
// Primary content
Text(
    "Main Ground, 2:00 PM",
    style = SportFlowTheme.typography.bodyMedium
)

// Secondary content
Text(
    "Cricket Tournament",
    style = SportFlowTheme.typography.bodySmall,
    color = TextSecondary
)
```

### Label Styles
```kotlin
// Button text
Text(
    "Register",
    style = SportFlowTheme.typography.labelLarge
)

// Badge text
Text(
    "NEW",
    style = SportFlowTheme.typography.labelSmall
)
```

### Special Styles
```kotlin
// Score display
Text(
    "45",
    style = SportFlowTheme.typography.scoreDisplay
)

// Timer
Text(
    "12:34",
    style = SportFlowTheme.typography.timerDisplay
)
```

---

## Animation Examples

### Slide Transition
```kotlin
AnimatedContent(
    targetState = currentStep,
    transitionSpec = {
        slideInHorizontally { it } + fadeIn() togetherWith
                slideOutHorizontally { -it } + fadeOut()
    }
) { step ->
    when (step) {
        1 -> Step1()
        2 -> Step2()
        3 -> Step3()
    }
}
```

### Fade Transition
```kotlin
AnimatedVisibility(
    visible = showDetails,
    enter = fadeIn(),
    exit = fadeOut()
) {
    DetailContent()
}
```

### Scale Animation
```kotlin
val scale by animateFloatAsState(
    targetValue = if (isPressed) 0.98f else 1f,
    label = "buttonScale"
)

Box(modifier = Modifier.graphicsLayer(scaleX = scale, scaleY = scale)) {
    // Content
}
```

---

## Best Practices

### ✅ DO
- Use GlassmorphicCard for all content containers
- Use StateAwareButton for registration buttons
- Use semantic colors (Green for success, Red for danger)
- Use GNITS Orange for primary actions
- Use PulseAnimation for important notifications
- Use StepProgressBar for multi-step forms
- Keep spacing consistent (16dp padding, 12dp gaps)
- Use proper typography hierarchy

### ❌ DON'T
- Don't use Material3 Card directly (use GlassmorphicCard)
- Don't use Material3 Button for primary actions (use PremiumButton)
- Don't use color-only indicators (use semantic colors + icons)
- Don't use GNITS Orange for disabled states
- Don't mix animation timings (use 300ms standard)
- Don't add unnecessary shadows or borders
- Don't use inconsistent spacing
- Don't override theme colors

---

## Troubleshooting

### Component not appearing?
- Check imports: `import com.sportflow.app.ui.components.*`
- Verify modifier is applied: `modifier = Modifier.fillMaxWidth()`
- Check parent layout has size constraints

### Animation stuttering?
- Use `graphicsLayer` for scale/rotation animations
- Avoid recompositions in animated content
- Use `rememberInfiniteTransition` for infinite animations

### Colors not matching?
- Use theme colors: `GnitsOrange`, `SuccessGreen`, etc.
- Don't hardcode hex values
- Check color contrast for accessibility

### Button not responding?
- Verify `onClick` lambda is not empty
- Check `enabled` state
- Ensure parent layout allows clicks

---

## Performance Tips

1. **Use LazyColumn for lists** — Avoids rendering all items
2. **Use rememberInfiniteTransition** — For continuous animations
3. **Use graphicsLayer** — For GPU-accelerated transforms
4. **Avoid recompositions** — Use `remember` for state
5. **Use AnimatedContent** — For efficient state transitions
6. **Lazy load images** — Use Coil with placeholder
7. **Minimize shadow elevation** — Use 2-8dp only
8. **Use efficient gradients** — Avoid complex color stops

---

## Accessibility Checklist

- [ ] Touch targets are at least 44dp
- [ ] Color contrast meets WCAG AA (4.5:1 for text)
- [ ] Icons have descriptive content descriptions
- [ ] Text is scalable (using sp units)
- [ ] Focus states are visible
- [ ] Semantic colors are used (not color-only)
- [ ] Haptic feedback is provided
- [ ] Loading states are clear

---

## Next Steps

1. Review DESIGN_SYSTEM_REFERENCE.md for complete specifications
2. Check VISUAL_UX_EXCELLENCE_IMPLEMENTATION.md for detailed features
3. Test components on various screen sizes
4. Gather user feedback on visual design
5. Iterate based on feedback
6. Prepare for production deployment
