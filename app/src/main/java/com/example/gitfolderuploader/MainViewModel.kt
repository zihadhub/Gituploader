package com.example.gitfolderuploader

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScanInfo(
    val count: Int,
    val bytes: Long,
    val skipped: List<String>,
    val preview: List<String>,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    // ধাপ ১: অ্যাকাউন্ট
    var token by mutableStateOf("")
    var rememberToken by mutableStateOf(true)
    var account by mutableStateOf<String?>(null)
    var verifying by mutableStateOf(false)
    var tokenError by mutableStateOf<String?>(null)

    // ধাপ ২: রিপো
    var repoInput by mutableStateOf("")
    var branch by mutableStateOf("main")
    var message by mutableStateOf("Upload via Git Folder Uploader")
    var createRepo by mutableStateOf(false)
    var privateRepo by mutableStateOf(true)
    var repos by mutableStateOf<List<RepoInfo>>(emptyList())
    var loadingRepos by mutableStateOf(false)
    var reposError by mutableStateOf<String?>(null)

    // ধাপ ৩: ফোল্ডার
    var folderUri by mutableStateOf<Uri?>(null)
    var folderName by mutableStateOf("")
    var subDir by mutableStateOf("")
    var excludes by mutableStateOf(".git, node_modules, build, .gradle, .idea, .DS_Store")
    var scanning by mutableStateOf(false)
    var scanInfo by mutableStateOf<ScanInfo?>(null)

    // ধাপ ৪: push
    var running by mutableStateOf(false)
    var done by mutableStateOf(0)
    var total by mutableStateOf(0)
    var resultUrl by mutableStateOf<String?>(null)
    val logs = mutableStateListOf<String>()

    private var job: Job? = null

    private val prefs: SharedPreferences =
        app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // টোকেন এনক্রিপ্টেড স্টোরেজে রাখা হয়; না পারলে সেভ করা হয় না
    private val securePrefs: SharedPreferences? = try {
        val key = MasterKey.Builder(app).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            app, "secure", key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        null
    }

    val ready: Boolean
        get() = token.isNotBlank() && parseRepo(repoInput) != null &&
            branch.isNotBlank() && folderUri != null

    init {
        repoInput = prefs.getString("repo", "") ?: ""
        branch = prefs.getString("branch", "main") ?: "main"
        message = prefs.getString("message", message) ?: message
        subDir = prefs.getString("subDir", "") ?: ""
        excludes = prefs.getString("excludes", excludes) ?: excludes
        rememberToken = prefs.getBoolean("rememberToken", true)
        createRepo = prefs.getBoolean("createRepo", false)
        privateRepo = prefs.getBoolean("privateRepo", true)
        token = securePrefs?.getString("token", "") ?: ""

        prefs.getString("folderUri", null)?.let { s ->
            val uri = Uri.parse(s)
            val granted = app.contentResolver.persistedUriPermissions.any { it.uri == uri }
            if (granted) {
                folderUri = uri
                folderName = DocumentFile.fromTreeUri(app, uri)?.name ?: ""
                scanFolder()
            }
        }
        if (token.isNotBlank()) verifyToken()
    }

    fun onTokenChange(s: String) {
        token = s
        account = null
        tokenError = null
        repos = emptyList()
    }

    /** টোকেনের অ্যাকাউন্টের push-যোগ্য রিপোগুলোর তালিকা আনে */
    fun loadRepos(force: Boolean = false) {
        val t = token.trim()
        if (t.isEmpty() || loadingRepos) return
        if (repos.isNotEmpty() && !force) return
        loadingRepos = true
        reposError = null
        viewModelScope.launch {
            try {
                repos = fetchRepos(t)
            } catch (e: Exception) {
                reposError = e.message
            } finally {
                loadingRepos = false
            }
        }
    }

    fun pickRepo(r: RepoInfo) {
        repoInput = r.fullName
        if (r.defaultBranch.isNotBlank()) branch = r.defaultBranch
    }

    fun verifyToken() {
        val t = token.trim()
        if (t.isEmpty()) return
        verifying = true
        tokenError = null
        viewModelScope.launch {
            try {
                val login = fetchLogin(t)
                account = login
                if (repoInput.isBlank()) repoInput = "$login/"
            } catch (e: Exception) {
                account = null
                tokenError = e.message
            } finally {
                verifying = false
            }
        }
    }

    fun onFolderPicked(uri: Uri) {
        val app = getApplication<Application>()
        try {
            app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        folderUri = uri
        folderName = DocumentFile.fromTreeUri(app, uri)?.name ?: ""
        resultUrl = null
        scanFolder()
    }

    private fun parseExcludes(): List<String> =
        excludes.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** push-এর আগে ফোল্ডারে কী কী আছে দেখায় (ls / git status-এর মতো) */
    fun scanFolder() {
        val uri = folderUri ?: return
        val app = getApplication<Application>()
        scanning = true
        viewModelScope.launch {
            try {
                scanInfo = withContext(Dispatchers.IO) {
                    val root = DocumentFile.fromTreeUri(app, uri)
                        ?: throw GitHubException("ফোল্ডার খোলা যায়নি")
                    val r = scanFolderTree(root, subDir.trim().trim('/'), parseExcludes())
                    ScanInfo(
                        count = r.files.size,
                        bytes = r.files.sumOf { it.size },
                        skipped = r.skipped,
                        preview = r.files.take(60).map { it.repoPath },
                    )
                }
            } catch (e: Exception) {
                scanInfo = null
                logs.clear()
                logs += "❌ স্ক্যান ব্যর্থ: ${e.message}"
            } finally {
                scanning = false
            }
        }
    }

    fun parseRepo(s: String): Pair<String, String>? {
        val clean = s.trim()
            .removePrefix("https://").removePrefix("http://")
            .removePrefix("github.com/")
            .removeSuffix("/").removeSuffix(".git")
        val parts = clean.split("/").filter { it.isNotBlank() }
        return if (parts.size >= 2) parts[0] to parts[1] else null
    }

    private fun savePrefs() {
        prefs.edit()
            .putString("repo", repoInput)
            .putString("branch", branch)
            .putString("message", message)
            .putString("subDir", subDir)
            .putString("excludes", excludes)
            .putBoolean("rememberToken", rememberToken)
            .putBoolean("createRepo", createRepo)
            .putBoolean("privateRepo", privateRepo)
            .putString("folderUri", folderUri?.toString())
            .apply()
        securePrefs?.edit()?.putString("token", if (rememberToken) token else "")?.apply()
    }

    fun start() {
        val uri = folderUri ?: return
        val repoPair = parseRepo(repoInput)
        if (token.isBlank() || branch.isBlank() || repoPair == null) {
            logs.clear()
            logs += "⚠️ টোকেন, রিপো (owner/repo) ও ব্রাঞ্চ ঠিকমতো দিন"
            return
        }
        savePrefs()
        running = true
        done = 0
        total = 0
        resultUrl = null
        logs.clear()

        job = viewModelScope.launch {
            try {
                val app = getApplication<Application>()
                val root = DocumentFile.fromTreeUri(app, uri)
                    ?: throw GitHubException("ফোল্ডার খোলা যায়নি, আবার বাছাই করুন")
                val cfg = UploadConfig(
                    token = token.trim(),
                    owner = repoPair.first,
                    repo = repoPair.second,
                    branch = branch.trim(),
                    message = message.ifBlank { "Upload via Git Folder Uploader" },
                    subDir = subDir.trim(),
                    excludes = parseExcludes(),
                    createRepo = createRepo,
                    privateRepo = privateRepo,
                )
                resultUrl = GitHubUploader(
                    resolver = app.contentResolver,
                    cfg = cfg,
                    log = { logs += it },
                    progress = { d, t -> done = d; total = t },
                ).run(root)
            } catch (e: CancellationException) {
                logs += "⛔ বাতিল করা হয়েছে (যে ফাইলগুলো আপলোড হয়েছে সেগুলো কমিট হয়নি)"
                throw e
            } catch (e: Exception) {
                logs += "❌ ${e.message}"
            } finally {
                running = false
            }
        }
    }

    fun cancel() {
        job?.cancel()
    }
}
