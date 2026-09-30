package com.lingualoop.android.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onLoggedIn: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.authenticated) {
        if (state.authenticated) {
            onLoggedIn()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (state.mode == AuthMode.LOGIN) "Log in to LinguaLoop" else "Create your account",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { viewModel.onModeChange(AuthMode.LOGIN) }) {
                Text("Log in")
            }
            OutlinedButton(onClick = { viewModel.onModeChange(AuthMode.REGISTER) }) {
                Text("Register")
            }
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.mode == AuthMode.REGISTER) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.displayName,
                onValueChange = viewModel::onDisplayNameChange,
                label = { Text("Display name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = viewModel::submit,
            enabled = !state.submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.mode == AuthMode.LOGIN) "Log in" else "Register")
        }
        Text(
            text = state.error.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .padding(top = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
}
