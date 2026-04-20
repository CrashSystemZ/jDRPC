import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "fun.crashsystem"
version = "1.2.1"

repositories {
    mavenCentral()
}

dependencies {
    api("com.google.code.gson:gson:2.11.0")
    api("org.apache.commons:commons-lang3:3.18.0")

    implementation("org.apache.logging.log4j:log4j-api:2.24.1")
    implementation("net.java.dev.jna:jna-platform:5.15.0")
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    withSourcesJar()
    withJavadocJar()
}

tasks.javadoc {
    (options as StandardJavadocDocletOptions).apply {
        encoding = "UTF-8"
        addBooleanOption("Xdoclint:none", true)
    }
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("all")
    mergeServiceFiles()
    // Keep the fat jar lean: consumers pulling jDRPC via Maven/Gradle get these
    // transitively through the POM (implementation scope). Downstream shadow builds
    // that drop the fat jar straight into a `libs/` folder can provide their own
    // copies without worrying about version clashes.
    dependencies {
        exclude(dependency("org.apache.logging.log4j:log4j-api:.*"))
        exclude(dependency("net.java.dev.jna:jna:.*"))
        exclude(dependency("net.java.dev.jna:jna-platform:.*"))
    }
}