plugins {
    java
    application
}

group = "br.com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val labs = sourceSets.create("labs") {
    java.srcDir("labs")
}

dependencies {
    implementation("org.xerial:sqlite-jdbc:3.+")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    testImplementation(platform("org.junit:junit-bom:5.+"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.assertj:assertj-core:3.+")
    testImplementation("org.mockito:mockito-junit-jupiter:5.+")
}

configurations {
    getByName(labs.implementationConfigurationName) {
        extendsFrom(testImplementation.get())
    }
    getByName(labs.runtimeOnlyConfigurationName) {
        extendsFrom(testRuntimeOnly.get())
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

application {
    mainClass = "br.com.example.Main"
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

tasks.test {
    useJUnitPlatform()
}