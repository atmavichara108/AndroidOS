// AndroidOS root build. Modules:
//  - :domain      — pure Kotlin contracts, state machines, invariants (no Android deps)
//  - :android-app — provisional native Kotlin runtime implementing the ports
plugins {
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

subprojects {
    group = "ru.rudra.androidos"
    version = "0.1.0-SNAPSHOT"
}
