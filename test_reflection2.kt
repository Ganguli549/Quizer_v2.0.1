import androidx.compose.material3.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.valueParameters

fun main() {
    val clazz = Class.forName("androidx.compose.material3.ModalBottomSheet_androidKt")
    for (method in clazz.declaredMethods) {
        if (method.name == "ModalBottomSheet") {
            println("ModalBottomSheet params:")
            for (param in method.parameters) {
                println("  - ${param.name}: ${param.type}")
            }
        }
    }
    
    val stateClazz = Class.forName("androidx.compose.material3.SheetStateKt")
    for (method in stateClazz.declaredMethods) {
        if (method.name.contains("rememberModalBottomSheetState")) {
            println("rememberModalBottomSheetState params:")
            for (param in method.parameters) {
                println("  - ${param.name}: ${param.type}")
            }
        }
    }
}
