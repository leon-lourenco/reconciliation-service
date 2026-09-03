plugins {
    // Java 21 is this project's toolchain (see build.gradle.kts). This resolver lets Gradle fetch a
    // matching JDK when the machine running the build only has a different one installed, so the
    // build produces the same bytecode everywhere instead of silently following whatever JDK is on PATH.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "reconciliation-service"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("card-billing-shared/versions/libs.versions.toml"))
        }
    }
}
