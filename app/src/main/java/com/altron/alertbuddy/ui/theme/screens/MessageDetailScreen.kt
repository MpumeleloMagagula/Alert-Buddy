package com.altron.alertbuddy.ui.theme.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.altron.alertbuddy.data.AlertRepository
import com.altron.alertbuddy.data.Message
import com.altron.alertbuddy.data.Severity
import com.altron.alertbuddy.service.AlertService
import com.altron.alertbuddy.ui.theme.getSeverityColor
import com.altron.alertbuddy.ui.theme.AlertBuddyColors
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// Screen showing full details of a single alert message
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageDetailScreen(
    repository: AlertRepository,
    messageId: String,
    onBackClick: () -> Unit
) {
    var message by remember { mutableStateOf<Message?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Load message on screen open
    LaunchedEffect(messageId) {
        message = repository.getMessage(messageId)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alert Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (message == null) {
            // Message not found state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Message not found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val msg = message!!
            val severityColor = getSeverityColor(msg.severity.name)
            val severityLabel = when (msg.severity) {
                Severity.CRITICAL -> "Critical"
                Severity.WARNING -> "Warning"
                Severity.INFO -> "Info"
            }
            val severityIcon = when (msg.severity) {
                Severity.CRITICAL -> Icons.Default.Warning
                Severity.WARNING -> Icons.Default.Warning
                Severity.INFO -> Icons.Default.Info
            }

            // Parse metadata JSON
            val parsedMetadata = remember(msg.metadata) {
                msg.metadata?.let { metadataJson ->
                    runCatching {
                        val json = JSONObject(metadataJson)
                        val result = mutableListOf<Pair<String, String>>()
                        json.keys().forEach { key ->
                            result.add(key.replaceFirstChar { it.uppercase() } to json.getString(key))
                        }
                        result.toList()
                    }.getOrNull()
                } ?: emptyList()
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Severity banner with title
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        color = severityColor,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                severityIcon,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = msg.title,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Color.White.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = severityLabel,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Metadata section
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        MetadataRow("Channel", msg.channelName)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        MetadataRow("Severity", severityLabel)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                        MetadataRow("Time", formatDateTime(msg.timestamp))

                        // Render additional metadata from JSON
                        parsedMetadata.forEach { (key, value) ->
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            MetadataRow(key, value)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Message content section
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = "MESSAGE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = msg.message,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 24.sp
                            )
                        }
                    }

                    // Acknowledged indicator
                    if (msg.isRead) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = AlertBuddyColors.Success,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Acknowledged",
                                color = AlertBuddyColors.Success,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Mark as Read button (only shown for unread messages)
                if (!msg.isRead) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.background,
                        shadowElevation = 8.dp
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    // Mark message as read
                                    repository.markAsRead(messageId)
                                    message = message?.copy(isRead = true)

                                    // Check if all alerts are now read - stop service if so
                                    val unreadCount = repository.getTotalUnreadCount()
                                    if (unreadCount == 0) {
                                        AlertService.stopService(context)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .height(52.dp),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Text(
                                text = "Mark as Read",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}