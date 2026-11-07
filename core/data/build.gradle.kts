// Implementação do domínio em Kotlin Multiplatform: Room, Ktor e a fila de pendências,
// montados pelo DataContainer. Código comum em src/commonMain; o que é de cada plataforma
// em src/androidMain (Room com Context, OkHttp, ConnectivityManager) e src/iosMain
// (Room com SQLite embutido, Darwin, NWPathMonitor).
// Sem Hilt nem WorkManager: esses são do :app. As features não dependem deste módulo.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.desafio.detekt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    android {
        namespace = "br.com.eduardoamaral.desafiointelbras.core.data"
        // Os mesmos valores dos outros módulos (build-logic, objeto Sdk).
        compileSdk = 36
        minSdk = 29

        // Roda os testes de src/commonTest na JVM, sem aparelho (testAndroidHostTest).
        withHostTest {}
        // Testes no aparelho, com SQLite real (DAO e migrações): src/androidDeviceTest.
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        // Desligado por padrão num módulo KMP; sem ele o APK de teste não tem assets, e o
        // AppDatabaseMigrationTest precisa dos esquemas lá (ver androidComponents abaixo).
        androidResources {
            enable = true
        }
    }

    // Aparelho e simulador em Mac Apple Silicon. Os alvos iOS só compilam no Mac.
    iosArm64()
    iosSimulatorArm64()

    compilerOptions {
        // O Room usa um "expect object" (AppDatabaseConstructor) para criar o banco no iOS;
        // o Kotlin ainda chama esse recurso de Beta e avisaria a cada compilação.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":core:domain"))

            implementation(libs.room.runtime)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
            // No iOS não há SQLite do sistema exposto ao Room: o driver traz o próprio.
            implementation(libs.androidx.sqlite.bundled)
        }
        // Repositório, fila, Ktor e mappers testados com fakes, em todos os alvos:
        // testAndroidHostTest (JVM) e iosSimulatorArm64Test (simulador do iOS).
        commonTest.dependencies {
            // Mesmo pacote (testutil): o applyFilter dos fakes do domínio é usado sem import.
            implementation(project(":core:testing"))
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.room.testing)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.junit)
            implementation(libs.androidx.espresso.core)
        }
    }
}

dependencies {
    // O Room gera a implementação do banco e do DAO para cada alvo.
    add("kspAndroid", libs.room.compiler)
    add("kspIosArm64", libs.room.compiler)
    add("kspIosSimulatorArm64", libs.room.compiler)
}

room {
    // Exporta o schema para versionar as migrações do banco
    schemaDirectory("$projectDir/schemas")
}

// Num módulo Android comum o plugin do Room põe os esquemas nos assets do APK de teste
// sozinho; com o plugin KMP do AGP 9 não. O MigrationTestHelper lê de lá cada versão
// (AppDatabase/1.json...) para criar o banco antigo e migrar.
androidComponents {
    onVariants { variant ->
        variant.deviceTests.values.forEach { test ->
            test.sources.assets?.addStaticSourceDirectory("schemas")
        }
    }
}

detekt {
    // O padrão do detekt é src/main e src/test; num módulo KMP o código fica em src/<sourceSet>.
    source.setFrom(fileTree("src") { include("**/*.kt") })
}
