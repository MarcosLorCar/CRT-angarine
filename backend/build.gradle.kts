plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "me.orange"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(25)
}
dependencies {
    implementation(project(":shared"))
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.cors)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.websockets)
    implementation(libs.logback.classic)

    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
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

