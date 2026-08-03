package com.rk.taskmanager_pro.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BatteryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Unknown") }
    var health by remember { mutableStateOf("Unknown") }
    var plugged by remember { mutableStateOf("Unplugged") }
    var temperature by remember { mutableFloatStateOf(0f) }
    var voltage by remember { mutableIntStateOf(0) }
    var technology by remember { mutableStateOf("Unknown") }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent == null) return
                val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (rawLevel != -1 && scale != -1) {
                    level = (rawLevel * 100 / scale.toFloat()).toInt()
                }

                val statusRaw = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                status = when (statusRaw) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                    BatteryManager.BATTERY_STATUS_FULL -> "Full"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                    else -> "Unknown"
                }

                val healthRaw = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
                health = when (healthRaw) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
                    BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
                    else -> "Unknown"
                }

                val pluggedRaw = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                plugged = when (pluggedRaw) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "AC Power"
                    BatteryManager.BATTERY_PLUGGED_USB -> "USB Power"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
                    else -> "Discharging / Unplugged"
                }

                val tempRaw = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)
                temperature = tempRaw / 10f

                voltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
                technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Li-ion"
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(receiver, filter)
        if (stickyIntent != null) {
            receiver.onReceive(context, stickyIntent)
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Battery Status & Health",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Outlined.BatteryChargingFull,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$level%",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "$status • $plugged",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BatteryInfoRow(label = "Health", value = health)
                HorizontalDivider()
                BatteryInfoRow(label = "Temperature", value = "$temperature °C")
                HorizontalDivider()
                BatteryInfoRow(label = "Voltage", value = "$voltage mV")
                HorizontalDivider()
                BatteryInfoRow(label = "Technology", value = technology)
            }
        }
    }
}

@Composable
private fun BatteryInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}
