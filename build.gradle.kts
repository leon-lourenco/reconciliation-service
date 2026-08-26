plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.cardbilling"
version = "1.0.0-SNAPSHOT"
description = "External statement ingest and matching, via indexed lookup instead of a nested loop"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.aspectj)

    // This service is both an OAuth2 resource server (its own endpoints need a Bearer token) and an
    // OAuth2 client (it obtains its own token, client-credentials, before calling billing-service).
    implementation(libs.spring.boot.starter.oauth2.resource.server)
    implementation(libs.spring.boot.starter.oauth2.client)

    implementation(libs.spring.boot.restclient)
    implementation(libs.resilience4j.spring.boot)
    implementation(libs.springdoc.openapi.starter.webmvc.ui)
    implementation(libs.opencsv)

    // The schema is written down and versioned rather than inferred by Hibernate: the indexes this
    // service reads through are the point of it, so they belong in a migration, not in ddl-auto.
    implementation(libs.spring.boot.flyway)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.archunit.junit5)

    // The standalone (shaded) WireMock distribution: billing-service does not exist as a running
    // service yet, and the shaded jar keeps WireMock's own Jetty off this application's classpath.
    testImplementation(libs.wiremock.standalone)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
