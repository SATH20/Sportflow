package com.sportflow.app.ui.screens.auth

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.sportflow.app.ui.components.*
import com.sportflow.app.ui.theme.*
import com.sportflow.app.ui.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    navController: NavHostController,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isSignUp by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    // Navigate on success
    LaunchedEffect(uiState.isLoggedIn) {
        if (uiState.isLoggedIn) {
            navController.navigate("home") {
                popUpTo("login") { inclusive = true }
            }
        }
    }

    GlowOrbBackground(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo / Branding
            val infiniteTransition = rememberInfiniteTransition(label = "logo")
            val logoGlow by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "logoGlow"
            )

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                AntigravityBlue.copy(alpha = logoGlow),
                                CyanPulse.copy(alpha = logoGlow * 0.7f)
                            )
                        ),
                        CircleShape
                    )
                    .border(2.dp, GlassBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.FlashOn,
                    contentDescription = null,
                    tint = SoftWhite,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "SportFlow",
                style = SportFlowTheme.typography.displayLarge,
                color = SoftWhite
            )
            Text(
                text = "Your game, your flow",
                style = SportFlowTheme.typography.bodyLarge,
                color = SoftWhiteDim
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Auth Form
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 24.dp,
                backgroundAlpha = 0.1f
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isSignUp) "Create Account" else "Welcome Back",
                        style = SportFlowTheme.typography.headlineLarge,
                        color = SoftWhite
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Name field (sign up only)
                    if (isSignUp) {
                        AuthTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = "Full Name",
                            icon = Icons.Outlined.Person,
                            imeAction = ImeAction.Next,
                            onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Email
                    AuthTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email",
                        icon = Icons.Outlined.Email,
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                        onImeAction = { focusManager.moveFocus(FocusDirection.Down) }
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Password
                    AuthTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = "Password",
                        icon = Icons.Outlined.Lock,
                        keyboardType = KeyboardType.Password,
                        isPassword = true,
                        passwordVisible = passwordVisible,
                        onPasswordToggle = { passwordVisible = !passwordVisible },
                        imeAction = ImeAction.Done,
                        onImeAction = {
                            focusManager.clearFocus()
                            if (isSignUp) viewModel.signUp(email, password, name)
                            else viewModel.signIn(email, password)
                        }
                    )

                    // Error
                    uiState.error?.let { error ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = error,
                            style = SportFlowTheme.typography.bodySmall,
                            color = ErrorRed
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Submit Button
                    GradientButton(
                        text = if (uiState.isLoading) "Loading..."
                        else if (isSignUp) "Create Account"
                        else "Sign In",
                        onClick = {
                            if (isSignUp) viewModel.signUp(email, password, name)
                            else viewModel.signIn(email, password)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !uiState.isLoading && email.isNotBlank() && password.isNotBlank(),
                        icon = if (isSignUp) Icons.Filled.PersonAdd else Icons.Filled.Login
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Toggle sign in / sign up
                    TextButton(onClick = { isSignUp = !isSignUp }) {
                        Text(
                            text = if (isSignUp) "Already have an account? Sign In"
                            else "Don't have an account? Sign Up",
                            style = SportFlowTheme.typography.bodyMedium,
                            color = AntigravityBlueLight
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordToggle: (() -> Unit)? = null,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: () -> Unit = {}
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                style = SportFlowTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = SoftWhiteDim
            )
        },
        trailingIcon = {
            if (isPassword) {
                IconButton(onClick = { onPasswordToggle?.invoke() }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff
                        else Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = SoftWhiteDim
                    )
                }
            }
        },
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation()
        else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        ),
        keyboardActions = KeyboardActions(
            onAny = { onImeAction() }
        ),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AntigravityBlue,
            unfocusedBorderColor = GlassBorder,
            cursorColor = AntigravityBlue,
            focusedLabelColor = AntigravityBlueLight,
            unfocusedLabelColor = SoftWhiteDim,
            focusedTextColor = SoftWhite,
            unfocusedTextColor = SoftWhite
        ),
        singleLine = true
    )
}
