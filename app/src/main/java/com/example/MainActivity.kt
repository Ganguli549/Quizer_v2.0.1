package com.example

import android.os.Bundle
import android.os.Environment
import java.io.File
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.example.data.AppDatabase
import com.example.data.DataParser
import com.example.data.QuizRepository
import com.example.ui.MainViewModel
import com.example.ui.screens.AppNavigation
import com.example.ui.theme.MyApplicationTheme

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            try {
                val f = File(cacheDir, "crash.txt")
                f.writeText(android.util.Log.getStackTraceString(e))
            } catch (ex: Exception) {}
        }
        
        enableEdgeToEdge()
        
        // We handle permissions in Compose now.

        val db = AppDatabase.getDatabase(this)
        val repository = QuizRepository(db)
        val settingsManager = com.example.data.SettingsManager(applicationContext)
        val soundHapticManager = com.example.ui.SoundHapticManager(applicationContext)

        val appContext = applicationContext
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(repository, settingsManager, soundHapticManager) as T
            }
        }
        val viewModelProvider = ViewModelProvider(this, factory)
        viewModel = viewModelProvider.get(MainViewModel::class.java)
        handleIntent(intent)

        // We will call this in onResume to ensure permissions are granted
        lifecycle.addObserver(object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onResume(owner: androidx.lifecycle.LifecycleOwner) {
                // Check permissions before initializing
                val hasPerms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    androidx.core.content.ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                
                if (hasPerms) {
                    viewModel.initializeApp(appContext)
                }
            }
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                if (com.example.data.SecurePrefs.get(appContext).getBoolean("auto_save_enabled", true)) {
                    val hasPerms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        Environment.isExternalStorageManager()
                    } else {
                        androidx.core.content.ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    }
                    if (hasPerms) {
                        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            com.example.data.BackupManager.createBackup(appContext)
                        }
                    }
                }
            }
        })

        setContentSafe()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("OPEN_AI_ASSISTANT", false) == true) {
            val bookId = intent.getStringExtra("BOOK_ID")
            val chatContextId = intent.getStringExtra("CHAT_CONTEXT_ID")
            if (bookId != null) {
                viewModel.selectBook(bookId)
                viewModel.triggerOpenAiAssistant(chatContextId)
            }
        }
    }

    private fun setContentSafe() {
        setContent {
            val appTheme by viewModel.themeState.collectAsState()
            val themeMode by viewModel.themeModeState.collectAsState()
            val isAppReady by viewModel.isAppReady.collectAsState()
            val appIcon by viewModel.appIconState.collectAsState()
            
            MyApplicationTheme(appTheme = appTheme, themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var hasPermissions by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    
                    // Update permission state on resume
                    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                                val storageGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    Environment.isExternalStorageManager()
                                } else {
                                    androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                }
                                val notificationsGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                } else true
                                
                                hasPermissions = storageGranted && notificationsGranted
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    if (!hasPermissions) {
                        com.example.ui.screens.PermissionScreen()
                    } else {
                        androidx.compose.animation.Crossfade(targetState = isAppReady, animationSpec = androidx.compose.animation.core.tween(500), label = "appReadyTransition") { ready ->
                            if (ready) {
                                AppNavigation(viewModel)
                            } else {
                                com.example.ui.screens.SplashScreen(appIcon)
                            }
                        }
                        
                        val showBackupDialog by viewModel.showBackupRestoreDialog.collectAsState()
                        if (showBackupDialog) {
                            com.example.ui.screens.RestoreBackupDialog(
                                onRestore = { viewModel.onRestoreBackupDecision(this@MainActivity, true) },
                                onDiscard = { viewModel.onRestoreBackupDecision(this@MainActivity, false) }
                            )
                        }
                    }
                }
            }
        }
    }}
