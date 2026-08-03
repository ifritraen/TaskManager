package com.rk.taskmanager_pro

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.rk.bridge.ProBridge
import com.rk.taskmanager_pro.screens.BatteryScreen
import com.rk.taskmanager_pro.screens.NetScreen
import com.rk.taskmanager_pro.services.TaskNotificationService

@Keep
class ProBridgeImpl : ProBridge {

    private val isProState: MutableState<Boolean> = mutableStateOf(true)
    private val isPendingState: MutableState<Boolean> = mutableStateOf(false)
    private val isServiceRunningState: MutableState<Boolean> = mutableStateOf(false)

    override fun initApp(app: Application, launchPurchaseUiCallback: () -> Unit, onPurchaseCallback: () -> Unit) {
        // App initialization for Pro module
    }

    override fun launchPurchase(activity: Activity) {
        // Already unlocked
    }

    override suspend fun getProVersionPrice(): String? = "Free Pro"

    override fun isPro(): MutableState<Boolean> = isProState

    override fun isPending(): MutableState<Boolean> = isPendingState

    override fun isNotificationServiceRunning(): MutableState<Boolean> = isServiceRunningState

    override fun updatePurchaseStatus() {
        isProState.value = true
    }

    override fun launchNotificationService(context: Context) {
        val intent = Intent(context, TaskNotificationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        isServiceRunningState.value = true
    }

    override fun stopNotificationService(context: Context) {
        val intent = Intent(context, TaskNotificationService::class.java)
        context.stopService(intent)
        isServiceRunningState.value = false
    }

    @Composable
    override fun NetScreen() {
        NetScreen()
    }

    @Composable
    override fun BatteryScreen() {
        BatteryScreen()
    }
}
