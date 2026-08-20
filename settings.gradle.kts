plugins {
    // Java 21 is this project's toolchain (see build.gradle.kts). This resolver lets Gradle fetch a
    // matching JDK when the machine running the build only has a different one installed, so the
    // build produces the same bytecode everywhere instead of silently following whatever JDK is on PATH.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "reconciliation-service"

// Versions live in gradle/libs.versions.toml, which Gradle picks up as `libs` automatically.
// ARCHITECTURE.md has every service consuming that catalog from the `card-billing-shared`
// submodule instead; that repo does not exist yet, so the catalog is kept here in the same
// shape and moves out wholesale once it does.
