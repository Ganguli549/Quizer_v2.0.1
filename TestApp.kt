import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.WindowInsets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Test() {
    ModalBottomSheet(
        onDismissRequest = {},
        contentWindowInsets = { WindowInsets(0) }
    ) {
    }
}
