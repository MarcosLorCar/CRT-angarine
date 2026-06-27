plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    id("com.gradleup.shadow") version "9.4.2"
    application
}

group = "me.orange"
version = "1.0.0"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    val ktor_version = "3.0.1"
    implementation(project(":shared"))
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-server-config-yaml:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-websockets:$ktor_version")
    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
}

val buildWebapp = tasks.register<Exec>("buildWebapp") {
    description = "Builds the frontend webapp"
    inputs.dir(file("../webapp/src"))
    inputs.file(file("../webapp/package.json"))
    inputs.file(file("../webapp/package-lock.json"))
    inputs.file(file("../webapp/vite.config.ts"))
    inputs.file(file("../webapp/index.html"))
    outputs.dir(file("../webapp/dist"))

    workingDir(file("../webapp"))
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    if (isWindows) {
        commandLine("cmd", "/c", "npm install && npm run build")
    } else {
        commandLine("sh", "-c", "npm install && npm run build")
    }
}

tasks.processResources {
    dependsOn(buildWebapp)
    from(file("../webapp/dist")) {
        into("static")
    }
}

