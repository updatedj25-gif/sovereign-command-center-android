package com.sovereign.commandcenter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.sovereign.commandcenter.auth.BiometricStepUpHelper
import com.sovereign.commandcenter.data.api.ApiClient
import com.sovereign.commandcenter.data.api.AppUpdateInfo
import com.sovereign.commandcenter.data.session.OwnerSession
import com.sovereign.commandcenter.data.session.SessionManager
import com.sovereign.commandcenter.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize configured baseUrl from persistent storage
        ApiClient.baseUrl = SessionManager.getBaseUrl(this)

        setContent {
            SovereignTheme {
                val isAuthenticated = remember { mutableStateOf(false) }
                val isAuthenticating = remember { mutableStateOf(false) }
                val updateInfoState = remember { mutableStateOf<AppUpdateInfo?>(null) }
                val sessionUser = remember { mutableStateOf("Adebola James Ogunjimi") }
                val showServerConfigDialog = remember { mutableStateOf(false) }

                // Check existing secure session on launch
                LaunchedEffect(Unit) {
                    val existing = SessionManager.loadSession(this@MainActivity)
                    if (existing != null && SessionManager.isSessionValid(existing)) {
                        sessionUser.value = existing.displayName
                        isAuthenticated.value = true
                    }

                    ApiClient.checkForUpdate(currentVersionCode = 2) { update ->
                        if (update != null && update.hasUpdate) {
                            runOnUiThread {
                                updateInfoState.value = update
                            }
                        }
                    }
                }

                // Server Configuration Dialog
                if (showServerConfigDialog.value) {
                    var inputUrl by remember { mutableStateOf(ApiClient.baseUrl) }
                    AlertDialog(
                        onDismissRequest = { showServerConfigDialog.value = false },
                        title = { Text("Server Endpoint Configuration", fontWeight = FontWeight.Bold) },
                        text = {
                            Column {
                                Text("Enter the Sovereign Backend URL (e.g., http://192.168.1.50:5000 or https://your-server.com):", fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = inputUrl,
                                    onValueChange = { inputUrl = it },
                                    label = { Text("Server URL") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (inputUrl.isNotBlank()) {
                                        SessionManager.setBaseUrl(this@MainActivity, inputUrl)
                                        Toast.makeText(this@MainActivity, "Server set to: ${ApiClient.baseUrl}", Toast.LENGTH_SHORT).show()
                                    }
                                    showServerConfigDialog.value = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SovereignAmber)
                            ) {
                                Text("Save", color = SovereignCream)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showServerConfigDialog.value = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                if (updateInfoState.value != null) {
                    val update = updateInfoState.value!!
                    Dialog(onDismissRequest = { updateInfoState.value = null }) {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = SovereignCreamDarker),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SovereignAmber),
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    modifier = Modifier.size(50.dp).background(SovereignAmber, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = SovereignCream)
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    "Update Available",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SovereignStone950
                                )
                                Text(
                                    "Version ${update.versionName} is ready to install.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = SovereignStone800
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    update.releaseNotes,
                                    fontSize = 12.sp,
                                    color = SovereignStone600
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { updateInfoState.value = null },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Later", color = SovereignStone800)
                                    }
                                    Button(
                                        onClick = {
                                            updateInfoState.value = null
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
                                            startActivity(intent)
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = SovereignAmber),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Download Now", color = SovereignCream, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                if (!isAuthenticated.value) {
                    PasskeyGateScreen(
                        isLoading = isAuthenticating.value,
                        serverUrl = ApiClient.baseUrl,
                        onOpenSettings = { showServerConfigDialog.value = true },
                        onTriggerPasskey = {
                            isAuthenticating.value = true

                            // Step 1: Engage native hardware biometric prompt on Samsung S20
                            BiometricStepUpHelper.authenticateCeoLogin(
                                activity = this@MainActivity,
                                onSucceeded = {
                                    lifecycleScope.launch {
                                        // Step 2: Fetch challenge from Sovereign server
                                        val challengeResult = ApiClient.getPasskeyChallenge()
                                        if (challengeResult.isFailure) {
                                            isAuthenticating.value = false
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Server connection failed (${ApiClient.baseUrl}): ${challengeResult.exceptionOrNull()?.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                            return@launch
                                        }

                                        val challenge = challengeResult.getOrThrow()

                                        // Step 3: Verify passkey assertion with backend server
                                        val verifyResult = ApiClient.verifyPasskey(challenge.challengeId)
                                        isAuthenticating.value = false

                                        if (verifyResult.isSuccess) {
                                            val verified = verifyResult.getOrThrow()
                                            SessionManager.saveSession(
                                                context = this@MainActivity,
                                                session = OwnerSession(
                                                    token = verified.token,
                                                    ownerId = verified.ownerId,
                                                    expiresAtMillis = System.currentTimeMillis() + 86_400_000,
                                                    displayName = verified.displayName
                                                )
                                            )
                                            sessionUser.value = verified.displayName
                                            isAuthenticated.value = true
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Welcome CEO Adebola James Ogunjimi",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Verification rejected: ${verifyResult.exceptionOrNull()?.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                onFailed = { reason ->
                                    isAuthenticating.value = false
                                    Toast.makeText(this@MainActivity, reason, Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    )
                } else {
                    CommandCenterCockpit(
                        userName = sessionUser.value,
                        onLogout = {
                            SessionManager.clearSession(this@MainActivity)
                            isAuthenticated.value = false
                        },
                        onTriggerStepUp = { repo, action, onApproved ->
                            BiometricStepUpHelper.executeActionBoundStepUp(
                                activity = this@MainActivity,
                                action = action,
                                repository = repo,
                                environment = "Production",
                                onAuthorized = { stepUpResult ->
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Authorized: ${stepUpResult.action} at ${stepUpResult.authorizedAt}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onApproved()
                                },
                                onFailed = { err ->
                                    Toast.makeText(this@MainActivity, "Step-Up Failed: $err", Toast.LENGTH_LONG).show()
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun PasskeyGateScreen(
    isLoading: Boolean,
    serverUrl: String,
    onOpenSettings: () -> Unit,
    onTriggerPasskey: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = SovereignCream
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Settings Icon to configure Server URL
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Server Settings", tint = SovereignStone800)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(SovereignAmber, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👑", fontSize = 42.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "SOVEREIGN",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    color = SovereignAmberDark
                )

                Text(
                    "Command Center • Trinity Universe",
                    style = MaterialTheme.typography.labelSmall,
                    color = SovereignStone600
                )

                Spacer(modifier = Modifier.height(32.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = SovereignCreamDarker),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp),
                            tint = SovereignAmber
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            "CEO Passkey Gate",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SovereignStone950
                        )

                        Text(
                            "Adebola James Ogunjimi",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SovereignStone800
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "Server: $serverUrl",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = SovereignStone600
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { if (!isLoading) onTriggerPasskey() },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = SovereignAmber),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), color = SovereignCream)
                            } else {
                                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = SovereignCream)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Unlock with Passkey / Fingerprint", color = SovereignCream, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandCenterCockpit(
    userName: String,
    onLogout: () -> Unit,
    onTriggerStepUp: (String, String, () -> Unit) -> Unit
) {
    val activeRepo = remember { mutableStateOf<String?>(null) }
    val chatInput = remember { mutableStateOf("") }
    val now = remember { SimpleDateFormat("EEEE, MMMM d, yyyy | h:mm a", Locale.getDefault()).format(Date()) }

    val repositories = listOf(
        "Sovereign Coding Agent",
        "Trinity Universe Website",
        "Sovereign Command Center",
        "Main Portal",
        "Book Library"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(SovereignAmber, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👑", fontSize = 18.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                "SOVEREIGN",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp,
                                color = SovereignAmberDark
                            )
                            Text(
                                "Trinity Universe Cockpit",
                                style = MaterialTheme.typography.labelSmall,
                                color = SovereignStone600
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onLogout() }) {
                        Icon(Icons.Default.Lock, contentDescription = "Lock Session", tint = SovereignStone800)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SovereignCreamDarker
                )
            )
        },
        containerColor = SovereignCream
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = now,
                    style = MaterialTheme.typography.labelSmall,
                    color = SovereignStone600
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SovereignCreamDarker),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SovereignAmber.copy(alpha = 0.25f))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            "Welcome, $userName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SovereignStone950
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Server-authoritative passkey session active. Sovereign backend verified.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SovereignStone800
                        )
                    }
                }
            }

            item {
                Text(
                    "ORGANIZATION REPOSITORIES",
                    style = MaterialTheme.typography.labelSmall,
                    color = SovereignAmberDark,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(repositories) { repo ->
                        val isSelected = activeRepo.value == repo
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) SovereignAmber else SovereignCreamDarker,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) SovereignAmberDark else SovereignAmber.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier.clickable {
                                activeRepo.value = if (isSelected) null else repo
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Code,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isSelected) SovereignCream else SovereignAmber
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    repo,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) SovereignCream else SovereignStone900
                                )
                            }
                        }
                    }
                }
            }

            if (activeRepo.value != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SovereignAmberSurface.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                "ACTIVE REPOSITORY CONTEXT",
                                style = MaterialTheme.typography.labelSmall,
                                color = SovereignAmberDark,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                activeRepo.value ?: "",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SovereignStone950
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Branch: main • Environment: Production • Security: Protected",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = SovereignStone800
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    val repoName = activeRepo.value ?: "Sovereign"
                                    onTriggerStepUp(repoName, "deploy_production") {
                                        // Successfully authorized by server
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SovereignAmberDark)
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Execute Action-Bound Biometric Step-Up", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
