package com.quickvolume.app

import android.Manifest
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifs.ensureChannels(this)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { QuickVolumeScreen() }
            }
        }
    }
}

private fun hasNotifPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

@Composable
fun QuickVolumeScreen() {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var guardOn by remember { mutableStateOf(BrightnessGuardService.isEnabled(ctx)) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { tick++ }

    // Dibaca ulang setiap tick berubah.
    val refresh = tick
    val nm = ctx.getSystemService(NotificationManager::class.java)
    val dndGranted = refresh >= 0 && nm.isNotificationPolicyAccessGranted
    val writeGranted = refresh >= 0 && Settings.System.canWrite(ctx)
    val notifGranted = refresh >= 0 && hasNotifPermission(ctx)
    val brightPct = if (refresh >= 0) BrightnessController.currentPercent(ctx) else 0

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Quick Volume Control", fontSize = 26.sp, fontWeight = FontWeight.Bold)

        // Tiga mode
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeButton("🔊 Normal", Color(0xFF2E7D32), Modifier.weight(1f)) {
                message = VolumeController.apply(ctx, VolumeMode.NORMAL).message; tick++
            }
            ModeButton("🤫 Silent", Color(0xFF546E7A), Modifier.weight(1f)) {
                message = VolumeController.apply(ctx, VolumeMode.SILENT).message; tick++
            }
            ModeButton("🌙 Loud", Color(0xFFC62828), Modifier.weight(1f)) {
                message = VolumeController.apply(ctx, VolumeMode.LOUD).message; tick++
            }
        }

        Button(
            onClick = { message = VolumeController.apply(ctx, VolumeMode.MUTE).message; tick++ },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF212121)),
        ) { Text("🔇 Mute Total (tanpa suara & getar)", fontWeight = FontWeight.Bold) }

        message?.let {
            Card(Modifier.fillMaxWidth()) { Text(it, Modifier.padding(14.dp)) }
        }

        // Volume saat ini
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Volume saat ini", fontWeight = FontWeight.SemiBold)
                for ((name, stream) in VolumeController.streams) {
                    val pct = if (refresh >= 0) VolumeController.readPercent(ctx, stream) else 0
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(name)
                        Text("$pct%", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Catatan: Android dapat mengecualikan alarm tertentu, panggilan darurat, " +
                        "suara aksesibilitas, atau suara sistem. Senyap mutlak tidak dijamin di semua perangkat.",
                    fontSize = 12.sp,
                )
            }
        }

        // Kecerahan
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Kecerahan layar: $brightPct%", fontWeight = FontWeight.SemiBold)
                Button(
                    onClick = { message = BrightnessController.setTarget(ctx).message; tick++ },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("☀ Atur ke 50% sekarang") }
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Penjaga kecerahan 50%")
                        Text(
                            "Menjaga kecerahan tetap 50% dan memberi peringatan saat 100%.",
                            fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = guardOn,
                        onCheckedChange = { on ->
                            if (on && !BrightnessController.canWrite(ctx)) {
                                message = "Berikan izin ubah pengaturan sistem dulu (bagian Izin di bawah)."
                            } else {
                                if (on) BrightnessGuardService.start(ctx) else BrightnessGuardService.stop(ctx)
                                guardOn = on
                            }
                        },
                    )
                }
            }
        }

        // Widget
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Widget layar utama", fontWeight = FontWeight.SemiBold)
                Text("Ganti mode suara dan kecerahan 50% langsung dari home screen.", fontSize = 12.sp)
                OutlinedButton(
                    onClick = {
                        val awm = AppWidgetManager.getInstance(ctx)
                        if (Build.VERSION.SDK_INT >= 26 && awm.isRequestPinAppWidgetSupported) {
                            awm.requestPinAppWidget(
                                ComponentName(ctx, VolumeWidgetProvider::class.java), null, null
                            )
                        } else {
                            message = "Tekan lama layar utama, pilih Widget, lalu cari \"Quick Volume\"."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Pasang widget ke layar utama") }
            }
        }

        // Izin
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Izin", fontWeight = FontWeight.SemiBold)
                PermissionRow("Jangan Ganggu (untuk Silent)", dndGranted) {
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
                PermissionRow("Ubah pengaturan sistem (kecerahan)", writeGranted) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                    )
                }
                PermissionRow("Notifikasi (peringatan 100%)", notifGranted) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeButton(label: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
    ) { Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(if (granted) "✅ Diizinkan" else "⚠ Belum diizinkan", fontSize = 12.sp)
        }
        if (!granted) OutlinedButton(onClick = onGrant) { Text("Izinkan") }
    }
}
