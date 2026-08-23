import androidx.compose.material3.*
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.valueParameters

fun main() {
    val clazz = Class.forName("androidx.compose.material3.ModalBottomSheetKt")
    for (method in clazz.declaredMethods) {
        if (method.name.contains("ModalBottomSheet")) {
            println(method.name)
            for (param in method.parameters) {
                println("  - ${param.name}: ${param.type}")
            }
        }
    }
}
