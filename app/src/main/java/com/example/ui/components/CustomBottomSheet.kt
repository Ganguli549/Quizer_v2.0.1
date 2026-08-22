package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomModalBottomSheet(
    maxHeightFraction: Float = 0.8f,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        // Apply only IME (keyboard) insets.
        // The individual screens already have internal bottom padding (~16dp) which is enough 
        // to dodge the navigation pill. This prevents the "double padding" massive gap.
        contentWindowInsets = { WindowInsets.ime },
        modifier = Modifier.fillMaxWidth()
    ) {
        // By constraining the height on the inner Column instead of the ModalBottomSheet itself,
        // we ensure that Jetpack Compose's nested scrolling behavior remains completely intact.
        // This prevents the sheet from accidentally dismissing when the user scrolls a list.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeight * maxHeightFraction)
        ) {
            content()
        }
    }
}
