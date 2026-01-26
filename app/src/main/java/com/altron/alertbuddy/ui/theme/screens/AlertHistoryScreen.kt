package com.altron.alertbuddy.ui.theme.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.altron.alertbuddy.data.AlertRepository
import com.altron.alertbuddy.data.Message
import com.altron.alertbuddy.data.Severity
import com.altron.alertbuddy.ui.theme.AlertBuddyColors
import com.altron.alertbuddy.util.ExportHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * ============================================================================
 * AlertHistoryScreen.kt - Archived/Acknowledged Alerts
 * ============================================================================
 *
 * PURPOSE:
 * Display a history of all acknowledged alerts for review and auditing.
 *
 * FEATURES:
 * - List of all acknowledged alerts
 * - Filter by date range
 * - Search functionality
 * - Tap to view details
 *
 * ============================================================================
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertHistoryScreen(
    repository: AlertRepository,
    onBackClick: () -> Unit,
    onAlertClick: (messageId: String, channelId: String, channelName: String) -> Unit
) {
    var acknowledgedAlerts by remember { mutableStateOf<List<Message>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    var showExportMenu by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun loadData() {
        scope.launch {
            acknowledgedAlerts = repository.getAcknowledgedAlerts()
            isLoading = false
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    // Filter alerts based on search and filter
    val filteredAlerts = remember(acknowledgedAlerts, searchQuery, selectedFilter) {
        acknowledgedAlerts.filter { alert ->
            val matchesSearch = searchQuery.isEmpty() ||
                    alert.title.contains(searchQuery, ignoreCase = true) ||
                    alert.message.contains(searchQuery, ignoreCase = true) ||
                    alert.channelName.contains(searchQuery, ignoreCase = true)

            val matchesFilter = when (selectedFilter) {
                HistoryFilter.ALL -> true
                HistoryFilter.TODAY -> isToday(alert.acknowledgedAt ?: alert.timestamp)
                HistoryFilter.THIS_WEEK -> isThisWeek(alert.acknowledgedAt ?: alert.timestamp)
                HistoryFilter.CRITICAL -> alert.severity == Severity.CRITICAL
                HistoryFilter.WARNING -> alert.severity == Severity.WARNING
            }

            matchesSearch && matchesFilter
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Alert History") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showExportMenu = true },
                            enabled = acknowledgedAlerts.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Export"
                            )
                        }

                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export as CSV") },
                                onClick = {
                                    showExportMenu = false
                                    ExportHelper.exportToCsv(
                                        context = context,
                                        alerts = filteredAlerts,
                                        title = "Alert History Export"
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.List,
                                        contentDescription = null
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export as Report (HTML)") },
                                onClick = {
                                    showExportMenu = false
                                    ExportHelper.exportToHtml(
                                        context = context,
                                        alerts = filteredAlerts,
                                        title = "Alert History Report"
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Email,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search alerts...") },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Filter chips
            ScrollableFilterChips(
                selectedFilter = selectedFilter,
                onFilterSelected = { selectedFilter = it }
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        isRefreshing = true
                        loadData()
                    },
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (filteredAlerts.isEmpty()) {
                        EmptyHistoryState(
                            hasFilters = searchQuery.isNotEmpty() || selectedFilter != HistoryFilter.ALL
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Group alerts by date
                            val groupedAlerts = filteredAlerts.groupBy { alert ->
                                formatDateHeader(alert.acknowledgedAt ?: alert.timestamp)
                            }

                            groupedAlerts.forEach { (dateHeader, alerts) ->
                                item {
                                    Text(
                                        text = dateHeader,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                    )
                                }

                                items(alerts) { alert ->
                                    HistoryAlertRow(
                                        alert = alert,
                                        onClick = { onAlertClick(alert.id, alert.channelId, alert.channelName) }
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

enum class HistoryFilter(val label: String) {
    ALL("All"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    CRITICAL("Critical"),
    WARNING("Warning")
}

@Composable
private fun ScrollableFilterChips(
    selectedFilter: HistoryFilter,
    onFilterSelected: (HistoryFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HistoryFilter.entries.forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.label) },
                leadingIcon = if (selectedFilter == filter) {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else null
            )
        }
    }
}

@Composable
private fun HistoryAlertRow(
    alert: Message,
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
            verticalAlignment = Alignment.Top
        ) {
            // Severity indicator
            Surface(
                modifier = Modifier.size(8.dp),
                shape = RoundedCornerShape(4.dp),
                color = when (alert.severity) {
                    Severity.CRITICAL -> AlertBuddyColors.Critical
                    Severity.WARNING -> AlertBuddyColors.Warning
                    Severity.INFO -> AlertBuddyColors.Info
                }
            ) {}

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = alert.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Acknowledged checkmark
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Acknowledged",
                        tint = AlertBuddyColors.Success,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Channel tag
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = alert.channelName,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Acknowledged time
                    Text(
                        text = formatAcknowledgedTime(alert.acknowledgedAt ?: alert.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyHistoryState(hasFilters: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (hasFilters) "No matching alerts" else "No acknowledged alerts",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (hasFilters)
                "Try adjusting your search or filters"
            else
                "Acknowledged alerts will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ============================================================================
// UTILITY FUNCTIONS
// ============================================================================

private fun formatDateHeader(timestamp: Long): String {
    val calendar = Calendar.getInstance()
    val today = calendar.clone() as Calendar

    calendar.timeInMillis = timestamp

    val dateFormat = SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())

    return when {
        isSameDay(calendar, today) -> "Today"
        isSameDay(calendar, today.apply { add(Calendar.DAY_OF_YEAR, -1) }) -> "Yesterday"
        else -> dateFormat.format(Date(timestamp))
    }
}

private fun formatAcknowledgedTime(timestamp: Long): String {
    val timeFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    return timeFormat.format(Date(timestamp))
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun isToday(timestamp: Long): Boolean {
    val calendar = Calendar.getInstance()
    val today = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    return isSameDay(calendar, today)
}

private fun isThisWeek(timestamp: Long): Boolean {
    val calendar = Calendar.getInstance()
    val today = Calendar.getInstance()
    calendar.timeInMillis = timestamp

    val weekStart = today.clone() as Calendar
    weekStart.set(Calendar.DAY_OF_WEEK, weekStart.firstDayOfWeek)
    weekStart.set(Calendar.HOUR_OF_DAY, 0)
    weekStart.set(Calendar.MINUTE, 0)
    weekStart.set(Calendar.SECOND, 0)
    weekStart.set(Calendar.MILLISECOND, 0)

    return calendar.timeInMillis >= weekStart.timeInMillis
}
