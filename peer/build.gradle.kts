plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":domain"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("ru.rudra.androidos.pa.peer.PeerMainKt")
}

tasks.test {
    useJUnitPlatform()
}
