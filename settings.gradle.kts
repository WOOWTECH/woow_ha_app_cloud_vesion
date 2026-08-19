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

// Integration-branch bypass: the base tag v2026.8.3-cloud-alpha1 has a
// pre-release suffix ('cloud-alpha1') that reckon cannot map to a next
// version (stages allowlist is 'beta', 'final'; 'cloud-alpha' isn't in it).
// The tag is an official GitHub Release marker with a distributed APK asset
// and can't be renamed or deleted. Any reckon config we try still fails
// the guard `reckoned < base` because reckon starts from 0.0.0 when the base
// isn't parseable.
//
// Bypass reckon entirely on this branch and hardcode a version above the
// base tag. Revert or find a permanent solution (rename tag / add stage /
// upgrade reckon) before merging to main.
if (!isWorktree) {
    gradle.beforeProject {
        version = "2027.0.0-beta.1+integrated"
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
