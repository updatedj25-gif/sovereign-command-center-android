package com.sovereign.commandcenter.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

private val SovereignNavy = Color(0xFF0F172A)
private val SovereignCardBg = Color(0xFF1E293B)
private val SovereignBorder = Color(0xFF334155)
private val SovereignGold = Color(0xFFFFD700)
private val SovereignEmerald = Color(0xFF00E676)
private val SovereignTextWhite = Color(0xFFF8FAFC)
private val SovereignTextMuted = Color(0xFF94A3B8)
private val SovereignDanger = Color(0xFFEF4444)

data class ServiceItem(
    val id: String,
    val name: String,
    val icon: String,
    var price: String = "Tap Check",
    var successRate: Int = 0
)

data class PoolNumber(
    val id: String,
    val number: String,
    val displayNumber: String,
    val areaCode: String,
    val state: String
)

@Composable
fun UsaSmsHubScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val edgeBaseUrl = "https://sovereign-agent-production.updatedj25.workers.dev"

    // Balance State (Manual trigger only - 0 background drain)
    var balanceText by remember { mutableStateOf("Tap to Check") }
    var isCheckingBalance by remember { mutableStateOf(false) }
    var balanceCooldown by remember { mutableStateOf(0) }

    // Service Deck
    val services = remember {
        mutableStateListOf(
            ServiceItem("329", "Facebook USA", "📱"),
            ServiceItem("457", "Instagram USA", "📸"),
            ServiceItem("907", "Telegram USA", "✈️"),
            ServiceItem("1012", "WhatsApp USA", "💬")
        )
    }

    // Number Pool & Active Lease
    var poolNumbers by remember { mutableStateOf<List<PoolNumber>>(emptyList()) }
    var isLoadingPool by remember { mutableStateOf(false) }
    var selectedService by remember { mutableStateOf<ServiceItem?>(null) }

    // Active Lease State
    var activeOrderNumber by remember { mutableStateOf<String?>(null) }
    var activeOrderId by remember { mutableStateOf<String?>(null) }
    var activeStateBadge by remember { mutableStateOf<String?>(null) }

    // OTP Snapshot & Safety Debounce
    var otpCode by remember { mutableStateOf<String?>(null) }
    var isCheckingOtp by remember { mutableStateOf(false) }
    var otpDebounceSeconds by remember { mutableStateOf(0) }
    var cancelLockoutSeconds by remember { mutableStateOf(0) }

    // Helpers
    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    // Cooldown tickers
    LaunchedEffect(balanceCooldown) {
        if (balanceCooldown > 0) {
            delay(1000)
            balanceCooldown -= 1
        }
    }
    LaunchedEffect(otpDebounceSeconds) {
        if (otpDebounceSeconds > 0) {
            delay(1000)
            otpDebounceSeconds -= 1
        }
    }
    LaunchedEffect(cancelLockoutSeconds) {
        if (cancelLockoutSeconds > 0) {
            delay(1000)
            cancelLockoutSeconds -= 1
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = SovereignNavy
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // TOP HEADER: Title & Top-Right Manual Balance Trigger
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "📱 USA SMS HUB",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = SovereignTextWhite
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = SovereignEmerald.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "USA ONLY ⚜️",
                                color = SovereignEmerald,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Physical Non-VoIP Lines • Direct Carrier Telemetry",
                        fontSize = 11.sp,
                        color = SovereignTextMuted
                    )
                }

                // TOP-RIGHT CORNER BALANCE TRIGGER (0% AUTO DRAIN)
                Surface(
                    color = SovereignCardBg,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, SovereignGold.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable(enabled = !isCheckingBalance && balanceCooldown == 0) {
                        coroutineScope.launch {
                            isCheckingBalance = true
                            try {
                                val res = withContext(Dispatchers.IO) {
                                    val conn = URL("$edgeBaseUrl/api/sms/balance").openConnection() as HttpURLConnection
                                    conn.requestMethod = "GET"
                                    conn.connectTimeout = 5000
                                    conn.readTimeout = 5000
                                                    val stream = if (conn.responseCode in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
                                                    val reader = BufferedReader(InputStreamReader(stream))
                                    val json = JSONObject(reader.readText())
                                    reader.close()
                                    json.optString("balance", "0.00")
                                }
                                balanceText = "$$res USD"
                                balanceCooldown = 3
                            } catch (e: Exception) {
                                balanceText = "Err 🔄"
                            } finally {
                                isCheckingBalance = false
                            }
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when {
                                isCheckingBalance -> "⏳ Querying..."
                                balanceCooldown > 0 -> "$balanceText (${balanceCooldown}s)"
                                else -> "💳 $balanceText 🔄"
                            },
                            color = SovereignGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // BODY: STATE MACHINE (NO AI CHAT DISTRACTION)
            if (activeOrderNumber == null) {
                // STATE 1 & 2: SERVICE DECK & NUMBER POOL
                Text(
                    text = "TARGET META & MESSAGING SERVICES",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = SovereignGold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Service Cards
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    services.forEachIndexed { index, svc ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedService = svc
                                    isLoadingPool = true
                                    coroutineScope.launch {
                                        try {
                                            val pool = withContext(Dispatchers.IO) {
                                                val conn = URL("$edgeBaseUrl/api/sms/batch?service=${svc.id}").openConnection() as HttpURLConnection
                                                conn.requestMethod = "GET"
                                                    val stream = if (conn.responseCode in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
                                                    val reader = BufferedReader(InputStreamReader(stream))
                                                val json = JSONObject(reader.readText())
                                                reader.close()
                                                val arr = json.optJSONArray("numbers") ?: JSONArray()
                                                val list = mutableListOf<PoolNumber>()
                                                for (i in 0 until arr.length()) {
                                                    val obj = arr.getJSONObject(i)
                                                    list.add(
                                                        PoolNumber(
                                                            id = obj.getString("id"),
                                                            number = obj.getString("number"),
                                                            displayNumber = obj.getString("displayNumber"),
                                                            areaCode = obj.getString("areaCode"),
                                                            state = obj.getString("state")
                                                        )
                                                    )
                                                }
                                                list
                                            }
                                            poolNumbers = pool
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Failed to load pool", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isLoadingPool = false
                                        }
                                    }
                                },
                            color = if (selectedService?.id == svc.id) SovereignCardBg.copy(alpha = 0.9f) else SovereignCardBg,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selectedService?.id == svc.id) SovereignGold else SovereignBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = svc.icon, fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = svc.name,
                                            fontWeight = FontWeight.Bold,
                                            color = SovereignTextWhite,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Wholesale: ${svc.price}",
                                            color = SovereignEmerald,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                // Manual Check Price Button
                                Surface(
                                    color = SovereignNavy,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, SovereignBorder),
                                    modifier = Modifier.clickable {
                                        coroutineScope.launch {
                                            try {
                                                val res = withContext(Dispatchers.IO) {
                                                    val conn = URL("$edgeBaseUrl/api/sms/prices").openConnection() as HttpURLConnection
                                                    conn.requestMethod = "GET"
                                                    val stream = if (conn.responseCode in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
                                                    val reader = BufferedReader(InputStreamReader(stream))
                                                    val json = JSONObject(reader.readText())
                                                    reader.close()
                                                    val arr = json.optJSONArray("services") ?: JSONArray()
                                                    for (i in 0 until arr.length()) {
                                                        val obj = arr.getJSONObject(i)
                                                        if (obj.getString("id") == svc.id) {
                                                            return@withContext "$${obj.optString("price", "0.00")} USD"
                                                        }
                                                    }
                                                    "N/A"
                                                }
                                                services[index] = svc.copy(price = res)
                                            } catch (e: Exception) {
                                                services[index] = svc.copy(price = "Err")
                                            }
                                        }
                                    }
                                ) {
                                    Text(
                                        text = "🔄 Check Price",
                                        color = SovereignGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 10-NUMBER STREAM POOL
                if (selectedService != null) {
                    Text(
                        text = "10 LIVE USA NON-VOIP NUMBERS (${selectedService?.name})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SovereignGold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    if (isLoadingPool) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("⚡ Streaming 10 physical USA lines...", color = SovereignGold, fontSize = 13.sp)
                        }
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            poolNumbers.forEach { item ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            // CEO One-Tap Selection
                                            coroutineScope.launch {
                                                copyToClipboard("US Number", item.displayNumber)
                                                activeOrderNumber = item.displayNumber
                                                activeStateBadge = item.state
                                                cancelLockoutSeconds = 120 // 2-min mandatory lockout
                                                poolNumbers = emptyList() // Purge remaining 9

                                            // Acquire live physical carrier lease from Edge Worker
                                            try {
                                                val leaseResult = withContext(Dispatchers.IO) {
                                                    val conn = URL("$edgeBaseUrl/api/sms/lease").openConnection() as HttpURLConnection
                                                    conn.requestMethod = "POST"
                                                    conn.setRequestProperty("Content-Type", "application/json")
                                                    conn.doOutput = true
                                                    conn.connectTimeout = 15000
                                                    conn.readTimeout = 15000
                                                    val body = JSONObject().apply {
                                                        put("service", selectedService?.id ?: "396")
                                                        put("areaCode", item.areaCode)
                                                    }
                                                    conn.outputStream.write(body.toString().toByteArray())
                                                    val stream = if (conn.responseCode in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
                                                    val reader = BufferedReader(InputStreamReader(stream))
                                                    val json = JSONObject(reader.readText())
                                                    reader.close()
                                                    json
                                                }
                                                val realOid = leaseResult.optString("order_id", "")
                                                val realNumber = leaseResult.optString("number", leaseResult.optString("displayNumber", ""))
                                                if (realOid.isNotEmpty()) {
                                                    activeOrderId = realOid
                                                }
                                                if (realNumber.isNotEmpty()) {
                                                    activeOrderNumber = realNumber
                                                    copyToClipboard("US Number", realNumber)
                                                    Toast.makeText(context, "Live Number Allocated: " + realNumber, Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                val err = leaseResult.optString("error", "")
                                                if (err.isNotEmpty()) {
                                                    Toast.makeText(context, "Carrier: " + err, Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "Order active: listening for OTP", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            }
                                        },
                                    color = SovereignCardBg,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, SovereignBorder)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Surface(
                                                color = SovereignNavy,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = item.state,
                                                    color = SovereignGold,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = item.displayNumber,
                                                fontWeight = FontWeight.Bold,
                                                color = SovereignTextWhite,
                                                fontSize = 15.sp
                                            )
                                        }

                                        Surface(
                                            color = SovereignEmerald.copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "SELECT ⚡",
                                                color = SovereignEmerald,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // STATE 3 & 4: ACTIVE DEPLOYMENT & OTP SNAPSHOT CARD
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        color = SovereignCardBg,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, SovereignGold),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "ACTIVE LEASE DEPLOYMENT",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SovereignGold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = activeStateBadge ?: "US - UNITED STATES",
                                fontSize = 13.sp,
                                color = SovereignTextMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = activeOrderNumber ?: "",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SovereignTextWhite
                                )
                                Surface(
                                    color = SovereignNavy,
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.clickable {
                                        copyToClipboard("US Number", activeOrderNumber ?: "")
                                    }
                                ) {
                                    Text(
                                        text = "📋 Re-Copy",
                                        color = SovereignGold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // OTP ARRIVAL CONTAINER (EMERALD MONOSPACE)
                    if (otpCode != null) {
                        Surface(
                            color = Color(0xFF064E3B),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(2.dp, SovereignEmerald),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "OTP CODE RECEIVED",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SovereignEmerald
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = otpCode ?: "",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { copyToClipboard("OTP Code", otpCode ?: "") },
                                    colors = ButtonDefaults.buttonColors(containerColor = SovereignEmerald)
                                ) {
                                    Text("📋 COPY OTP CODE", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        // MANUAL SNAPSHOT TRIGGER (5s Debounce)
                        Button(
                            onClick = {
                                if (otpDebounceSeconds == 0 && !isCheckingOtp) {
                                    isCheckingOtp = true
                                    otpDebounceSeconds = 5
                                    coroutineScope.launch {
                                        try {
                                            val code = withContext(Dispatchers.IO) {
                                                val oid = activeOrderId ?: ""
                                                if (oid.isEmpty()) {
                                                    Toast.makeText(context, "Please select an active line first", Toast.LENGTH_SHORT).show()
                                                    return@launch
                                                }
                                                val conn = URL("$edgeBaseUrl/api/sms/check?orderid=$oid").openConnection() as HttpURLConnection
                                                conn.requestMethod = "GET"
                                                    val stream = if (conn.responseCode in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
                                                    val reader = BufferedReader(InputStreamReader(stream))
                                                val json = JSONObject(reader.readText())
                                                reader.close()
                                                json.optString("code", null) ?: json.optString("sms", null)
                                            }
                                            if (!code.isNullOrEmpty() && code != "null") {
                                                otpCode = code
                                                copyToClipboard("OTP Code", code)
                                            } else {
                                                Toast.makeText(context, "OTP not arrived yet. Try again.", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Snapshot check failed", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            isCheckingOtp = false
                                        }
                                    }
                                }
                            },
                            enabled = otpDebounceSeconds == 0 && !isCheckingOtp,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SovereignGold)
                        ) {
                            Text(
                                text = when {
                                    isCheckingOtp -> "⏳ FETCHING SNAPSHOT..."
                                    otpDebounceSeconds > 0 -> "WAITING ${otpDebounceSeconds}s..."
                                    else -> "🔄 FETCH LATEST OTP SNAPSHOT"
                                },
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // 120s SAFETY CANCEL LOCKOUT BUTTON
                    OutlinedButton(
                        onClick = {
                            if (cancelLockoutSeconds == 0) {
                                coroutineScope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                                val oid = activeOrderId ?: ""
                                                if (oid.isEmpty()) {
                                                    Toast.makeText(context, "Please select an active line first", Toast.LENGTH_SHORT).show()
                                                    return@launch
                                                }
                                            val conn = URL("$edgeBaseUrl/api/sms/cancel").openConnection() as HttpURLConnection
                                            conn.requestMethod = "POST"
                                            conn.setRequestProperty("Content-Type", "application/json")
                                            conn.doOutput = true
                                            val b = JSONObject().apply { put("orderid", oid) }
                                            conn.outputStream.write(b.toString().toByteArray())
                                            conn.inputStream.close()
                                        }
                                        Toast.makeText(context, "Lease cancelled. Funds released.", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Cancelled and reset.", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        activeOrderNumber = null
                                        activeOrderId = null
                                        otpCode = null
                                    }
                                }
                            }
                        },
                        enabled = cancelLockoutSeconds == 0,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (cancelLockoutSeconds == 0) SovereignDanger else SovereignBorder)
                    ) {
                        Text(
                            text = if (cancelLockoutSeconds > 0) {
                                "❌ CANCEL LEASE (LOCKED ${cancelLockoutSeconds}s)"
                            } else {
                                "❌ CANCEL LEASE & RELEASE FUNDS"
                            },
                            color = if (cancelLockoutSeconds == 0) SovereignDanger else SovereignTextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
