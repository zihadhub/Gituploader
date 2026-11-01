package com.example.gitfolderuploader

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme { Screen() }
        }
    }
}

// ───────────────────────── ছোট ছোট UI অংশ ─────────────────────────

@Composable
private fun Pill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Text(
            text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun StepCard(
    number: Int,
    title: String,
    done: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        border = BorderStroke(1.dp, cs.outline),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(26.dp)
                        .background(if (done) cs.primary else cs.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (done) "✓" else "$number",
                        color = if (done) cs.onPrimary else cs.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean = true,
    password: Boolean = false,
    visible: Boolean = true,
    hint: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = trailing,
        supportingText = if (hint != null) {
            { Text(hint) }
        } else null,
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun RowScope.Stat(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.weight(1f),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Terminal(prompt: String, lines: List<String>) {
    Surface(color = TermBg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(prompt, color = TermDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            lines.takeLast(150).forEach { l ->
                val c = when {
                    l.startsWith("❌") -> TermErr
                    l.startsWith("⚠") || l.startsWith("⏳") || l.startsWith("🔁") -> TermWarn
                    l.startsWith("🎉") || l.startsWith("✅") -> TermOk
                    else -> TermFg
                }
                Text(l, color = c, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
    }
}

// ───────────────────────── মূল স্ক্রিন ─────────────────────────

@Composable
private fun Screen(vm: MainViewModel = viewModel()) {
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val cs = MaterialTheme.colorScheme

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.onFolderPicked(uri)
    }

    // push চলার সময় স্ক্রিন জাগিয়ে রাখা
    DisposableEffect(vm.running) {
        val window = (ctx as? Activity)?.window
        if (vm.running) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = cs.background) {
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // হেডার
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Git Folder Uploader", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "ফোল্ডার বাছাই  →  টোকেন  →  Push",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
                val acc = vm.account
                if (acc != null) Pill("● @$acc", cs.primary) else Pill("সংযুক্ত নয়", cs.onSurfaceVariant)
            }

            // ধাপ ১ — অ্যাকাউন্ট
            StepCard(1, "অ্যাকাউন্ট (Access Token)", vm.account != null) {
                var visible by remember { mutableStateOf(false) }
                Field(
                    "Personal Access Token", vm.token, vm::onTokenChange,
                    enabled = !vm.running, password = true, visible = visible,
                    trailing = {
                        TextButton(onClick = { visible = !visible }) { Text(if (visible) "লুকান" else "দেখুন") }
                    },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = vm::verifyToken,
                        enabled = vm.token.isNotBlank() && !vm.verifying && !vm.running,
                    ) { Text("যাচাই করুন") }
                    when {
                        vm.verifying -> Text("যাচাই হচ্ছে…", style = MaterialTheme.typography.bodySmall)
                        vm.account != null -> Text(
                            "✓ @${vm.account} হিসেবে সংযুক্ত",
                            color = cs.primary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        vm.tokenError != null -> Text(
                            vm.tokenError ?: "",
                            color = cs.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = vm.rememberToken, onCheckedChange = { vm.rememberToken = it }, enabled = !vm.running)
                    Text("টোকেন এনক্রিপ্ট করে মনে রাখো", style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "Classic: repo স্কোপ   |   Fine-grained: Contents → Read and write",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }

            // ধাপ ২ — রিপোজিটরি
            StepCard(2, "রিপোজিটরি", vm.parseRepo(vm.repoInput) != null && vm.branch.isNotBlank()) {
                Field(
                    "রিপো (owner/repo বা লিংক)", vm.repoInput, { vm.repoInput = it },
                    enabled = !vm.running, hint = "যেমন: eizus/my-project",
                )
                Field("ব্রাঞ্চ", vm.branch, { vm.branch = it }, enabled = !vm.running)
                Field("কমিট মেসেজ", vm.message, { vm.message = it }, enabled = !vm.running)
                SwitchRow(
                    "রিপো না থাকলে বানাও",
                    "টোকেনে রিপো তৈরির অনুমতি লাগবে (Classic: repo)",
                    vm.createRepo, !vm.running,
                ) { vm.createRepo = it }
                if (vm.createRepo) {
                    SwitchRow("প্রাইভেট রিপো", null, vm.privateRepo, !vm.running) { vm.privateRepo = it }
                }
            }

            // ধাপ ৩ — ফোল্ডার
            StepCard(3, "ফোল্ডার", vm.folderUri != null) {
                var showPreview by remember { mutableStateOf(false) }
                var advanced by remember { mutableStateOf(false) }

                FilledTonalButton(
                    onClick = { picker.launch(null) },
                    enabled = !vm.running,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(if (vm.folderName.isEmpty()) "📁  ফোল্ডার বাছাই করুন" else "📁  ${vm.folderName}  ·  বদলান")
                }

                if (vm.scanning) {
                    Text("স্ক্যান হচ্ছে…", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                vm.scanInfo?.let { info ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Stat("ফাইল", "${info.count}")
                        Stat("মোট সাইজ", humanSize(info.bytes))
                        Stat("বাদ (বড়)", "${info.skipped.size}")
                    }
                    TextButton(onClick = { showPreview = !showPreview }) {
                        Text(if (showPreview) "▾ ফাইলের তালিকা লুকান" else "▸ ফাইলের তালিকা দেখুন")
                    }
                    if (showPreview) {
                        Surface(color = TermBg, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                info.preview.forEach {
                                    Text("+ $it", color = TermOk, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                }
                                if (info.count > info.preview.size) {
                                    Text(
                                        "… আরও ${info.count - info.preview.size} টি ফাইল",
                                        color = TermDim, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                                    )
                                }
                                info.skipped.forEach {
                                    Text("! $it", color = TermWarn, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                TextButton(onClick = { advanced = !advanced }) {
                    Text(if (advanced) "▾ অ্যাডভান্সড" else "▸ অ্যাডভান্সড")
                }
                if (advanced) {
                    Field(
                        "রিপোর ভেতরের সাবফোল্ডার (ঐচ্ছিক)", vm.subDir, { vm.subDir = it },
                        enabled = !vm.running, hint = "ফাঁকা রাখলে রিপোর রুটে যাবে",
                    )
                    Field(
                        "বাদ দেওয়া নাম (কমা দিয়ে)", vm.excludes, { vm.excludes = it },
                        enabled = !vm.running, hint = "*.apk এর মতো এক্সটেনশনও দেওয়া যায়",
                    )
                    OutlinedButton(
                        onClick = vm::scanFolder,
                        enabled = vm.folderUri != null && !vm.scanning && !vm.running,
                    ) { Text("আবার স্ক্যান") }
                }

                Text(
                    "Download বা স্টোরেজের মূল ফোল্ডার বাছাই করা যায় না — তার ভেতরের প্রজেক্ট ফোল্ডারটি বাছাই করুন।",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }

            // ধাপ ৪ — Push
            StepCard(4, "Push", vm.resultUrl != null) {
                if (vm.running) {
                    OutlinedButton(
                        onClick = vm::cancel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) { Text("⛔  বাতিল") }
                } else {
                    Button(
                        onClick = vm::start,
                        enabled = vm.ready,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) { Text("⬆  Push to GitHub", fontWeight = FontWeight.SemiBold) }
                    if (!vm.ready) {
                        Text(
                            "টোকেন, রিপো ও ফোল্ডার দিলে বাটন চালু হবে",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }

                if (vm.running || vm.total > 0) {
                    val pct = if (vm.total == 0) 0 else vm.done * 100 / vm.total
                    LinearProgressIndicator(
                        progress = { if (vm.total == 0) 0f else vm.done.toFloat() / vm.total },
                        modifier = Modifier.fillMaxWidth(),
                        color = cs.primary,
                        trackColor = cs.surfaceVariant,
                    )
                    Text(
                        "${vm.done} / ${vm.total} ফাইল  ·  $pct%",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }

                if (vm.logs.isNotEmpty()) {
                    Terminal("$ git push origin ${vm.branch}", vm.logs)
                }

                vm.resultUrl?.let { url ->
                    FilledTonalButton(
                        onClick = { uriHandler.openUri(url) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("🔗  GitHub-এ দেখুন") }
                }
            }
        }
    }
}
