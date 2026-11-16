package com.ezstats.android.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ezstats.android.EzViewModel
import com.ezstats.android.ui.theme.Motion

/**
 * A big icon in one of Material's expressive shapes, turning slowly: the centrepiece of the screens you see
 * before the dashboard.
 */
@Composable
fun HeroBadge(icon: ImageVector, container: Color, content: Color, shape: Shape = MaterialShapes.Cookie9Sided.toShape(), size: Dp = 112.dp) {
    val spin = rememberInfiniteTransition(label = "hero")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart), label = "hero angle")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = angle }
                .clip(shape)
                .background(container),
        )
        Icon(icon, null, Modifier.size(size * 0.46f), tint = content)
    }
}

/** The server can't be reached, and there is nothing saved to show. */
@Composable
fun OfflineScreen(vm: EzViewModel) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HeroBadge(
            Icons.Rounded.CloudOff,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            MaterialShapes.SoftBurst.toShape(),
        )
        Spacer(Modifier.height(24.dp))
        Text("Can't reach the server", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(vm.active?.url.orEmpty(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(
            vm.statusError.orEmpty(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = { vm.check() }, shapes = ButtonDefaults.shapes(), modifier = Modifier.widthIn(min = 200.dp).height(56.dp)) { Text("Try again") }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { vm.showServers = true }) { Text("Change server") }
    }
}

/** Which way of signing in the login screen is showing. */
private const val PASSWORD = 0
private const val QUICKLOGIN = 1
private const val RESET = 2

/** Sign in with a name and password or a QuickLogin code from the game, or reset the password with a code. */
@Composable
fun LoginScreen(vm: EzViewModel) {
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var newPass by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf(PASSWORD) }
    var show by remember { mutableStateOf(false) }
    val ready = when (mode) {
        QUICKLOGIN -> user.isNotBlank() && code.length == 8
        RESET -> user.isNotBlank() && code.length == 8 && newPass.isNotEmpty()
        else -> user.isNotBlank() && pass.isNotEmpty()
    }
    val submit = {
        if (!vm.loginBusy && ready) {
            when (mode) {
                QUICKLOGIN -> vm.signInWithCode(user, code)
                RESET -> vm.resetPassword(user, code, newPass)
                else -> vm.signIn(user, pass)
            }
        }
    }
    val switchTo = { next: Int ->
        if (next != mode) {
            mode = next
            code = ""
            vm.clearLoginError()
        }
    }

    Box(Modifier.fillMaxSize().systemBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .widthIn(max = 460.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HeroBadge(
                if (mode == RESET) Icons.Rounded.LockReset else Icons.Rounded.Thermostat,
                MaterialTheme.colorScheme.primaryContainer,
                MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(24.dp))
            Text(if (mode == RESET) "Reset password" else "Welcome back", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(4.dp))
            Text(vm.active?.name.orEmpty(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(vm.active?.url.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))

            // Password or QuickLogin: a connected pair of toggles (resetting a password has neither).
            if (mode != RESET) {
                androidx.compose.foundation.layout.Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    ToggleButton(
                        checked = mode == PASSWORD,
                        onCheckedChange = { switchTo(PASSWORD) },
                        shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Password, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Password")
                    }
                    ToggleButton(
                        checked = mode == QUICKLOGIN,
                        onCheckedChange = { switchTo(QUICKLOGIN) },
                        shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Pin, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("QuickLogin")
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn(Motion.fadeIn(40)) togetherWith fadeOut(Motion.fadeOut()) },
                label = "login mode",
            ) { shown ->
                Column {
                    OutlinedTextField(
                        value = user,
                        onValueChange = { user = it },
                        label = { Text("Minecraft name") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    if (shown == PASSWORD) {
                        OutlinedTextField(
                            value = pass,
                            onValueChange = { pass = it },
                            label = { Text("Password") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = { ShowToggle(show) { show = !show } },
                            supportingText = { Text("Set in game with /ezstats register") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        TextButton(onClick = { switchTo(RESET) }, modifier = Modifier.align(Alignment.End)) { Text("Forgot password?") }
                    } else {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.filter(Char::isDigit).take(8) },
                            label = { Text("8 digit code") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            supportingText = {
                                Text(
                                    if (shown == QUICKLOGIN) "Run /ezstats quicklogin in game. The code comes in chat and works once, for 5 minutes."
                                    else "Run /ezstats resetpassword in game. The code comes in chat and works once, for 5 minutes.",
                                )
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.NumberPassword,
                                imeAction = if (shown == RESET) ImeAction.Next else ImeAction.Done,
                            ),
                            keyboardActions = KeyboardActions(onDone = { submit() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (shown == RESET) {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = newPass,
                                onValueChange = { newPass = it },
                                label = { Text("New password") },
                                singleLine = true,
                                shape = MaterialTheme.shapes.medium,
                                visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = { ShowToggle(show) { show = !show } },
                                supportingText = { Text("8 to 128 characters, no spaces. It goes straight to the server, never into Minecraft.") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { submit() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = vm.loginError != null,
                enter = fadeIn() + expandVertically(Motion.size),
                exit = fadeOut() + shrinkVertically(Motion.size),
            ) {
                Text(
                    vm.loginError.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
            if (vm.loginBusy) {
                ContainedLoadingIndicator(Modifier.size(56.dp))
            } else {
                Button(
                    onClick = { submit() },
                    enabled = ready,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(if (mode == RESET) "Set password and sign in" else "Sign in", style = MaterialTheme.typography.titleMedium) }
            }
            Spacer(Modifier.height(8.dp))
            if (mode == RESET) {
                TextButton(onClick = { switchTo(PASSWORD) }) { Text("Back to sign in") }
            }
            TextButton(onClick = { vm.showServers = true }) {
                Icon(Icons.Rounded.Dns, null, Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Change server")
            }
        }
    }
}

@Composable
private fun ShowToggle(show: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Hide password" else "Show password")
    }
}
