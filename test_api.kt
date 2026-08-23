import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Test() {
    ModalBottomSheet(
        onDismissRequest = {},
        // Let's see if this compiles
        // sheetGesturesEnabled = false 
    )
}
