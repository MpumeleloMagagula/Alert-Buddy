package com.altron.alertbuddy.ui.theme.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.altron.alertbuddy.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * ShiftManagementScreen - Admin/Manager Shift Control
 *
 * Allows Admins and Managers to:
 * - View all team members
 * - Add/remove team members
 * - Change team member roles
 * - Create and manage shifts
 * - View handover history
 *
 * ACCESS: Only Admins and Managers can access this screen
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShiftManagementScreen(
    repository: AlertRepository,
    currentUser: User? = null,
    onBackClick: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var loggedInUser by remember { mutableStateOf(currentUser) }
    var teamMembers by remember { mutableStateOf<List<TeamMember>>(emptyList()) }
    var shifts by remember { mutableStateOf<List<Shift>>(emptyList()) }
    var handoverLogs by remember { mutableStateOf<List<HandoverLog>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(0) }

    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showCreateShiftDialog by remember { mutableStateOf(false) }
    var showEditRoleDialog by remember { mutableStateOf<TeamMember?>(null) }

    fun loadData() {
        scope.launch {
            isLoading = true

            // Load current user from repository if not passed
            if (loggedInUser == null) {
                loggedInUser = repository.getCurrentUser()
            }

            teamMembers = repository.getAllTeamMembers()
            shifts = repository.getAllShifts()
            handoverLogs = repository.getRecentHandovers(20)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shift Management") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            when (selectedTab) {
                0 -> FloatingActionButton(
                    onClick = { showAddMemberDialog = true }
                ) {
                    Icon(Icons.Default.Add, "Add Team Member")
                }
                1 -> FloatingActionButton(
                    onClick = { showCreateShiftDialog = true }
                ) {
                    Icon(Icons.Default.Add, "Create Shift")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Team") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Shifts") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("History") }
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                when (selectedTab) {
                    0 -> TeamMembersTab(
                        members = teamMembers,
                        currentUserId = loggedInUser?.id,
                        onEditRole = { showEditRoleDialog = it },
                        onDelete = { member ->
                            scope.launch {
                                repository.deleteTeamMember(member.id)
                                loadData()
                            }
                        },
                        onStatusChange = { member, status ->
                            scope.launch {
                                repository.updateTeamMemberStandbyStatus(member.id, status)
                                loadData()
                            }
                        }
                    )
                    1 -> ShiftsTab(
                        shifts = shifts,
                        teamMembers = teamMembers,
                        onActivate = { shift ->
                            scope.launch {
                                repository.activateShift(shift.id)
                                shift.assignedToId?.let { repository.setCurrentStandby(it) }
                                loadData()
                            }
                        },
                        onDelete = { shift ->
                            scope.launch {
                                repository.deleteShift(shift.id)
                                loadData()
                            }
                        }
                    )
                    2 -> HandoverHistoryTab(logs = handoverLogs)
                }
            }
        }
    }

    if (showAddMemberDialog) {
        AddTeamMemberDialog(
            onDismiss = { showAddMemberDialog = false },
            onConfirm = { name, email, role ->
                scope.launch {
                    val member = TeamMember(
                        id = UUID.randomUUID().toString(),
                        email = email,
                        displayName = name,
                        role = role,
                        standbyStatus = StandbyStatus.OFFLINE
                    )
                    repository.insertTeamMember(member)
                    loadData()
                    showAddMemberDialog = false
                }
            }
        )
    }

    if (showCreateShiftDialog) {
        CreateShiftDialog(
            teamMembers = teamMembers,
            currentUser = loggedInUser,
            onDismiss = { showCreateShiftDialog = false },
            onConfirm = { shift ->
                scope.launch {
                    repository.createShift(shift)
                    loadData()
                    showCreateShiftDialog = false
                }
            }
        )
    }

    showEditRoleDialog?.let { member ->
        EditRoleDialog(
            member = member,
            onDismiss = { showEditRoleDialog = null },
            onConfirm = { newRole ->
                scope.launch {
                    val updatedMember = member.copy(role = newRole)
                    repository.insertTeamMember(updatedMember)
                    loadData()
                    showEditRoleDialog = null
                }
            }
        )
    }
}

@Composable
private fun TeamMembersTab(
    members: List<TeamMember>,
    currentUserId: String?,
    onEditRole: (TeamMember) -> Unit,
    onDelete: (TeamMember) -> Unit,
    onStatusChange: (TeamMember, StandbyStatus) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(members) { member ->
            TeamMemberManagementCard(
                member = member,
                isCurrentUser = member.id == currentUserId,
                onEditRole = { onEditRole(member) },
                onDelete = { onDelete(member) },
                onStatusChange = { status -> onStatusChange(member, status) }
            )
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun TeamMemberManagementCard(
    member: TeamMember,
    isCurrentUser: Boolean,
    onEditRole: () -> Unit,
    onDelete: () -> Unit,
    onStatusChange: (StandbyStatus) -> Unit
) {
    var showStatusMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val statusColor = when (member.standbyStatus) {
        StandbyStatus.ON_STANDBY -> Color(0xFF4CAF50)
        StandbyStatus.AVAILABLE -> Color(0xFF2196F3)
        StandbyStatus.OFFLINE -> Color(0xFF9E9E9E)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = member.displayName.take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = member.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        if (isCurrentUser) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "(You)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = member.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Surface(
                    color = getRoleColor(member.role).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable { onEditRole() }
                ) {
                    Text(
                        text = member.role.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = getRoleColor(member.role)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    OutlinedButton(
                        onClick = { showStatusMenu = true }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(member.standbyStatus.name.replace("_", " "))
                        Icon(Icons.Default.ArrowDropDown, null)
                    }

                    DropdownMenu(
                        expanded = showStatusMenu,
                        onDismissRequest = { showStatusMenu = false }
                    ) {
                        StandbyStatus.entries.forEach { status ->
                            DropdownMenuItem(
                                text = { Text(status.name.replace("_", " ")) },
                                onClick = {
                                    onStatusChange(status)
                                    showStatusMenu = false
                                }
                            )
                        }
                    }
                }

                if (!isCurrentUser) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove Team Member") },
            text = { Text("Are you sure you want to remove ${member.displayName} from the team?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ShiftsTab(
    shifts: List<Shift>,
    teamMembers: List<TeamMember>,
    onActivate: (Shift) -> Unit,
    onDelete: (Shift) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (shifts.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No shifts scheduled",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Tap + to create a shift",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(shifts) { shift ->
                ShiftCard(
                    shift = shift,
                    dateFormat = dateFormat,
                    onActivate = { onActivate(shift) },
                    onDelete = { onDelete(shift) }
                )
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun ShiftCard(
    shift: Shift,
    dateFormat: SimpleDateFormat,
    onActivate: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (shift.isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = shift.assignedToName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${dateFormat.format(Date(shift.startTime))} - ${dateFormat.format(Date(shift.endTime))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                if (shift.isActive) {
                    Surface(
                        color = Color(0xFF4CAF50),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "ACTIVE",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (shift.handoverNotes != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Notes: ${shift.handoverNotes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (!shift.isActive) {
                    TextButton(onClick = onActivate) {
                        Text("Activate")
                    }
                }
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun HandoverHistoryTab(logs: List<HandoverLog>) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (logs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No handovers yet",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        } else {
            items(logs) { log ->
                HandoverLogCard(log = log, dateFormat = dateFormat)
            }
        }
    }
}

@Composable
private fun HandoverLogCard(
    log: HandoverLog,
    dateFormat: SimpleDateFormat
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${log.fromUserName} → ${log.toUserName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = dateFormat.format(Date(log.handoverAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                if (log.notes != null) {
                    Text(
                        text = log.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }

            if (log.pendingAlertsCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "${log.pendingAlertsCount} pending",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun AddTeamMemberDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, UserRole) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.USER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Team Member") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Role:", style = MaterialTheme.typography.labelLarge)

                UserRole.entries.forEach { role ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedRole = role },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role }
                        )
                        Text(role.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, email, selectedRole) },
                enabled = name.isNotBlank() && email.isNotBlank()
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun CreateShiftDialog(
    teamMembers: List<TeamMember>,
    currentUser: User?,
    onDismiss: () -> Unit,
    onConfirm: (Shift) -> Unit
) {
    var selectedMember by remember { mutableStateOf<TeamMember?>(null) }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Shift") },
        text = {
            Column {
                Text("Assign to:", style = MaterialTheme.typography.labelLarge)

                Spacer(modifier = Modifier.height(8.dp))

                teamMembers.forEach { member ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMember = member }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedMember?.id == member.id,
                            onClick = { selectedMember = member }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(member.displayName)
                            Text(
                                text = member.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    selectedMember?.let { member ->
                        val now = System.currentTimeMillis()
                        val shift = Shift(
                            id = UUID.randomUUID().toString(),
                            assignedToId = member.id,
                            assignedToName = member.displayName,
                            startTime = now,
                            endTime = now + (8 * 60 * 60 * 1000),
                            handoverNotes = notes.ifBlank { null },
                            createdById = currentUser?.id ?: "",
                            createdByName = currentUser?.displayName ?: currentUser?.email ?: "Unknown"
                        )
                        onConfirm(shift)
                    }
                },
                enabled = selectedMember != null
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EditRoleDialog(
    member: TeamMember,
    onDismiss: () -> Unit,
    onConfirm: (UserRole) -> Unit
) {
    var selectedRole by remember { mutableStateOf(member.role) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Role") },
        text = {
            Column {
                Text(
                    text = "Change role for ${member.displayName}:",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                UserRole.entries.forEach { role ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedRole = role },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedRole == role,
                            onClick = { selectedRole = role }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(role.name, fontWeight = FontWeight.Medium)
                            Text(
                                text = getRoleDescription(role),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedRole) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun getRoleColor(role: UserRole): Color = when (role) {
    UserRole.ADMIN -> Color(0xFFE91E63)
    UserRole.MANAGER -> Color(0xFF9C27B0)
    UserRole.USER -> Color(0xFF2196F3)
}

private fun getRoleDescription(role: UserRole): String = when (role) {
    UserRole.ADMIN -> "Full access to all features"
    UserRole.MANAGER -> "Can manage shifts and team"
    UserRole.USER -> "Basic access only"
}
