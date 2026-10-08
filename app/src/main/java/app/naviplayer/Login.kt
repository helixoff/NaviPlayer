package app.naviplayer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLogin: (Credentials) -> Unit) {
    var url by remember { mutableStateOf("http://") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val canLogin = !busy && url.length > 7 && user.isNotBlank() && pass.isNotEmpty()

    fun submit() {
        if (!canLogin) return
        busy = true
        errorMessage = null
        val credentials = Credentials(url.trim().trimEnd('/'), user.trim(), pass)
        scope.launch {
            try {
                SubsonicApi(credentials).ping()
                onLogin(credentials)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "Could not connect to this server."
                busy = false
            }
        }
    }

    NaviBackdrop(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "NaviPlayer",
                modifier = Modifier.size(92.dp).align(Alignment.CenterHorizontally),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "NaviPlayer",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = Pal.Text,
                style = MaterialTheme.typography.displaySmall,
            )
            Text(
                "Your music, from your Navidrome server.",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = Pal.Subtle,
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(28.dp))

            TonalCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Sign in", color = Pal.Text, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Use an existing account from your Navidrome server.",
                        color = Pal.Subtle,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                OutlinedTextField(
                    url, { url = it }, Modifier.fillMaxWidth(), label = { Text("server address") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Dns, null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    supportingText = { Text("For example: https://music.example.com") },
                )
                OutlinedTextField(
                    user, { user = it }, Modifier.fillMaxWidth(), label = { Text("username") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                OutlinedTextField(
                    pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text("password") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Key, null) },
                    trailingIcon = {
                        IconButton(onClick = { showPass = !showPass }) {
                            Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, "toggle password")
                        }
                    },
                    visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                )
                }
            }

            errorMessage?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(20.dp))
            Button(enabled = canLogin, modifier = Modifier.fillMaxWidth().height(56.dp), onClick = ::submit) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(12.dp))
                }
                Text(if (busy) "Connecting…" else "Sign in", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
