package com.sovereign.commandcenter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sovereign.commandcenter.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SovereignTheme {
                CommandCenterCockpit()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommandCenterCockpit() {
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
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Security, contentDescription = "Passkey Status", tint = SovereignAmber)
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Notifications", tint = SovereignStone800)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SovereignCreamDarker
                )
            )
        },
        bottomBar = {
            Surface(
                color = SovereignCreamDarker,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .navigationBarsPadding()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = chatInput.value,
                        onValueChange = { chatInput.value = it },
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, SovereignAmber.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        placeholder = {
                            Text(
                                if (activeRepo.value == null) "Direct Sovereign across Trinity Universe..."
                                else "Message " + activeRepo.value + "...",
                                color = SovereignStone600,
                                fontSize = 13.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SovereignCream,
                            unfocusedContainerColor = SovereignCream,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { chatInput.value = "" },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = SovereignAmber)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send", tint = SovereignCream)
                    }
                }
            }
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
                            "Welcome, Adebola James Ogunjimi",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SovereignStone950
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "I am ready to analyze Trinity Universe, explain activity across your repositories, or begin work on a specific project.",
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
                                "Branch: main • Environment: Production • Health: Nominal (0 errors)",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = SovereignStone800
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
