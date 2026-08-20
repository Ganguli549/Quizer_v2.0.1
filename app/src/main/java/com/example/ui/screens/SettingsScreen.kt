package com.example.ui.screens
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.ui.graphics.asImageBitmap


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.example.ui.MainViewModel
import kotlinx.coroutines.launch

import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment

fun changeAppIcon(context: android.content.Context, icon: String) {
    val pm = context.packageManager
    val packageName = context.packageName

    val aliases = listOf(
        "OceanIcon",
        "ForestIcon",
        "SunsetIcon",
        "LavenderIcon",
        "DarkIcon",
        "GoldIcon",
        "TitaniumIcon"
    )

    val targetAlias = when (icon) {
        "forest" -> "ForestIcon"
        "sunset" -> "SunsetIcon"
        "lavender" -> "LavenderIcon"
        "dark" -> "DarkIcon"
        "gold" -> "GoldIcon"
        "titanium" -> "TitaniumIcon"
        else -> "OceanIcon" // default uses OceanIcon
    }

    val targetComponent = ComponentName(packageName, "com.example.$targetAlias")
    
    // Disable others
    aliases.filter { it != targetAlias }.forEach { alias ->
        val component = ComponentName(packageName, "com.example.$alias")
        pm.setComponentEnabledSetting(
            component,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    // Enable target alias last, WITH DONT_KILL_APP to prevent instant app crash/freeze
    pm.setComponentEnabledSetting(
        targetComponent,
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
        PackageManager.DONT_KILL_APP
    )
}

@OptIn(ExperimentalMaterial3Api::class, coil.annotation.ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit, onNavigateToAiSettings: () -> Unit) {
    var showResetDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        com.example.ui.components.ResponsiveContainer(modifier = Modifier.padding(padding)) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column {
                Text("AI Features", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onNavigateToAiSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("AI Assistant Settings")
                }
                Spacer(modifier = Modifier.height(24.dp))
                
                Text("Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Appearance:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                val currentThemeMode by viewModel.themeModeState.collectAsState()
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    FilterChip(
                        selected = currentThemeMode == "system",
                        onClick = { viewModel.playToggle(); viewModel.updateThemeMode("system") },
                        label = { Text("System") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = currentThemeMode == "light",
                        onClick = { viewModel.playToggle(); viewModel.updateThemeMode("light") },
                        label = { Text("Light") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = currentThemeMode == "dark",
                        onClick = { viewModel.playToggle(); viewModel.updateThemeMode("dark") },
                        label = { Text("Dark") }
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Text("Color Scheme:", style = MaterialTheme.typography.labelLarge)
                Spacer(modifier = Modifier.height(4.dp))
                
                val currentTheme by viewModel.themeState.collectAsState()
                @Composable
                fun ThemeChip(themeStr: String, label: String) {
                    FilterChip(
                        selected = currentTheme == themeStr,
                        onClick = { 
                            viewModel.playButtonPress()
                            viewModel.updateTheme(themeStr)
                        },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeChip("dynamic", "Default")
                    ThemeChip("ocean", "Ocean")
                    ThemeChip("forest", "Forest")
                    ThemeChip("sunset", "Sunset")
                    ThemeChip("lavender", "Lavender")
                    ThemeChip("gold", "Gold")
                    ThemeChip("colorful", "Colorful")
                    ThemeChip("amoled", "Dark")
                }
            }

            HorizontalDivider()

            Column {
                Text("Data & Backups", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                
                var autoSaveEnabled by remember { mutableStateOf(com.example.data.SecurePrefs.get(context).getBoolean("auto_save_enabled", true)) }
                
                Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                autoSaveEnabled = !autoSaveEnabled
                                com.example.data.SecurePrefs.get(context).edit().putBoolean("auto_save_enabled", autoSaveEnabled).apply()
                            },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (autoSaveEnabled) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                tint = if (autoSaveEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto Save Data", style = MaterialTheme.typography.titleSmall)
                                Text("Silently backs up to 'Quizer/Backups'", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = autoSaveEnabled,
                                onCheckedChange = {
                                    autoSaveEnabled = it
                                    com.example.data.SecurePrefs.get(context).edit().putBoolean("auto_save_enabled", it).apply()
                                }
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (com.example.data.BackupManager.createBackup(context)) {
                                        if (viewModel.hapticFeedbackEnabledState.value) viewModel.soundHapticManager.hapticSuccess()
                                        android.widget.Toast.makeText(context, "Data exported successfully", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        if (viewModel.hapticFeedbackEnabledState.value) viewModel.soundHapticManager.hapticWarning()
                                        android.widget.Toast.makeText(context, "Export failed", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export")
                            }
                            Button(
                                onClick = {
                                    if (com.example.data.BackupManager.restoreBackup(context)) {
                                        if (viewModel.hapticFeedbackEnabledState.value) viewModel.soundHapticManager.hapticSuccess()
                                        android.widget.Toast.makeText(context, "Data restored! Restarting app...", android.widget.Toast.LENGTH_SHORT).show()
                                        coroutineScope.launch {
                                            kotlinx.coroutines.delay(1500)
                                            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                            context.startActivity(intent)
                                            Runtime.getRuntime().exit(0)
                                        }
                                    } else {
                                        if (viewModel.hapticFeedbackEnabledState.value) viewModel.soundHapticManager.hapticWarning()
                                        android.widget.Toast.makeText(context, "Restore failed or no backup found", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                                contentPadding = PaddingValues(vertical = 12.dp)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Restore")
                            }
                        }
                }
            }

            HorizontalDivider()
            
            Column {
                Text("App Icon", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                val currentAppIcon by viewModel.appIconState.collectAsState()
                @Composable
                fun AppIconChip(iconStr: String, label: String, imageResId: Int) {
                    FilterChip(
                        selected = currentAppIcon == iconStr,
                        onClick = { 
                            if (currentAppIcon != iconStr) {
                                try {
                                    viewModel.playButtonPress()
                                    viewModel.updateAppIcon(iconStr)
                                    changeAppIcon(context, iconStr)
                                    if (viewModel.hapticFeedbackEnabledState.value) viewModel.soundHapticManager.hapticSuccess(); android.widget.Toast.makeText(context, "App icon updated. It may take a few moments to reflect on your home screen.", android.widget.Toast.LENGTH_LONG).show()
                                } catch (e: Exception) {
                                    if (viewModel.hapticFeedbackEnabledState.value) viewModel.soundHapticManager.hapticWarning(); android.widget.Toast.makeText(context, "Failed to change icon: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        label = { Text(label) },
                        leadingIcon = {
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            val bitmap = remember(imageResId) {
                                val drawable = androidx.core.content.ContextCompat.getDrawable(ctx, imageResId)
                                if (drawable != null) {
                                    val bmp = android.graphics.Bitmap.createBitmap(
                                        100, 100, android.graphics.Bitmap.Config.ARGB_8888
                                    )
                                    val canvas = android.graphics.Canvas(bmp)
                                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                                    drawable.draw(canvas)
                                    bmp.asImageBitmap()
                                } else {
                                    null
                                }
                            }
                            if (bitmap != null) {
                                androidx.compose.foundation.Image(
                                    bitmap = bitmap,
                                    contentDescription = "$label Icon",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    AppIconChip("ocean", "Ocean", com.example.R.drawable.ocean)
                    AppIconChip("forest", "Forest", com.example.R.drawable.forest)
                    AppIconChip("sunset", "Sunset", com.example.R.drawable.sunset)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    AppIconChip("lavender", "Lavender", com.example.R.drawable.lavender)
                    AppIconChip("gold", "Gold", com.example.R.drawable.gold)
                    AppIconChip("dark", "Dark", com.example.R.drawable.dark)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                    AppIconChip("titanium", "Titanium", com.example.R.drawable.titanium)
                }
            }

            HorizontalDivider()

            Column {
                Text("Experience", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                val timerSoundEnabled by viewModel.timerSoundEnabledState.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Timer Sounds", style = MaterialTheme.typography.bodyLarge)
                        Text("Low time and time-out alerts", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = timerSoundEnabled,
                        onCheckedChange = { viewModel.playToggle(); viewModel.updateTimerSound(it) }
                    )
                }

                val clickSoundEnabled by viewModel.clickSoundEnabledState.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Click Sounds", style = MaterialTheme.typography.bodyLarge)
                        Text("Button tap and interaction sounds", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = clickSoundEnabled,
                        onCheckedChange = { viewModel.playToggle(); viewModel.updateClickSound(it) }
                    )
                }

                val hapticEnabled by viewModel.hapticFeedbackEnabledState.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Haptic Feedback", style = MaterialTheme.typography.bodyLarge)
                        Text("Premium vibration effects", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = hapticEnabled,
                        onCheckedChange = { viewModel.playToggle(); viewModel.updateHapticFeedback(it) }
                    )
                }
            }

            HorizontalDivider()

            Column {
                Text("Danger Zone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
                    Button(
                        onClick = {
                            coil.Coil.imageLoader(context).memoryCache?.clear()
                            coil.Coil.imageLoader(context).diskCache?.clear()
                            if (viewModel.hapticFeedbackEnabledState.value) viewModel.soundHapticManager.hapticSuccess(); android.widget.Toast.makeText(context, "Cache Cleared", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Cache")
                    }
                    Button(
                    onClick = { showResetDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset App")
                }
                }
                Text("This will clear all cache, book selection, and reset all settings to their fresh install condition.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            
            HorizontalDivider()
            
            var showAppGuide by remember { mutableStateOf(false) }
            var showBookDataGuide by remember { mutableStateOf(false) }

            Column {
                Text("About", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    FilledTonalButton(onClick = { showAppGuide = true }, modifier = Modifier.weight(1f)) {
                        Text("App Guide")
                    }
                    FilledTonalButton(onClick = { showBookDataGuide = true }, modifier = Modifier.weight(1f)) {
                        Text("Book Data Guide")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "© 2026 Saurabh Prajapat. All rights reserved.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                val context = androidx.compose.ui.platform.LocalContext.current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { 
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://t.me/+GpXtyE2KkvI3MWQ1"))
                        context.startActivity(intent)
                    }
                ) {
                    Text(
                        text = "Join: ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Telegram",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Telegram Group",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (showAppGuide) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showAppGuide = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Column {
                            TopAppBar(
                                title = { Text("App Guide") },
                                navigationIcon = {
                                    IconButton(onClick = { showAppGuide = false }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close")
                                    }
                                }
                            )
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    Text(com.example.data.AppGuides.guideBookContent)
                                }
                            }
                        }
                    }
                }
            }

            if (showBookDataGuide) {
                androidx.compose.ui.window.Dialog(
                    onDismissRequest = { showBookDataGuide = false },
                    properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        Column {
                            TopAppBar(
                                title = { Text("Book Data Guide") },
                                navigationIcon = {
                                    IconButton(onClick = { showBookDataGuide = false }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close")
                                    }
                                }
                            )
                            Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                                androidx.compose.foundation.text.selection.SelectionContainer {
                                    Text(com.example.data.AppGuides.bookDataGuideContent)
                                }
                            }
                        }
                    }
                }
            }
        }
    
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Application?") },
            text = { Text("Are you sure you want to completely reset the app? All data, settings, and progress will be lost. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        coroutineScope.launch {
                            viewModel.resetApp(context)
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(viewModel: MainViewModel, onBack: () -> Unit, onNavigateToUsage: () -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var geminiApiKeys by remember { mutableStateOf("") }
    var imageGenApiUrl by remember { mutableStateOf("") }
    var imageGenApiKey by remember { mutableStateOf("") }
    var imageGenModel by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("gemini-1.5-flash") }
    var expandedModelDropdown by remember { mutableStateOf(false) }
    
    var models by remember { mutableStateOf(listOf(
        "gemini-1.5-flash",
        "gemini-1.5-pro",
        "gemini-1.5-flash-8b"
    )) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var imageModels by remember { mutableStateOf(listOf("flux", "turbo", "sana")) }
    var expandedImageModelDropdown by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        val prefs = com.example.data.SecurePrefs.get(context)
        geminiApiKeys = prefs.getString("gemini_api_keys", prefs.getString("gemini_api_key", "")) ?: ""
        imageGenApiUrl = prefs.getString("image_gen_api_url", "") ?: ""
        imageGenApiKey = prefs.getString("image_gen_api_key", "") ?: ""
        imageGenModel = prefs.getString("image_gen_model", "flux") ?: "flux" 
        selectedModel = prefs.getString("gemini_model", "gemini-1.5-flash") ?: "gemini-1.5-flash"
    }

    val coroutineScope = rememberCoroutineScope()
    val saveAndFetch = {
        com.example.data.SecurePrefs.get(context).edit()
            .putString("gemini_api_keys", geminiApiKeys)
            .putString("image_gen_api_url", imageGenApiUrl)
            .putString("image_gen_api_key", imageGenApiKey)
            .putString("image_gen_model", imageGenModel)
            .apply()
        
        coroutineScope.launch {
            isLoadingModels = true
            val fetchedModels = com.example.data.GeminiHelper.getAvailableModels(context)
            if (fetchedModels.isNotEmpty()) {
                models = fetchedModels
                if (selectedModel !in models) {
                    selectedModel = models.firstOrNull() ?: "gemini-1.5-flash"
                    com.example.data.SecurePrefs.get(context)
                        .edit().putString("gemini_model", selectedModel).apply()
                }
            }
            val fetchedImageModels = com.example.data.GeminiHelper.getAvailableImageModels(context)
            if (fetchedImageModels.isNotEmpty()) {
                imageModels = fetchedImageModels
                if (imageGenModel !in imageModels) {
                    imageGenModel = imageModels.firstOrNull() ?: "flux"
                    com.example.data.SecurePrefs.get(context)
                        .edit().putString("image_gen_model", imageGenModel).apply()
                }
            }
            isLoadingModels = false
        }
    }

    LaunchedEffect(Unit) {
        saveAndFetch()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        com.example.ui.components.ResponsiveContainer(modifier = Modifier.padding(padding)) {
        Column(
            modifier = Modifier
                
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("API Keys Configuration", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            
            OutlinedButton(onClick = onNavigateToUsage, modifier = Modifier.fillMaxWidth()) {
                Text("View API Usage")
            }

            
            OutlinedTextField(
                value = geminiApiKeys,
                onValueChange = { geminiApiKeys = it },
                label = { Text("Gemini API Keys (Comma Separated)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { saveAndFetch() })
            )
            
            Button(
                onClick = { saveAndFetch() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Keys & Refresh Models")
            }
            Text(
                "Add multiple keys separated by commas. If one reaches its limit or fails, the app will automatically try the next one. Leave blank to use built-in key.", 
                style = MaterialTheme.typography.bodySmall, 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            OutlinedButton(
                onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                    intent.data = android.net.Uri.parse("https://aistudio.google.com/app/apikey")
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Get Free Gemini API Key")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Image Generation Model Configuration", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            
            ExposedDropdownMenuBox(
                expanded = expandedImageModelDropdown,
                onExpandedChange = { expandedImageModelDropdown = it }
            ) {
                OutlinedTextField(
                    value = imageGenModel,
                    onValueChange = { 
                        imageGenModel = it
                        com.example.data.SecurePrefs.get(context)
                            .edit().putString("image_gen_model", it).apply()
                    },
                    label = { Text("Image Model (e.g. flux, dall-e-3)") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryEditable, true),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedImageModelDropdown) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                ExposedDropdownMenu(
                    expanded = expandedImageModelDropdown,
                    onDismissRequest = { expandedImageModelDropdown = false },
                ) {
                    imageModels.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                imageGenModel = selectionOption
                                expandedImageModelDropdown = false
                                com.example.data.SecurePrefs.get(context)
                                    .edit().putString("image_gen_model", selectionOption).apply()
                            }
                        )
                    }
                }
            }
            
            OutlinedTextField(
                value = imageGenApiUrl,
                onValueChange = { 
                    imageGenApiUrl = it
                    com.example.data.SecurePrefs.get(context)
                        .edit().putString("image_gen_api_url", it).apply()
                },
                label = { Text("API URL (Optional, leave blank for Pollinations)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            OutlinedTextField(
                value = imageGenApiKey,
                onValueChange = { 
                    imageGenApiKey = it
                    com.example.data.SecurePrefs.get(context)
                        .edit().putString("image_gen_api_key", it).apply()
                },
                label = { Text("API Key (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Text(
                "If API URL and Key are left blank, the app will use Pollinations AI for free image generation based on the specified model (e.g., flux, turbo).", 
                style = MaterialTheme.typography.bodySmall, 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Text("Gemini Model Selection", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            
            if (isLoadingModels) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            
            ExposedDropdownMenuBox(
                expanded = expandedModelDropdown,
                onExpandedChange = { expandedModelDropdown = it },
            ) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryEditable, true).fillMaxWidth(),
                    readOnly = true,
                    value = selectedModel,
                    onValueChange = {},
                    label = { Text("AI Model") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedModelDropdown) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = expandedModelDropdown,
                    onDismissRequest = { expandedModelDropdown = false },
                ) {
                    models.forEach { selectionOption ->
                        DropdownMenuItem(
                            text = { Text(selectionOption) },
                            onClick = {
                                selectedModel = selectionOption
                                expandedModelDropdown = false
                                com.example.data.SecurePrefs.get(context)
                                    .edit().putString("gemini_model", selectionOption).apply()
                            }
                        )
                    }
                }
            }
            Text(
                "gemini-1.5-flash is currently the standard fast model. For complex reasoning, use gemini-1.5-pro.", 
                style = MaterialTheme.typography.bodySmall, 
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    
        }
    }
}
