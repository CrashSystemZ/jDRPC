import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java-library`
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "fun.crashsystem"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    api("com.google.code.gson:gson:2.11.0")
    api("org.slf4j:slf4j-api:2.0.13")
    api("org.apache.commons:commons-lang3:3.14.0")

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
}