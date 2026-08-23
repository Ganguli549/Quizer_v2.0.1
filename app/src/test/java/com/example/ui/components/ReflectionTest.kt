package com.example.ui.components

import org.junit.Test
import java.lang.reflect.Modifier

class ReflectionTest {
    @Test
    fun checkParams() {
        val clazz = Class.forName("androidx.compose.material3.ModalBottomSheet_androidKt")
        for (method in clazz.declaredMethods) {
            if (method.name.contains("ModalBottomSheet")) {
                println("ModalBottomSheet params:")
                for (param in method.parameters) {
                    println("  - ${param.name}: ${param.type}")
                }
            }
        }
    }
}
