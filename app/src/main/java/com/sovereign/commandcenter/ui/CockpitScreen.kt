package com.sovereign.commandcenter.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereign.commandcenter.data.api.RepoHealth
import com.sovereign.commandcenter.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandCenterCockpit(
    userName: String,
    viewModel: CommandCenterViewModel,
    onLogout: () -> Unit,
    onTriggerStepUp: (String, String, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var chatInput by rememberSaveable { mutableStateOf("") }
    val now = remember { SimpleDateFormat("EEEE, MMMM d, yyyy | h:mm a", Locale.getDefault()).format(Date()) }
    val listState = rememberLazyListState()

    // 3-Top Menu Sheet States
    var showSessionsSheet by remember { mutableStateOf(false) }
    var showFilesSheet by remember { mutableStateOf(false) }
    var showPreviewSheet by remember { mutableStateOf(false) }

    // Voice Duplex State
    var isVoiceActive by remember { mutableStateOf(false) }

    val selectedRepo = uiState.selectedRepository
    val currentMessages = remember(uiState.chatMessages, selectedRepo) {
        uiState.chatMessages.filter { it.repositoryContext == selectedRepo }
    }

    LaunchedEffect(currentMessages.size, uiState.isStreaming) {
        if (currentMessages.isNotEmpty()) {
            listState.animateScrollToItem(currentMessages.size - 1)
        }
    }

    val dynamicRepos: List<RepoHealth> = uiState.orgHealth?.repositories ?: emptyList()

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(SovereignCreamDarker)) {
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
                                    if (selectedRepo == null) "Trinity Universe • Global Cockpit" else "$selectedRepo Cockpit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SovereignStone600
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.startNewChat() }) {
                            Icon(Icons.Default.Add, contentDescription = "New Chat", tint = SovereignStone800)
                        }
                        IconButton(onClick = { viewModel.refreshHealthAndMessages() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh Health", tint = SovereignStone800)
                        }
                        Button(
                            onClick = { onLogout() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = "Logout", tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Logout", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = SovereignCreamDarker)
                )

                // 3-TOP MENU NAVIGATION BAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // [☰ Sessions]
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showSessionsSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        color = if (showSessionsSheet) SovereignAmber else SovereignCream,
                        border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Sessions", tint = SovereignAmberDark, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Sessions", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SovereignStone900)
                        }
                    }

                    // [📁 Files]
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showFilesSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        color = if (showFilesSheet) SovereignAmber else SovereignCream,
                        border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = "Files", tint = SovereignAmberDark, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Files", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SovereignStone900)
                        }
                    }

                    // [🌐 Preview]
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showPreviewSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        color = if (showPreviewSheet) SovereignAmber else SovereignCream,
                        border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Language, contentDescription = "Preview", tint = SovereignAmberDark, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Preview", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SovereignStone900)
                        }
                    }
                }
            }
        },
        bottomBar = {
            // CURVY INPUT BAR CONTAINER
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
                color = SovereignCreamDarker,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // EMERGENCY RED KILL SWITCH BANNER (When agent is executing)
                    AnimatedVisibility(visible = uiState.isStreaming) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFEF2F2))
                                .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(0xFFDC2626), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("AGENT STREAM ACTIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
                            }
                            Text("Emergency Red Switch Armed", fontSize = 11.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Medium)
                        }
                    }

                    // THE CURVY INPUT ROW
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SovereignCream, RoundedCornerShape(28.dp))
                            .border(1.dp, SovereignAmber.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // [+] Attachment Button
                        IconButton(
                            onClick = {
                                Toast.makeText(context, "Attachment: Senior Sovereign attachment API ready", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Attach", tint = SovereignStone600)
                        }

                        // Center Curvy Prompt Composer
                        val placeholderText = if (selectedRepo == null) {
                            "Ask across Trinity Universe..."
                        } else {
                            "Ask $selectedRepo..."
                        }

                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = { Text(placeholderText, fontSize = 13.sp, color = SovereignStone600) },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = SovereignAmberDark
                            ),
                            maxLines = 4
                        )

                        // Voice Mic Duplex Button
                        IconButton(
                            onClick = {
                                isVoiceActive = !isVoiceActive
                                Toast.makeText(context, if (isVoiceActive) "Voice Duplex: Listening..." else "Voice Duplex: Muted", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Voice Duplex",
                                tint = if (isVoiceActive) Color(0xFF16A34A) else SovereignStone600
                            )
                        }

                        // EMERGENCY RED KILL SWITCH (Server Authoritative Cancellation)
                        Surface(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    viewModel.cancelActiveStream()
                                    isVoiceActive = false
                                    Toast.makeText(context, "EMERGENCY STOP EXECUTED", Toast.LENGTH_SHORT).show()
                                },
                            color = if (uiState.isStreaming) Color(0xFFDC2626) else Color(0xFF991B1B),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Kill Switch", tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("STOP", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                        }

                        // Send Button
                        AnimatedVisibility(visible = chatInput.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val msg = chatInput
                                    chatInput = ""
                                    viewModel.sendMessage(msg)
                                },
                                modifier = Modifier.size(38.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = SovereignAmber)
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Send", tint = SovereignCream)
                            }
                        }
                    }
                }
            }
        },
        containerColor = SovereignCream
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val isTablet = maxWidth >= 720.dp

            if (isTablet) {
                // Dual-Pane Tablet Layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (uiState.pendingApproval != null) {
                                val approval = uiState.pendingApproval!!
                                item {
                                    ApprovalBanner(
                                        approval = approval,
                                        selectedRepo = selectedRepo,
                                        onReject = { viewModel.submitApproval(false, "Rejected by CEO") },
                                        onAuthorize = { repo, tool ->
                                            onTriggerStepUp(repo, tool) {
                                                viewModel.submitApproval(true, "Authorized by CEO")
                                            }
                                        }
                                    )
                                }
                            }

                            item {
                                WorkspaceContextBanner(
                                    selectedRepo = selectedRepo,
                                    dynamicRepos = dynamicRepos,
                                    contextVersion = uiState.contextVersion,
                                    onTriggerStepUp = onTriggerStepUp
                                )
                            }

                            if (currentMessages.isEmpty()) {
                                item { EmptyChatBanner(selectedRepo) }
                            } else {
                                items(currentMessages) { message ->
                                    ChatMessageBubble(message = message)
                                }
                            }

                            if (uiState.isStreaming && currentMessages.isNotEmpty() && currentMessages.last().content.isEmpty()) {
                                item { StreamingIndicator() }
                            }
                        }
                    }
                }
            } else {
                // Compact Phone Layout (Samsung S20)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = now,
                            style = MaterialTheme.typography.labelSmall,
                            color = SovereignStone600
                        )
                    }

                    // Organization Repositories Dynamic Pill Bar
                    item {
                        Column {
                            Text(
                                "ORGANIZATION REPOSITORIES",
                                style = MaterialTheme.typography.labelSmall,
                                color = SovereignAmberDark,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    val isGlobalSelected = selectedRepo == null
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isGlobalSelected) SovereignAmber else SovereignCreamDarker,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isGlobalSelected) SovereignAmberDark else SovereignAmber.copy(alpha = 0.35f)
                                        ),
                                        modifier = Modifier.clickable { viewModel.selectRepository(null) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("🌐", fontSize = 13.sp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "Global Universe",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isGlobalSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isGlobalSelected) SovereignCream else SovereignStone900
                                            )
                                        }
                                    }
                                }

                                item {
                                    FilterChip(
                                        selected = selectedRepo == "General Project",
                                        onClick = { viewModel.selectRepository("General Project") },
                                        label = { Text("⚡ General Project", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = SovereignAmber,
                                            selectedLabelColor = SovereignCream
                                        ),
                                        shape = RoundedCornerShape(16.dp),
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }

                                items(dynamicRepos) { repo ->
                                    val isSelected = selectedRepo == repo.name
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) SovereignAmber else SovereignCreamDarker,
                                        border = BorderStroke(
                                            1.dp,
                                            if (isSelected) SovereignAmberDark else SovereignAmber.copy(alpha = 0.35f)
                                        ),
                                        modifier = Modifier.clickable {
                                            if (isSelected) viewModel.selectRepository(null)
                                            else viewModel.selectRepository(repo.name)
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .background(
                                                        if (repo.status.equals("nominal", ignoreCase = true)) Color(0xFF16A34A) else Color(0xFFEA580C),
                                                        CircleShape
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                repo.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) SovereignCream else SovereignStone900
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Pending Approval Banner
                    if (uiState.pendingApproval != null) {
                        val approval = uiState.pendingApproval!!
                        item {
                            ApprovalBanner(
                                approval = approval,
                                selectedRepo = selectedRepo,
                                onReject = { viewModel.submitApproval(false, "Rejected by CEO") },
                                onAuthorize = { repo, tool ->
                                    onTriggerStepUp(repo, tool) {
                                        viewModel.submitApproval(true, "Authorized by CEO")
                                    }
                                }
                            )
                        }
                    }

                    // Repository Context Card
                    if (selectedRepo != null) {
                        item {
                            WorkspaceContextBanner(
                                selectedRepo = selectedRepo,
                                dynamicRepos = dynamicRepos,
                                contextVersion = uiState.contextVersion,
                                onTriggerStepUp = onTriggerStepUp
                            )
                        }
                    }

                    // Chat History Messages
                    if (currentMessages.isEmpty()) {
                        item {
                            EmptyChatBanner(selectedRepo)
                        }
                    } else {
                        items(currentMessages) { message ->
                            ChatMessageBubble(message = message)
                        }
                    }

                    // Streaming Indicator
                    if (uiState.isStreaming && currentMessages.isNotEmpty() && currentMessages.last().content.isEmpty()) {
                        item {
                            StreamingIndicator()
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }

    // MODAL BOTTOM SHEET: [☰ SESSIONS]
    if (showSessionsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSessionsSheet = false },
            containerColor = SovereignCreamDarker
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CEO SESSIONS", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SovereignAmberDark)
                    Button(
                        onClick = {
                            viewModel.startNewChat()
                            showSessionsSheet = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SovereignAmber),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New", modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Session", fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Active Target: ${selectedRepo ?: "Trinity Universe (Global)"}", fontSize = 13.sp, color = SovereignStone800)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Total Messages in Cockpit: ${uiState.chatMessages.size}", fontSize = 12.sp, color = SovereignStone600)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // MODAL BOTTOM SHEET: [📁 FILES]
    if (showFilesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilesSheet = false },
            containerColor = SovereignCreamDarker
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("CODEBASE FILE TREE", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SovereignAmberDark)
                    Text(selectedRepo ?: "Global Root", fontSize = 12.sp, color = SovereignStone600)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SovereignCream,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("📁 src/", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = SovereignStone800)
                        Text("  📄 App.tsx", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = SovereignStone800)
                        Text("  📄 main.tsx", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = SovereignStone800)
                        Text("📁 artifacts/", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = SovereignStone800)
                        Text("  📄 package.json", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = SovereignStone800)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // MODAL BOTTOM SHEET: [🌐 PREVIEW]
    if (showPreviewSheet) {
        ModalBottomSheet(
            onDismissRequest = { showPreviewSheet = false },
            containerColor = SovereignCreamDarker
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("LIVE WEB PREVIEW", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SovereignAmberDark)
                    Text("PORT 5173", fontSize = 11.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    color = Color.Black,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Preview Viewport Active\n[Rendering Senior Web Engine]", color = Color.LightGray, fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

// -----------------------------------------------------------------------------
// EXACT HELPER COMPOSABLES (RECONCILED WITH BACKUP PARITY)
// -----------------------------------------------------------------------------

@Composable
fun ApprovalBanner(
    approval: PendingApprovalData,
    selectedRepo: String?,
    onReject: () -> Unit,
    onAuthorize: (String, String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0xFFDC2626)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "CEO APPROVAL REQUIRED",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFDC2626),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Tool: ${approval.tool}",
                fontWeight = FontWeight.Bold,
                color = SovereignStone950,
                fontSize = 14.sp
            )
            Text(
                approval.dangerReason,
                fontSize = 12.sp,
                color = SovereignStone800
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Reject", color = SovereignStone800)
                }
                Button(
                    onClick = {
                        onAuthorize(selectedRepo ?: "Trinity Universe", approval.tool)
                    },
                    modifier = Modifier.weight(1.5f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null, tint = SovereignCream, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Authorize (Passkey)", color = SovereignCream, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun WorkspaceContextBanner(
    selectedRepo: String?,
    dynamicRepos: List<RepoHealth>,
    contextVersion: Long,
    onTriggerStepUp: (String, String, () -> Unit) -> Unit
) {
    val repoInfo = dynamicRepos.find { it.name == selectedRepo }
    val title = selectedRepo ?: "Trinity Universe (Global Context)"
    Card(
        colors = CardDefaults.cardColors(containerColor = SovereignAmberSurface.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "WORKSPACE CONTEXT",
                style = MaterialTheme.typography.labelSmall,
                color = SovereignAmberDark,
                fontWeight = FontWeight.Bold
            )
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = SovereignStone950
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Branch: ${repoInfo?.branch ?: "main"} • Status: ${repoInfo?.status ?: "nominal"} • Context v$contextVersion",
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = SovereignStone800
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    onTriggerStepUp(selectedRepo ?: "Trinity Universe", "deploy_production") {}
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = SovereignAmberDark)
            ) {
                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Trigger Biometric Step-Up", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun EmptyChatBanner(selectedRepo: String?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SovereignCreamDarker.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Code,
                contentDescription = null,
                tint = SovereignAmber,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (selectedRepo == null) "Trinity Universe Global Intelligence" else "Active Workspace: $selectedRepo",
                fontWeight = FontWeight.Bold,
                color = SovereignStone950,
                fontSize = 14.sp
            )
            Text(
                if (selectedRepo == null)
                    "Ask questions across all approved organization repositories or select a repository pill above."
                else
                    "Ask Sovereign to inspect tests, search codebase, or trigger deployments for this repository.",
                color = SovereignStone600,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun StreamingIndicator() {
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = SovereignAmber)
        Spacer(modifier = Modifier.width(8.dp))
        Text("Sovereign Agent thinking...", fontSize = 12.sp, color = SovereignStone600)
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            color = if (isUser) SovereignAmber else SovereignCreamDarker,
            border = BorderStroke(
                1.dp,
                if (isUser) SovereignAmberDark else SovereignAmber.copy(alpha = 0.25f)
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (isUser) "CEO" else "Sovereign Agent",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isUser) SovereignCream else SovereignAmberDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (message.content.isEmpty() && !isUser) "..." else message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) SovereignCream else SovereignStone950
                )
            }
        }
    }
}
