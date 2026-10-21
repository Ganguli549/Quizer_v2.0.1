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
        
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val f = File(cacheDir, "crash.txt")
                f.writeText(android.util.Log.getStackTraceString(throwable))
            } catch (ex: Exception) {
                // Ignore write failures
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
        
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        
        // Permissions and other setup continue here.

        
        // We handle permissions in Compose now.

        val repository = QuizRepository(applicationContext)
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
                viewModel.onAppForegrounded(appContext)
                // Check permissions before initializing
                val hasPerms = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    androidx.core.content.ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                
                if (hasPerms) {
                    viewModel.initializeApp(appContext)
                    com.example.data.MediaScannerHelper.hideAllQuizerImagesFromGallery(appContext)
                }
            }
            override fun onPause(owner: androidx.lifecycle.LifecycleOwner) {
                viewModel.onAppMinimized(appContext)
            }
            override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                viewModel.onAppMinimized(appContext)
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
            val isMatteMode by viewModel.isMatteModeState.collectAsState()
            
            MyApplicationTheme(appTheme = appTheme, themeMode = themeMode, isMatteMode = isMatteMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var hasPermissions by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    
                    val view = androidx.compose.ui.platform.LocalView.current
                    androidx.compose.runtime.LaunchedEffect(themeMode) {
                        val window = this@MainActivity.window
                        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                        val isDark = when (themeMode) {
                            "dark" -> true
                            "light" -> false
                            else -> (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
                        }
                        insetsController.isAppearanceLightStatusBars = !isDark
                        insetsController.isAppearanceLightNavigationBars = !isDark
                    }
                    
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
                                
                                // Core offline quiz functionality requires storage access; notifications are optional for background tasks
                                hasPermissions = storageGranted
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
