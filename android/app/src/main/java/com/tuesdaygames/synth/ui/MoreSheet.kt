//
//  MoreSheet.kt
//
//  The More screen: unlock the full version, the tip jar, send feedback, a few
//  settings and the about line. Opened from the top bar, or from a locked
//  feature. Ported from iOS's MoreMenu.swift.
//

package com.tuesdaygames.synth.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tuesdaygames.synth.BuildConfig
import com.tuesdaygames.synth.Feedback
import com.tuesdaygames.synth.KeyValueStore
import com.tuesdaygames.synth.Prefs
import com.tuesdaygames.synth.store.FullVersion
import com.tuesdaygames.synth.store.PurchaseOutcome
import com.tuesdaygames.synth.store.Store
import com.tuesdaygames.synth.store.StoreModel

@Composable
fun MoreSheet(
    store: StoreModel,
    prefs: KeyValueStore,
    activity: Activity,
    onSettingsChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Theme.panel)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("More", style = Theme.titleStyle(17.sp), color = Theme.cream, modifier = Modifier.align(Alignment.Center))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Text("Done", color = Theme.accent, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    FullVersionSection(store, activity)
                    TipJarCard(store, prefs, activity)
                    FeedbackSection(store.isUnlocked, activity)
                    SettingsSection(prefs, onSettingsChanged)
                    AboutSection(store)
                }
            }
        }
    }
}

@Composable
private fun Section(header: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(header, style = Theme.titleStyle(13.sp), color = Theme.label, modifier = Modifier.padding(start = 4.dp))
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .background(Theme.module, RoundedCornerShape(16.dp))
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
private fun FullVersionSection(store: StoreModel, activity: Activity) {
    val state = store.state
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Section("808Synth: Full Version") {
        if (state.isUnlocked) {
            Text("You own the full version.\nThank you for supporting 808Synth!", color = Theme.cream)
        } else {
            FullVersion.features.forEach { feature ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(feature.title, color = if (feature.available) Theme.cream else Theme.label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        if (!feature.available) Text("COMING SOON", style = Theme.titleStyle(9.sp), letterSpacing = 1.sp, color = Theme.accent)
                    }
                    Text(feature.detail, color = Theme.label, fontSize = 14.sp)
                }
            }
            if (Store.fullGameOnSale) {
                Text("One purchase. Yours to keep. No ads, ever.", color = Theme.label, fontSize = 13.sp)
                val product = state.fullGame
                Button(
                    onClick = {
                        if (product == null) return@Button
                        busy = true
                        store.buy(product, activity) { outcome ->
                            busy = false
                            message = when (outcome) {
                                PurchaseOutcome.Success -> "Unlocked. Enjoy the full version!"
                                PurchaseOutcome.Pending -> "Waiting for approval."
                                PurchaseOutcome.Cancelled -> "Not purchased. Tap again to try."
                                PurchaseOutcome.Failed -> "The purchase didn't go through."
                            }
                        }
                    },
                    enabled = product != null && !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Theme.accent, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            product != null -> "Unlock for ${product.price}"
                            state.hasLoaded -> "Unavailable right now"
                            else -> "Loading…"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
                LinkButton("Restore purchases", enabled = !busy) {
                    busy = true
                    message = "Checking your purchases…"
                    store.restore { found ->
                        busy = false
                        message = if (found) "Full version restored." else "No earlier purchase found."
                    }
                }
                LinkButton("Redeem code", enabled = !busy) {
                    if (!store.redeemCode(activity)) message = "The Play Store couldn't be opened."
                }
            } else {
                Text("Coming soon.", color = Theme.label)
            }
        }
        if (message.isNotEmpty()) Text(message, color = Theme.accent)
    }
}

@Composable
private fun LinkButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text,
        color = if (enabled) Theme.accent else Theme.label,
        fontSize = 16.sp,
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun FeedbackSection(fullVersion: Boolean, activity: Activity) {
    var showAddress by remember { mutableStateOf(false) }
    Section("Feedback") {
        LinkButton("✉  Send feedback") {
            if (!Feedback.send(activity, fullVersion)) showAddress = true
        }
        Text("Ideas, bugs, a beat you're proud of? We read everything.", color = Theme.label, fontSize = 13.sp)
    }
    if (showAddress) {
        AlertDialog(
            onDismissRequest = { showAddress = false },
            title = { Text("Email us") },
            text = { Text("Send your feedback to ${Feedback.ADDRESS}. It helps to mention your device and what happened.") },
            confirmButton = {
                TextButton(onClick = {
                    val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Email", Feedback.ADDRESS))
                    showAddress = false
                }) { Text("Copy Address", color = Theme.accent) }
            },
            dismissButton = { TextButton(onClick = { showAddress = false }) { Text("OK", color = Theme.accent) } },
        )
    }
}

@Composable
private fun SettingsSection(prefs: KeyValueStore, onChanged: () -> Unit) {
    var haptics by remember { mutableStateOf(prefs.getBoolean(Prefs.HAPTICS, true)) }
    var keepAwake by remember { mutableStateOf(prefs.getBoolean(Prefs.KEEP_AWAKE, true)) }
    Section("Settings") {
        SettingRow("Haptics", haptics) {
            haptics = it
            prefs.putBoolean(Prefs.HAPTICS, it)
            onChanged()
        }
        HorizontalDivider(color = Theme.moduleEdge)
        SettingRow("Keep the screen on while the beat plays", keepAwake) {
            keepAwake = it
            prefs.putBoolean(Prefs.KEEP_AWAKE, it)
            onChanged()
        }
    }
}

@Composable
private fun SettingRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, color = Theme.cream, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = on,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Theme.accent, checkedThumbColor = Color.White),
        )
    }
}

@Composable
private fun AboutSection(store: StoreModel) {
    Section("About") {
        Row {
            Text("Version", color = Theme.cream, modifier = Modifier.weight(1f))
            Text("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Theme.label)
        }
        Text("A Tuesday Games app. No ads, ever.", color = Theme.label)
        if (BuildConfig.DEBUG) {
            Text(store.state.diagnostics, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Theme.label.copy(alpha = 0.7f))
        }
    }
    Spacer(Modifier.height(16.dp).size(0.dp))
}
