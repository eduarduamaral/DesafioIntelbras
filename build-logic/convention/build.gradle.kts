import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "br.com.eduardoamaral.desafiointelbras.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // compileOnly: as classes vêm do build principal em tempo de execução (root build.gradle.kts).
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.desafio.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = libs.plugins.desafio.android.compose.get().pluginId
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("hilt") {
            id = libs.plugins.desafio.hilt.get().pluginId
            implementationClass = "HiltConventionPlugin"
        }
        register("detekt") {
            id = libs.plugins.desafio.detekt.get().pluginId
            implementationClass = "DetektConventionPlugin"
        }
    }
}
