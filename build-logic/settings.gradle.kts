// Build separado com os convention plugins do projeto: cada módulo declara só o papel
// dele ("sou um app Android", "uso Hilt") e a configuração fica num lugar só.
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
