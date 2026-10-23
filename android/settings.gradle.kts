pluginManagement {
    repositories {
        val relay = System.getenv("DUOLIAN_MAVEN_RELAY")
        if (!relay.isNullOrBlank()) {
            require(relay == "http://127.0.0.1:17861")
            listOf("google", "central", "plugins").forEach { name ->
                maven { url = uri("$relay/$name"); isAllowInsecureProtocol = true }
            }
        } else { google(); mavenCentral(); gradlePluginPortal() }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        val relay = System.getenv("DUOLIAN_MAVEN_RELAY")
        if (!relay.isNullOrBlank()) {
            require(relay == "http://127.0.0.1:17861")
            listOf("google", "central").forEach { name ->
                maven { url = uri("$relay/$name"); isAllowInsecureProtocol = true }
            }
        } else { google(); mavenCentral() }
    }
}
rootProject.name = "独练"
include(":app")
