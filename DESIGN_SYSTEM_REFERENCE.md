# GNITS SPORTFLOW — DESIGN SYSTEM REFERENCE

## Color Palette

### Primary Colors
```
GNITS Orange:        #F09819 (Primary action, high intent)
GNITS Orange Light:  #FEF3E2 (Background for orange elements)
GNITS Orange Dark:   #E08C15 (Hover/pressed state)
```

### Secondary Colors
```
Mirage Blue:         #635BFF (Structural elements, info)
Mirage Blue Light:   #EEF2FF (Background for blue elements)
Mirage Blue Dark:    #4338CA (Hover/pressed state)
```

### Semantic Colors
```
Success Green:       #22C55E (Confirmed, accepted)
Success Green Light: #DCFCE7 (Background)
Live Red:            #EF4444 (Live, urgent, danger)
Live Red BG:         #FEF2F2 (Background)
Warning Amber:       #F59E0B (Warnings, caution)
Warning Amber BG:    #FFFBEB (Background)
Error Red:           #EF4444 (Errors, denials)
```

### Neutral Colors
```
Pure White:          #FFFFFF (Primary background)
Off White:           #F8FAFC (Secondary background)
Screen BG:           #F1F5F9 (Screen background)
Card Surface:        #F8FAFC (Card background)
Card Border:         #E2E8F0 (Card border)
Text Primary:        #0F172A (Main text)
Text Secondary:      #64748B (Secondary text)
Text Tertiary:       #94A3B8 (Tertiary text)
```

---

## Typography

### Display Styles
```
Display Large:
  Size: 32sp
  Weight: ExtraBold (800)
  Line Height: 40sp
  Letter Spacing: -0.5sp
  Use: Screen titles, hero headlines

Display Medium:
  Size: 24sp
  Weight: Bold (700)
  Line Height: 32sp
  Letter Spacing: -0.3sp
  Use: Section headers
```

### Headline Styles
```
Headline Large:
  Size: 20sp
  Weight: Bold (700)
  Line Height: 28sp
  Use: Card titles, important content

Headline Medium:
  Size: 18sp
  Weight: SemiBold (600)
  Line Height: 26sp
  Use: Subsection titles

Headline Small:
  Size: 16sp
  Weight: SemiBold (600)
  Line Height: 24sp
  Use: Card subtitles
```

### Body Styles
```
Body Large:
  Size: 16sp
  Weight: Normal (400)
  Line Height: 24sp
  Use: Primary content

Body Medium:
  Size: 14sp
  Weight: Normal (400)
  Line Height: 20sp
  Use: Secondary content

Body Small:
  Size: 12sp
  Weight: Normal (400)
  Line Height: 16sp
  Use: Tertiary content, metadata
```

### Label Styles
```
Label Large:
  Size: 14sp
  Weight: SemiBold (600)
  Line Height: 20sp
  Use: Buttons, chips

Label Medium:
  Size: 12sp
  Weight: Medium (500)
  Line Height: 16sp
  Use: Small labels

Label Small:
  Size: 10sp
  Weight: Medium (500)
  Line Height: 14sp
  Letter Spacing: 0.5sp
  Use: Badges, tags
```

### Special Styles
```
Score Display:
  Size: 48sp
  Weight: ExtraBold (800)
  Line Height: 56sp
  Use: Match scores, large numbers

Timer Display:
  Size: 14sp
  Weight: Bold (700)
  Line Height: 20sp
  Letter Spacing: 1sp
  Use: Match timers, countdowns
```

---

## Component Specifications

### Glassmorphic Card
```
Background:    Gradient (95% white → 85% white)
Border:        1dp white (40% opacity)
Corner Radius: 16dp
Shadow:        8dp (elevated) / 2dp (pressed)
Padding:       16dp
Interaction:   Smooth press animation
```

### Premium Button
```
Height:        48dp
Corner Radius: 12dp
Shadow:        4dp (elevated) / 2dp (pressed)
Padding:       Horizontal 16dp, Vertical 12dp
Icon Size:     20dp
Text Style:    Label Large, SemiBold
States:
  ├─ Enabled:   GNITS Orange background
  ├─ Pressed:   Darker orange, 2dp shadow
  ├─ Disabled:  Grey (50% opacity)
  └─ Loading:   Infinite alpha pulse
```

### State-Aware Button
```
REGISTER:
  ├─ Color:    GNITS Orange
  ├─ Icon:     PersonAdd
  ├─ Text:     "Register"
  └─ Enabled:  true

REGISTERED:
  ├─ Color:    Success Green
  ├─ Icon:     CheckCircle
  ├─ Text:     "Registered ✓"
  └─ Enabled:  false

FULL:
  ├─ Color:    Grey (50%)
  ├─ Icon:     Block
  ├─ Text:     "Squad Full"
  └─ Enabled:  false

INELIGIBLE:
  ├─ Color:    Grey (50%)
  ├─ Icon:     Lock
  ├─ Text:     "Not Eligible"
  └─ Enabled:  false
```

### Pulse Animation
```
Duration:      1500ms
Scale:         1.0 → 1.05 → 1.0
Shadow Alpha:  0.2 → 0.6 → 0.2
Easing:        EaseInOutCubic
Repeat:        Infinite
Trigger:       Squad becomes full
```

### Step Progress Bar
```
Progress Bar:
  ├─ Height:   4dp
  ├─ Color:    GNITS Orange
  ├─ Track:    Grey (20% opacity)
  └─ Radius:   2dp

Step Indicators:
  ├─ Size:     32dp
  ├─ Radius:   CircleShape
  ├─ Completed: Success Green + checkmark
  ├─ Current:   GNITS Orange + border
  └─ Future:    Grey (20% opacity)
```

### Admin Decision Panel
```
Avatar:
  ├─ Size:     48dp
  ├─ Gradient: Orange → Dark Orange
  ├─ Icon:     Person (white)
  └─ Shape:    CircleShape

Info:
  ├─ Roll Number: Bold, Text Primary
  ├─ Email:       Regular, Text Secondary
  └─ Department:  SemiBold, GNITS Orange

Buttons:
  ├─ Accept:  Success Green, 44dp height
  ├─ Deny:    Red outline, 44dp height
  └─ Gap:     12dp between buttons
```

### Time-Block Schedule
```
Per Slot:
  ├─ Time:     GNITS Orange, Label Large
  ├─ Match:    Text Primary, Body Medium
  ├─ Venue:    Text Secondary, Body Small
  ├─ Icon:     Edit (GNITS Orange)
  └─ Card:     Glassmorphic

Layout:
  ├─ Vertical arrangement
  ├─ 8dp gap between slots
  ├─ 16dp padding
  └─ Scrollable for full day
```

---

## Spacing System

```
4dp   — Minimal spacing (rarely used)
6dp   — Icon-to-text spacing
8dp   — Small gaps between elements
12dp  — Standard gap between components
16dp  — Card padding, section spacing
20dp  — Large spacing, screen padding
24dp  — Extra large spacing
32dp  — Section separators
```

---

## Shadow System

```
Elevated (8dp):
  ├─ Ambient:  5% black
  ├─ Spot:     12% black
  └─ Use:      Default card state

Pressed (2dp):
  ├─ Ambient:  5% black
  ├─ Spot:     8% black
  └─ Use:      Button pressed state

Hover (4dp):
  ├─ Ambient:  8% black
  ├─ Spot:     12% black
  └─ Use:      Button hover state
```

---

## Animation Timings

```
Fast:      200ms (fade in/out)
Standard:  300ms (slide transitions)
Slow:      1500ms (pulse animations)
Easing:    EaseInOutCubic (smooth)
```

---

## Responsive Breakpoints

```
Mobile:    < 600dp
Tablet:    600dp - 840dp
Desktop:   > 840dp

Current Focus: Mobile (primary)
```

---

## Accessibility

```
✓ Color Contrast: WCAG AA compliant
✓ Touch Targets: Minimum 44dp
✓ Text Sizing: Scalable (sp units)
✓ Icon Sizing: Consistent (16dp, 20dp, 24dp)
✓ Semantic Colors: Not color-only indicators
✓ Focus States: Clear visual feedback
✓ Haptic Feedback: Light vibration on actions
```

---

## Implementation Files

```
PremiumComponents.kt:
  ├─ GlassmorphicCard
  ├─ PremiumButton
  ├─ StateAwareButton
  ├─ PulseAnimation
  ├─ StepProgressBar
  ├─ CricketScorecardChip
  ├─ BadmintonScorecardChip
  ├─ AdminDecisionPanel
  ├─ TimeBlockSchedule
  └─ Supporting data classes

Theme Files:
  ├─ Color.kt (color definitions)
  ├─ Type.kt (typography)
  └─ Theme.kt (theme composition)

Screen Files:
  ├─ HomeFeedScreenComplete.kt (uses GlassmorphicCard, StateAwareButton, PulseAnimation)
  ├─ MyMatchesScreenComplete.kt (uses GlassmorphicCard, PremiumButton)
  ├─ AdvancedRegistrationBottomSheet.kt (uses StepProgressBar)
  └─ AdminApprovalScreen.kt (uses AdminDecisionPanel)
```

---

## Usage Examples

### Using GlassmorphicCard
```kotlin
GlassmorphicCard(
    modifier = Modifier.fillMaxWidth(),
    onClick = { /* handle click */ }
) {
    Column(modifier = Modifier.padding(16.dp)) {
        // Content here
    }
}
```

### Using StateAwareButton
```kotlin
StateAwareButton(
    state = if (isRegistered) ButtonState.REGISTERED else ButtonState.REGISTER,
    onClick = { /* handle click */ },
    modifier = Modifier.fillMaxWidth()
)
```

### Using PulseAnimation
```kotlin
PulseAnimation(
    modifier = Modifier.fillMaxWidth(),
    color = GnitsOrange
) {
    GlassmorphicCard { /* content */ }
}
```

### Using StepProgressBar
```kotlin
StepProgressBar(
    currentStep = currentStep,
    totalSteps = 3,
    modifier = Modifier.fillMaxWidth()
)
```

### Using AdminDecisionPanel
```kotlin
AdminDecisionPanel(
    rollNumber = "21A91A0123",
    email = "student@gnits.ac.in",
    department = "CSE",
    onAccept = { /* accept */ },
    onDeny = { /* deny */ }
)
```

---

## Brand Guidelines

### GNITS Orange Usage
```
✓ Primary action buttons (Register, Submit, Update)
✓ High-intent UI elements
✓ Section headers and titles
✓ Active states and indicators
✓ Icon accents
✗ Do NOT use for disabled states
✗ Do NOT use for error messages
```

### Mirage Blue Usage
```
✓ Structural elements (Top bars, navigation)
✓ Secondary actions
✓ Info badges and chips
✓ Informational content
✗ Do NOT use for primary actions
✗ Do NOT use for critical alerts
```

### Semantic Color Usage
```
✓ Green: Success, confirmed, accepted
✓ Red: Live, urgent, danger, denied
✓ Amber: Warnings, caution, near capacity
✓ Grey: Disabled, inactive, unavailable
```

---

## Performance Notes

```
✓ Glassmorphic cards use efficient gradients
✓ Pulse animation uses graphicsLayer (GPU-accelerated)
✓ State transitions use AnimatedContent (efficient)
✓ No unnecessary recompositions
✓ Lazy loading for lists
✓ Smooth 60fps animations
✓ Minimal memory footprint
```

---

## Future Enhancements

```
□ Dark mode support
□ Custom font (Inter) bundling
□ Haptic feedback customization
□ Animation speed preferences
□ High contrast mode
□ Reduced motion support
```
