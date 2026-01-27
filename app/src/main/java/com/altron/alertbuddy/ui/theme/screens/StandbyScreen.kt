package com.altron.alertbuddy.ui.theme.screens

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.altron.alertbuddy.data.*
import kotlinx.coroutines.launch

/**
 * StandbyScreen - View Team Standby Status
 *
 * Shows who is currently on standby, who's available, and allows handover.
 *
 * FEATURES:
 * - Current on-standby person highlighted at top
 * - List of all team members with status indicators
 * - Handover button for current standby person
 * - Status: ON_STANDBY (green), AVAILABLE (blue), OFFLINE (gray)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandbyScreen(
    repository: AlertRepository,
    currentUser: User? = null,
    onBackClick: () -> Unit,
    onManageShifts: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    var loggedInUser by remember { mutableStateOf(currentUser) }
    var teamMembers by remember { mutableStateOf<List<TeamMember>>(emptyList()) }
    var currentStandby by remember { mutableStateOf<TeamMember?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showHandoverDialog by remember { mutableStateOf(false) }
    var selectedHandoverTarget by remember { mutableStateOf<TeamMember?>(null) }
    var handoverNotes by remember { mutableStateOf("") }

    fun loadData() {
        scope.launch {
            isLoading = true

            // Load current user from repository if not passed
            if (loggedInUser == null) {
                loggedInUser = repository.getCurrentUser()
            }

            teamMembers = repository.getAllTeamMembers()
            currentStandby = repository.getCurrentStandbyMember()

            // Initialize demo team members if none exist
            if (teamMembers.isEmpty() && loggedInUser != null) {
                repository.initializeDemoTeamMembers(
                    currentUserId = loggedInUser!!.id,
                    currentUserEmail = loggedInUser!!.email,
                    currentUserName = loggedInUser!!.displayName ?: ""
                )
                teamMembers = repository.getAllTeamMembers()
                currentStandby = repository.getCurrentStandbyMember()
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    val isCurrentUserOnStandby = currentStandby?.isCurrentUser == true
    // Admin always has access; also check the user's role from User entity
    val canManageShifts = loggedInUser?.role == UserRole.ADMIN || loggedInUser?.role == UserRole.MANAGER

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Standby Status") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (canManageShifts) {
                        IconButton(onClick = onManageShifts) {
                            Icon(Icons.Default.Settings, "Manage Shifts")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    CurrentStandbyCard(
                        standbyMember = currentStandby,
                        isCurrentUser = isCurrentUserOnStandby
                    )
                }

                if (isCurrentUserOnStandby) {
                    item {
                        HandoverButton(
                            onClick = { showHandoverDialog = true }
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Team Members",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(teamMembers) { member ->
                    TeamMemberCard(
                        member = member,
                        isCurrentStandby = member.id == currentStandby?.id,
                        onClick = {
                            if (isCurrentUserOnStandby && member.id != currentStandby?.id && member.standbyStatus == StandbyStatus.AVAILABLE) {
                                selectedHandoverTarget = member
                                showHandoverDialog = true
                            }
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }

    if (showHandoverDialog && currentStandby != null) {
        val availableMembers = teamMembers.filter {
            it.standbyStatus == StandbyStatus.AVAILABLE && it.id != currentStandby?.id
        }

        HandoverDialog(
            currentStandby = currentStandby!!,
            availableMembers = availableMembers,
            selectedTarget = selectedHandoverTarget,
            notes = handoverNotes,
            onNotesChange = { handoverNotes = it },
            onTargetSelect = { selectedHandoverTarget = it },
            onDismiss = {
                showHandoverDialog = false
                selectedHandoverTarget = null
                handoverNotes = ""
            },
            onConfirm = {
                selectedHandoverTarget?.let { target ->
                    scope.launch {
                        repository.performHandover(
                            fromMember = currentStandby!!,
                            toMember = target,
                            notes = handoverNotes.ifBlank { null }
                        )
                        loadData()
                        showHandoverDialog = false
                        selectedHandoverTarget = null
                        handoverNotes = ""
                    }
                }
            }
        )
    }
}

@Composable
private fun CurrentStandbyCard(
    standbyMember: TeamMember?,
    isCurrentUser: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Currently On Standby",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = standbyMember?.displayName?.take(2)?.uppercase() ?: "?",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = standbyMember?.displayName ?: "No one assigned",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            if (standbyMember != null) {
                Text(
                    text = standbyMember.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            if (isCurrentUser) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiary,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "This is you",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun HandoverButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondary
        )
    ) {
        Icon(
            Icons.Default.Refresh,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text("Hand Over to Someone Else")
    }
}

@Composable
private fun TeamMemberCard(
    member: TeamMember,
    isCurrentStandby: Boolean,
    onClick: () -> Unit
) {
    val statusColor = when (member.standbyStatus) {
        StandbyStatus.ON_STANDBY -> Color(0xFF4CAF50)
        StandbyStatus.AVAILABLE -> Color(0xFF2196F3)
        StandbyStatus.OFFLINE -> Color(0xFF9E9E9E)
    }

    val statusText = when (member.standbyStatus) {
        StandbyStatus.ON_STANDBY -> "On Standby"
        StandbyStatus.AVAILABLE -> "Available"
        StandbyStatus.OFFLINE -> "Offline"
    }

    val animatedColor by animateColorAsState(
        targetValue = statusColor,
        label = "statusColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = member.standbyStatus == StandbyStatus.AVAILABLE) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentStandby)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.displayName.take(2).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
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
                    if (member.isCurrentUser) {
                        Spacer(modifier = Modifier.width(8.dp))
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

                if (member.role != UserRole.USER) {
                    Text(
                        text = member.role.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Surface(
                color = animatedColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(animatedColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = animatedColor
                    )
                }
            }
        }
    }
}

@Composable
private fun HandoverDialog(
    currentStandby: TeamMember,
    availableMembers: List<TeamMember>,
    selectedTarget: TeamMember?,
    notes: String,
    onNotesChange: (String) -> Unit,
    onTargetSelect: (TeamMember) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hand Over Standby") },
        text = {
            Column {
                Text(
                    text = "Select who will take over standby duty:",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (availableMembers.isEmpty()) {
                    Text(
                        text = "No team members are currently available. Ask someone to set their status to Available first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                } else {
                    availableMembers.forEach { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTargetSelect(member) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedTarget?.id == member.id,
                                onClick = { onTargetSelect(member) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = member.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
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
                        onValueChange = onNotesChange,
                        label = { Text("Handover Notes (optional)") },
                        placeholder = { Text("Any pending issues to be aware of...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = selectedTarget != null
            ) {
                Text("Hand Over")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
