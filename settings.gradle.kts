pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Local AndroidSolidServices builds (0.6.0-dev); remove once published to Maven Central.
        mavenLocal {
            content {
                includeGroup("com.erfangholami.androidsolidservices")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "Solid Share"
include(":app")
