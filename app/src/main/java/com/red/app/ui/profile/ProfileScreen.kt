package com.red.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.red.app.data.MissAvRepository
import com.red.app.ui.jelly.NuvioAvatarIcon

/**
 * Profile & Settings Screen (Tab 4 "Profile").
 */
@Composable
fun ProfileScreen() {
    var addonSources by remember { mutableStateOf(MissAvRepository.getAddonSources()) }
    var newAddonName by remember { mutableStateOf("") }
    var newAddonUrl by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    var hardwareAccel by remember { mutableStateOf(true) }
    var autoPlayNext by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 100.dp)
    ) {
        // 1. Header: Profile Info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NuvioAvatarIcon(selected = true, size = 56)

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "Red User",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Default Profile",
                    color = Color(0xFF8E92A4),
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF21232C))
                    .border(1.dp, Color(0xFF383C4D), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "Switch Profile",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Addon Management Section (Where the user's MissAV addon is built-in & custom addons can be added!)
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Addon Sources",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFE50914))
                        .clickable { showAddDialog = !showAddDialog }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Add URL",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Add Addon Form
            if (showAddDialog) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1B1D25))
                        .border(1.dp, Color(0xFF383C4D), RoundedCornerShape(14.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Install Custom Stremio / Nuvio Addon",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        // Name Input
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF141620), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF2E3244), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            if (newAddonName.isEmpty()) {
                                Text(
                                    text = "Addon Name (optional)",
                                    color = Color(0xFF6B7082),
                                    fontSize = 13.sp
                                )
                            }
                            BasicTextField(
                                value = newAddonName,
                                onValueChange = { newAddonName = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                cursorBrush = SolidColor(Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // URL Input
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF141620), RoundedCornerShape(8.dp))
                                .border(1.dp, Color(0xFF2E3244), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            if (newAddonUrl.isEmpty()) {
                                Text(
                                    text = "https://.../manifest.json",
                                    color = Color(0xFF6B7082),
                                    fontSize = 13.sp
                                )
                            }
                            BasicTextField(
                                value = newAddonUrl,
                                onValueChange = { newAddonUrl = it },
                                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                cursorBrush = SolidColor(Color.White),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Box(
                                modifier = Modifier
                                    .clickable { showAddDialog = false }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(text = "Cancel", color = Color(0xFF8E92A4), fontSize = 13.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFE50914), RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (newAddonUrl.isNotBlank()) {
                                            val name = if (newAddonName.isNotBlank()) newAddonName else "Custom Addon"
                                            MissAvRepository.addAddonSource(name, newAddonUrl)
                                            addonSources = MissAvRepository.getAddonSources()
                                            newAddonName = ""
                                            newAddonUrl = ""
                                            showAddDialog = false
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Install",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Addon List Cards
            addonSources.forEach { source ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF161822))
                        .border(1.dp, Color(0xFF262938), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = source.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(
                                            color = if (source.isEnabled) Color(0xFF1E3A24) else Color(0xFF332020),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (source.isEnabled) "ACTIVE" else "DISABLED",
                                        color = if (source.isEnabled) Color(0xFF4CAF50) else Color(0xFFFF5252),
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = source.manifestUrl,
                                color = Color(0xFF7E8398),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = source.isEnabled,
                                onCheckedChange = {
                                    MissAvRepository.toggleAddon(source.id)
                                    addonSources = MissAvRepository.getAddonSources()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFFE50914)
                                )
                            )

                            if (!source.isBuiltIn) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove",
                                    tint = Color(0xFF8E92A4),
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable {
                                            MissAvRepository.removeAddon(source.id)
                                            addonSources = MissAvRepository.getAddonSources()
                                        }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Playback Preferences
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = "Player Configuration",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Hardware Accel Setting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF161822))
                    .border(1.dp, Color(0xFF262938), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hardware Accelerated Decoding",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "GPU accelerated video rendering",
                        color = Color(0xFF8E92A4),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = hardwareAccel,
                    onCheckedChange = { hardwareAccel = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFE50914)
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Auto Play Next Setting
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF161822))
                    .border(1.dp, Color(0xFF262938), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Auto-play Continuous Streams",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Automatically buffer next episode",
                        color = Color(0xFF8E92A4),
                        fontSize = 11.sp
                    )
                }
                Switch(
                    checked = autoPlayNext,
                    onCheckedChange = { autoPlayNext = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFFE50914)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. About Red Card
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF161822))
                .border(1.dp, Color(0xFF262938), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = "Red Media Player • Nuvio Edition",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Pixel-perfect Nuvio UI architecture with built-in MissAV HLS proxy bypass, gesture video player, and Stremio addon compatibility.",
                    color = Color(0xFF8E92A4),
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}
