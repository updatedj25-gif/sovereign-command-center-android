package com.sovereign.commandcenter.ui
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card

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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import com.sovereign.commandcenter.data.api.RepoTreeEntry
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
import com.sovereign.commandcenter.data.models.ExecutiveAuditDigestProvider
import com.sovereign.commandcenter.data.models.AuditReport
import com.sovereign.commandcenter.data.models.AuditSeverity
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // Staged Voice Dictation Review Gate (Zero silent auto-send)
    LaunchedEffect(uiState.stagedVoiceInput) {
        uiState.stagedVoiceInput?.let { recognized ->
            if (recognized.isNotBlank()) {
                chatInput = if (chatInput.isBlank()) recognized else "$chatInput $recognized"
                viewModel.clearStagedVoiceInput()
            }
        }
    }


    // Secondary Menu Sheet States
    // Primary Navigation: CHAT | FILES | PREVIEW
    var activeTab by rememberSaveable { mutableStateOf("CHAT") }
    var selectedFileContent by remember { mutableStateOf<Pair<String, String>?>(null) }
    var fileSearchQuery by remember { mutableStateOf("") }
    var previewDeviceMode by remember { mutableStateOf("MOBILE") }
    var showSessionsSheet by remember { mutableStateOf(false) }
    var showFilesSheet by remember { mutableStateOf(false) }
    var showPreviewSheet by remember { mutableStateOf(false) }
    var showAuditInboxSheet by remember { mutableStateOf(false) }

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

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = SovereignCreamDarker,
                modifier = Modifier
                    .width(320.dp)
                    .statusBarsPadding()
                    .padding(top = 10.dp, bottom = 16.dp, start = 8.dp, end = 8.dp),
                drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👑", fontSize = 20.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "CEO SESSIONS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SovereignAmberDark
                            )
                        }
                        IconButton(onClick = { coroutineScope.launch { drawerState.close() } }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Drawer", tint = SovereignStone800)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // EXECUTIVE MESSAGE MENU / DAILY AUDIT INBOX (AHEAD OF NEW CHAT)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showAuditInboxSheet = true
                                coroutineScope.launch { drawerState.close() }
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = SovereignCream,
                        border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(SovereignAmber.copy(alpha = 0.2f), RoundedCornerShape(6.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🛡️", fontSize = 16.sp)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Executive Message Menu",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = SovereignStone900
                                )
                                Text(
                                    "Daily Security Audit Inbox",
                                    fontSize = 11.sp,
                                    color = SovereignAmberDark
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                            ) {
                                Text(
                                    "SECURE",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF047857)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            viewModel.startNewChat()
                            coroutineScope.launch { drawerState.close() }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SovereignAmber),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New Session", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Start New Session", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = SovereignAmber.copy(alpha = 0.25f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "ACTIVE TARGET",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SovereignStone600,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = SovereignCream,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = selectedRepo ?: "Trinity Universe (Global)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = SovereignAmberDark
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (selectedRepo == null) "All organization repositories connected" else "Context constrained to $selectedRepo",
                                fontSize = 12.sp,
                                color = SovereignStone800
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                        "CHAT HISTORY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SovereignStone600,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (uiState.chatHistory.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                """""No prior sessions yet.
Start chatting to record history.""""",
                                fontSize = 12.sp,
                                color = SovereignStone600,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(uiState.chatHistory) { item ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            viewModel.restoreSession(item)
                                            coroutineScope.launch { drawerState.close() }
                                        },
                                    shape = RoundedCornerShape(8.dp),
                                    color = SovereignCream,
                                    border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.25f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = item.title,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp,
                                            color = SovereignStone900,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = item.repositoryContext ?: "Global Context",
                                                fontSize = 11.sp,
                                                color = SovereignAmberDark
                                            )
                                            Text(
                                                text = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(item.timestamp)),
                                                fontSize = 10.sp,
                                                color = SovereignStone600
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Total Cockpit Messages: ${uiState.chatMessages.size}",
                        fontSize = 12.sp,
                        color = SovereignStone600
                    )
                }
            }
        }
    ) {
        Scaffold(
        topBar = {
            Column(modifier = Modifier.background(SovereignCreamDarker)) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Open Navigation Drawer",
                                tint = SovereignStone800
                            )
                        }
                    },
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
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(
                                    "SOVEREIGN",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp,
                                    color = SovereignAmberDark,
                                    maxLines = 1,
                                    softWrap = false
                                )
                                Text(
                                    if (selectedRepo == null) "Trinity Universe • Global" else "$selectedRepo",
                                    fontSize = 11.sp,
                                    color = SovereignStone600,
                                    maxLines = 1,
                                    softWrap = false
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

                // SECONDARY NAVIGATION BAR: [CHAT] | [FILES] | [PREVIEW]
                val isChatActive = (activeTab == "CHAT")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // [💬 Chat]
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                activeTab = "CHAT"
                                showFilesSheet = false
                                showPreviewSheet = false
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (activeTab == "CHAT") SovereignAmber else SovereignCream,
                        border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.ChatBubble, contentDescription = "Chat", tint = SovereignAmberDark, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Chat", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SovereignStone900)
                        }
                    }

                    // [📁 Files]
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                activeTab = "FILES"
                                showPreviewSheet = false
                                showFilesSheet = false
                                if (uiState.repoTree.isEmpty() && !uiState.isTreeLoading) {
                                    viewModel.loadRepoTree(selectedRepo)
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (activeTab == "FILES") SovereignAmber else SovereignCream,
                        border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = "Files", tint = SovereignAmberDark, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Files", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SovereignStone900)
                        }
                    }

                    // [🌐 Preview]
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                activeTab = "PREVIEW"
                                showFilesSheet = false
                                showPreviewSheet = false
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (activeTab == "PREVIEW") SovereignAmber else SovereignCream,
                        border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Language, contentDescription = "Preview", tint = SovereignAmberDark, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Preview", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SovereignStone900)
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (activeTab == "CHAT") {
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
// Red banner eliminated for clean CEO keyboard ergonomics

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

                        // Persistent Stop & Action Controls: STOP is always accessible during active work
                        if (uiState.isStreaming) {
                            Surface(
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable {
                                        viewModel.cancelActiveStream()
                                        isVoiceActive = false
                                        Toast.makeText(context, "EMERGENCY STOP EXECUTED", Toast.LENGTH_SHORT).show()
                                    },
                                color = Color(0xFFDC2626),
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
                        }

                        if (chatInput.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val msg = chatInput
                                    chatInput = ""
                                    viewModel.sendMessage(msg)
                                },
                                modifier = Modifier.size(38.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = SovereignAmber)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = SovereignCream)
                            }
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

            when (activeTab) {
                "FILES" -> {
                    FullPageVsCodeExplorer(
                        selectedRepo = selectedRepo,
                        uiState = uiState,
                        searchQuery = fileSearchQuery,
                        onSearchChange = { fileSearchQuery = it },
                        selectedFile = selectedFileContent,
                        onSelectFile = { name, c -> selectedFileContent = name to c },
                        onCloseFile = { selectedFileContent = null },
                        onRetry = { viewModel.loadRepoTree(selectedRepo) }
                    )
                }
                "PREVIEW" -> {
                    FullPageModernPreview(
                        selectedRepo = selectedRepo,
                        deviceMode = previewDeviceMode,
                        onDeviceModeChange = { previewDeviceMode = it }
                    )
                }
                else -> {
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

    // MODAL BOTTOM SHEET: [🛡️ EXECUTIVE MESSAGE MENU / DAILY AUDIT INBOX]
    if (showAuditInboxSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAuditInboxSheet = false },
            containerColor = SovereignCreamDarker
        ) {
            ExecutiveAuditInboxSheet(
                onClose = { showAuditInboxSheet = false }
            )
        }
    }

                }
            }

    // [📁 FILES] and [🌐 PREVIEW] upgraded to Full-Page Modes
    if (false && showFilesSheet) {
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
                        LaunchedEffect(selectedRepo) {
                            if (selectedRepo != null) {
                                viewModel.loadRepoTree(selectedRepo)
                            }
                        }
                        if (selectedRepo == null) {
                            Text(
                                "Select a specific repository to browse its authorized file tree.",
                                fontSize = 12.sp,
                                color = SovereignStone600
                            )
                        } else if (uiState.isTreeLoading) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = SovereignAmberDark)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Loading repository tree...", fontSize = 12.sp, color = SovereignStone800)
                            }
                        } else if (uiState.treeError != null) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Failed to load tree: ${uiState.treeError}", fontSize = 12.sp, color = Color(0xFFDC2626))
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { viewModel.loadRepoTree(selectedRepo) },
                                    colors = ButtonDefaults.buttonColors(containerColor = SovereignAmber),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text("Retry", fontSize = 12.sp)
                                }
                            }
                        } else if (uiState.repoTree.isEmpty()) {
                            Text("No files found or empty repository.", fontSize = 12.sp, color = SovereignStone600)
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 350.dp)) {
                                items(uiState.repoTree) { item ->
                                    val icon = if (item.type == "tree") "📁 " else "📄 "
                                    Text(
                                        text = "$icon${item.path}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = SovereignStone900,
                                        modifier = Modifier.padding(vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // MODAL BOTTOM SHEET: [🌐 PREVIEW]
    if (false && showPreviewSheet) {
        val previewEval = remember(selectedRepo) {
            com.sovereign.commandcenter.preview.TruthfulPreviewCoordinator.evaluatePreview(
                kind = com.sovereign.commandcenter.preview.PreviewKind.WEB_APP,
                devServerPort = null,
                hasRenderSignal = false
            )
        }
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
                    Text("WORKSPACE PREVIEW", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SovereignAmberDark)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = when (previewEval.state) {
                            com.sovereign.commandcenter.preview.TruthfulPreviewState.RENDERED -> Color(0xFFD1FAE5)
                            com.sovereign.commandcenter.preview.TruthfulPreviewState.WAITING_FOR_RENDER -> Color(0xFFFEF3C7)
                            else -> Color(0xFFFEE2E2)
                        }
                    ) {
                        Text(
                            text = previewEval.state.name,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = when (previewEval.state) {
                                com.sovereign.commandcenter.preview.TruthfulPreviewState.RENDERED -> Color(0xFF047857)
                                com.sovereign.commandcenter.preview.TruthfulPreviewState.WAITING_FOR_RENDER -> Color(0xFFB45309)
                                else -> Color(0xFFDC2626)
                            },
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    color = SovereignCream,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.3f))
                ) {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = previewEval.truthfulReason,
                            color = SovereignStone800,
                            fontSize = 12.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
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
fun PacedStepAccordionCard(
    step: PacedStepData,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { 
        mutableStateOf(step.actionCard.status.equals("running", ignoreCase = true) || step.actionCard.status.equals("recovering", ignoreCase = true)) 
    }

    val (statusColor, statusBg, statusText) = when (step.actionCard.status.lowercase()) {
        "completed" -> Triple(Color(0xFF16A34A), Color(0xFFDCFCE7), "COMPLETED")
        "running" -> Triple(Color(0xFFD97706), Color(0xFFFEF3C7), "RUNNING")
        "recovering" -> Triple(Color(0xFFEA580C), Color(0xFFFFEDD5), "SELF-HEALING")
        "failed" -> Triple(Color(0xFFDC2626), Color(0xFFFEE2E2), "FAILED")
        else -> Triple(Color(0xFF6B7280), Color(0xFFF3F4F6), "QUEUED")
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E293B)
                    ) {
                        Text(
                            text = "Step ${step.stepIndex}/${step.totalSteps}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = "[${step.actionCard.tool}]",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF374151)
                    )

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusBg
                    ) {
                        Text(
                            text = statusText,
                            color = statusColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Text(
                    text = if (isExpanded) "▲" else "▼",
                    color = Color(0xFF6B7280),
                    fontSize = 12.sp
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))

                if (step.conversationalPrelude.isNotBlank()) {
                    Text(
                        text = step.conversationalPrelude,
                        fontSize = 13.sp,
                        color = Color(0xFF4B5563),
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF9FAFB),
                    border = BorderStroke(1.dp, Color(0xFFF3F4F6)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "ACTION TARGET",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF9CA3AF)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = step.actionCard.description,
                            fontSize = 12.sp,
                            color = Color(0xFF1F2937),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                step.selfHealingTrace?.let { trace ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFFBEB),
                        border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "🛡️ AUTONOMOUS SELF-HEALING (BOUNDED)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Diagnosis: ${trace.failureReason}",
                                fontSize = 11.sp,
                                color = Color(0xFF92400E)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Resolution: ${trace.logicalResolution}",
                                fontSize = 11.sp,
                                color = Color(0xFF78350F),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatMessageBubble(message: ChatMessage) {
    if (message.pacedStep != null) {
        PacedStepAccordionCard(step = message.pacedStep)
        return
    }
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

@Composable
fun ExecutiveAuditInboxSheet(
    onClose: () -> Unit
) {
    val reports = remember { ExecutiveAuditDigestProvider.dailyReports }
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🛡️", fontSize = 20.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        "EXECUTIVE MESSAGE MENU",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = SovereignAmberDark
                    )
                    Text(
                        "Daily Security Audit Digests",
                        fontSize = 11.sp,
                        color = SovereignStone600
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = SovereignStone800)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(reports) { report ->
                var expanded by remember { mutableStateOf(false) }
                val (badgeBg, badgeFg) = when (report.severity) {
                    AuditSeverity.CRITICAL -> Color(0xFFFEE2E2) to Color(0xFFDC2626)
                    AuditSeverity.WARNING -> Color(0xFFFEF3C7) to Color(0xFFD97706)
                    AuditSeverity.INFO -> Color(0xFFD1FAE5) to Color(0xFF059669)
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded },
                    shape = RoundedCornerShape(10.dp),
                    color = SovereignCream,
                    border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = report.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = SovereignStone900,
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = badgeBg
                            ) {
                                Text(
                                    text = report.severity.name,
                                    color = badgeFg,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = report.affectedScope,
                                fontSize = 11.sp,
                                color = SovereignAmberDark,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = report.timestamp,
                                fontSize = 10.sp,
                                color = SovereignStone600
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = report.summary,
                            fontSize = 12.sp,
                            color = SovereignStone800
                        )

                        if (expanded) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = SovereignAmber.copy(alpha = 0.2f), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "TECHNICAL AUDIT PROOF",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SovereignStone600,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = SovereignCreamDarker,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = report.technicalDetails,
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = SovereignStone900,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}


// =============================================================================
// FULL-PAGE VS CODE STYLE EXPLORER COMPONENT
// =============================================================================
@Composable
fun FullPageVsCodeExplorer(
    selectedRepo: String?,
    uiState: CommandCenterUiState,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    selectedFile: Pair<String, String>?,
    onSelectFile: (String, String) -> Unit,
    onCloseFile: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SovereignCream)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SovereignCreamDarker,
            border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.3f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, contentDescription = "Explorer", tint = SovereignAmberDark, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "EXPLORER: " + (selectedRepo ?: "Trinity Universe"),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SovereignStone900
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SovereignAmber.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "${uiState.repoTree.size} files",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SovereignAmberDark,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        if (selectedFile != null) {
            Column(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF1E1E1E)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedFile.first,
                            color = Color(0xFF9CDCFE),
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(onClick = onCloseFile, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Close", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E))
                        .padding(12.dp)
                ) {
                    Text(
                        text = selectedFile.second,
                        color = Color(0xFFD4D4D4),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchChange,
                    placeholder = { Text("Search files in repository...", fontSize = 12.sp, color = SovereignStone600) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SovereignAmber,
                        unfocusedBorderColor = SovereignAmber.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                when {
                    uiState.isTreeLoading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = SovereignAmberDark, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Loading repository tree...", fontSize = 12.sp, color = SovereignStone800)
                            }
                        }
                    }
                    uiState.treeError != null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(uiState.treeError ?: "", color = Color(0xFFDC2626), fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = onRetry,
                                    colors = ButtonDefaults.buttonColors(containerColor = SovereignAmber),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Retry Loading", color = SovereignStone900, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    uiState.repoTree.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No files found in active repository.", fontSize = 12.sp, color = SovereignStone600)
                        }
                    }
                    else -> {
                        val filtered = uiState.repoTree.filter { it.path.contains(searchQuery, ignoreCase = true) }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filtered) { entry ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSelectFile(entry.path, "// Synchronized Workspace Artifact: " + entry.path + "\n// VM Dignity Protocol")
                                        },
                                    color = SovereignCreamDarker,
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.15f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val icon = if (entry.type == "tree") "📁" else "📄"
                                        Text(icon, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = entry.path,
                                            fontSize = 12.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            color = SovereignStone900
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// FULL-PAGE MODERN RESPONSIVE UI PREVIEW COMPONENT
// =============================================================================
@Composable
fun FullPageModernPreview(
    selectedRepo: String?,
    deviceMode: String,
    onDeviceModeChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SovereignCream)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SovereignCreamDarker,
            border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(20.dp),
                        color = SovereignCream,
                        border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔒", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "http://localhost:5173",
                                fontSize = 12.sp,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                color = SovereignStone900
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("MOBILE" to "📱", "TABLET" to "📟", "DESKTOP" to "💻").forEach { (mode, icon) ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (deviceMode == mode) SovereignAmber else SovereignCream,
                                modifier = Modifier.clickable { onDeviceModeChange(mode) },
                                border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.4f))
                            ) {
                                Text(icon, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            val widthModifier = when (deviceMode) {
                "MOBILE" -> Modifier.width(360.dp)
                "TABLET" -> Modifier.width(520.dp)
                else -> Modifier.fillMaxWidth()
            }

            Surface(
                modifier = widthModifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp)),
                color = Color.White,
                shadowElevation = 8.dp,
                border = BorderStroke(2.dp, SovereignStone800.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(42.dp),
                        color = SovereignAmberDark,
                        strokeWidth = 3.dp
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Senior Sovereign Web Engine",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = SovereignStone900
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Syncing live dev server on port 5173\nWaiting for WebSocket compilation signal...",
                        fontSize = 12.sp,
                        color = SovereignStone600,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SovereignCream,
                        border = BorderStroke(1.dp, SovereignAmber.copy(alpha = 0.4f))
                    ) {
                        Text(
                            text = "PORT: 5173 • SANDBOX ISOLATED",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SovereignAmberDark,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
