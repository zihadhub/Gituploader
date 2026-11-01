package com.example.gitfolderuploader

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                Screen()
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    enabled: Boolean,
    password: Boolean = false,
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        supportingText = if (hint != null) {
            { Text(hint) }
        } else null,
    )
}

@Composable
private fun Screen(vm: MainViewModel = viewModel()) {
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.onFolderPicked(uri)
    }

    // আপলোড চলার সময় স্ক্রিন জাগিয়ে রাখা
    DisposableEffect(vm.running) {
        val window = (ctx as? Activity)?.window
        if (vm.running) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Git Folder Uploader", style = MaterialTheme.typography.headlineSmall)
            Text(
                "পুরো ফোল্ডার, হুবহু সাজানো কাঠামোসহ, এক কমিটে GitHub-এ।",
                style = MaterialTheme.typography.bodyMedium,
            )

            Field(
                "Personal Access Token", vm.token, { vm.token = it }, !vm.running, password = true,
                hint = "Fine-grained: Contents → Read and write  |  Classic: repo",
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = vm.rememberToken, onCheckedChange = { vm.rememberToken = it }, enabled = !vm.running)
                Text("টোকেন এনক্রিপ্ট করে মনে রাখো")
            }
            Field(
                "রিপো (owner/repo বা লিংক)", vm.repoInput, { vm.repoInput = it }, !vm.running,
                hint = "যেমন: eizus/my-project",
            )
            Field("ব্রাঞ্চ", vm.branch, { vm.branch = it }, !vm.running)
            Field("কমিট মেসেজ", vm.message, { vm.message = it }, !vm.running)
            Field(
                "রিপোর ভেতরের সাবফোল্ডার (ঐচ্ছিক)", vm.subDir, { vm.subDir = it }, !vm.running,
                hint = "ফাঁকা রাখলে রিপোর রুটে যাবে",
            )
            Field(
                "বাদ দেওয়া নাম (কমা দিয়ে)", vm.excludes, { vm.excludes = it }, !vm.running,
                hint = "*.apk এর মতো এক্সটেনশনও দেওয়া যায়",
            )

            OutlinedButton(
                onClick = { picker.launch(null) },
                enabled = !vm.running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (vm.folderName.isEmpty()) "📁 ফোল্ডার বাছাই করুন" else "📁 ${vm.folderName}  (বদলান)")
            }
            Text(
                "মনে রাখবেন: Download বা স্টোরেজের মূল ফোল্ডার বাছাই করা যায় না। তার ভেতরের প্রজেক্ট ফোল্ডারটি বাছাই করুন।",
                style = MaterialTheme.typography.bodySmall,
            )

            if (vm.running) {
                OutlinedButton(onClick = vm::cancel, modifier = Modifier.fillMaxWidth()) {
                    Text("⛔ বাতিল")
                }
            } else {
                Button(
                    onClick = vm::start,
                    enabled = vm.folderUri != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("⬆️ আপলোড শুরু করুন")
                }
            }

            if (vm.running || vm.total > 0) {
                LinearProgressIndicator(
                    progress = { if (vm.total == 0) 0f else vm.done.toFloat() / vm.total },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("${vm.done} / ${vm.total} ফাইল", style = MaterialTheme.typography.bodySmall)
            }

            vm.resultUrl?.let { url ->
                Button(onClick = { uriHandler.openUri(url) }, modifier = Modifier.fillMaxWidth()) {
                    Text("🔗 GitHub-এ দেখুন")
                }
            }

            if (vm.logs.isNotEmpty()) {
                Surface(
                    tonalElevation = 2.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        vm.logs.takeLast(150).forEach {
                            Text(it, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
