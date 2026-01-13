package com.altron.alertbuddy.ui.theme.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.altron.alertbuddy.data.AlertRepository
import com.altron.alertbuddy.data.ChannelWithUnreadCount
import kotlinx.coroutines.launch

// Color constants for severity and badges
private val CriticalRed = Color(0xFFDC2626)
private val BadgeRed = Color(0xFFEF4444)

/**
 * Channel list screen - the main home screen after login.
 * Displays all alert channels with their unread message counts.
 * Shows a warning banner if there are any unread alerts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelListScreen(
    repository: AlertRepository,
    onChannelClick: (channelId: String, channelName: String) -> Unit,
    onSettingsClick: () -> Unit
) {
    // State for channels and loading
    var channels by remember { mutableStateOf<List<ChannelWithUnreadCount>>(emptyList()) }
    var totalUnread by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Function to load/refresh channel data
    fun loadData() {
        scope.launch {
            try {
                channels = repository.getChannelsWithUnreadCount()
                totalUnread = repository.getTotalUnreadCount()
            } catch (e: Exception) {
                Log.e("ChannelListScreen", "Error loading data", e)
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    // Load data on screen launch
    LaunchedEffect(Unit) {
        loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // App icon in header
                        Surface(
                            modifier = Modifier.size(28.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Notifications,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White
                                )
                            }
                        }
                        Text(
                            text = "Alert Buddy",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    // Settings button
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            // Loading state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Pull-to-refresh enabled list
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    loadData()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Show unread alerts banner if there are any
                    if (totalUnread > 0) {
                        item {
                            UnreadBanner(count = totalUnread)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Channel list
                    items(channels) { channel ->
                        ChannelRow(
                            channel = channel,
                            onClick = { onChannelClick(channel.id, channel.name) }
                        )
                    }

                    // Empty state when no channels
                    if (channels.isEmpty()) {
                        item {
                            EmptyChannelsState()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Red banner showing total unread alert count.
 * Displayed at the top of the channel list when there are unread alerts.
 */
@Composable
private fun UnreadBanner(count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = CriticalRed,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$count unread alert${if (count != 1) "s" else ""} require attention",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Individual channel row showing channel name and unread count badge.
 */
@Composable
private fun ChannelRow(
    channel: ChannelWithUnreadCount,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Channel icon - highlighted if has unread
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    tint = if (channel.unreadCount > 0)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Channel name - bold if has unread
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (channel.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Unread count badge
                if (channel.unreadCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = BadgeRed
                    ) {
                        Text(
                            text = if (channel.unreadCount > 99) "99+" else channel.unreadCount.toString(),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                // Navigation arrow
                Icon(
                    Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Empty state shown when there are no alert channels.
 */
@Composable
private fun EmptyChannelsState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Notifications,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No alert channels",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Your alert channels will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}