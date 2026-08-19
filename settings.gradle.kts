include(":common", ":app", ":wear", ":automotive", ":testing-unit", ":lint")

rootProject.name = "home-assistant-android"

includeBuild("build-logic")

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

// Reckon plugin doesn't work in git worktrees (JGit doesn't handle worktree .git files).
// Detect worktrees (.git is a file, not a directory) and skip reckon in that case.
val isWorktree = settings.settingsDir.resolve(".git").isFile

plugins {
    // So we can't reach the libs.plugins.* aliases from here so we need to declare them the old way...
    id("org.ajoberstar.reckon.settings").version("1.0.1").apply(false)
}

if (!isWorktree) {
    apply(plugin = "org.ajoberstar.reckon.settings")

    extensions.configure<org.ajoberstar.reckon.gradle.ReckonExtension>("reckon") {
        val isCiBuild = providers.environmentVariable("CI").isPresent

        setDefaultInferredScope("patch")
        if (!isCiBuild) {
            // Use a snapshot version scheme with Reckon when not running in CI, which allows caching to
            // improve performance. Background: https://github.com/home-assistant/android/issues/5220.
            snapshots()
        } else {
            stages("beta", "final")
        }
        // Integration-branch override: base tag v2026.8.3-cloud-alpha1 has a
        // non-standard pre-release suffix ('cloud-alpha1' rather than 'beta.N'
        // or 'alpha.N'), which reckon can't map to a next-version. Forcing
        // MAJOR scope makes reckon compute 2027.0.0-beta.N which is well
        // above the base tag, unblocking dev builds. Revert this or rename
        // the tag before merging to main.
        setScopeCalc { java.util.Optional.of(org.ajoberstar.reckon.core.Scope.MAJOR) }
        setStageCalc(calcStageFromProp())
        setTagWriter { it.toString() }
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
                includeGroupByRegex("org\\.chromium.*")
            }
        }
        mavenCentral()
        google()
        maven("https://jitpack.io")
    }
}
