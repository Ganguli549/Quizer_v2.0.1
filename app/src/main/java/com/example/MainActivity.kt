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
        val viewModel = viewModelProvider.get(MainViewModel::class.java)

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
        })

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
                                hasPermissions = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    Environment.isExternalStorageManager()
                                } else {
                                    androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                }
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose {
                            lifecycleOwner.lifecycle.removeObserver(observer)
                        }
                    }

                    if (!hasPermissions) {
                        com.example.ui.screens.PermissionScreen(
                            onRequestPermission = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                    try {
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                        intent.addCategory("android.intent.category.DEFAULT")
                                        intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                                        startActivity(intent)
                                    } catch (e: Exception) {
                                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        startActivity(intent)
                                    }
                                } else {
                                    androidx.core.app.ActivityCompat.requestPermissions(
                                        this@MainActivity,
                                        arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE, android.Manifest.permission.READ_EXTERNAL_STORAGE),
                                        100
                                    )
                                }
                            }
                        )
                    } else {
                        androidx.compose.animation.Crossfade(targetState = isAppReady, animationSpec = androidx.compose.animation.core.tween(500), label = "appReadyTransition") { ready ->
                            if (ready) {
                                AppNavigation(viewModel)
                            } else {
                                com.example.ui.screens.SplashScreen(appIcon)
                            }
                        }
                    }
                }
            }
        }
    }
}


