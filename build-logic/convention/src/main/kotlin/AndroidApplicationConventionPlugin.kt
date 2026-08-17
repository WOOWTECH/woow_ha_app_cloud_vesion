import com.android.build.api.dsl.ApplicationExtension
import io.homeassistant.companion.android.getPluginId
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

private const val APPLICATION_ID = "com.woowtech.homecloud"
private const val NAMESPACE = "io.homeassistant.companion.android"

// WOOW paas 環境設定：debug 走 stg，release 走 prod。
// prod 端點皆在網域根路徑下（已由 paas-platform 諮詢確認，`/woow` 只是 SPA 網頁入口，不是 API 前綴）。
private const val WOOW_PAAS_BASE_URL_DEBUG = "https://stg.woowtech.io"
private const val WOOW_PAAS_BASE_URL_RELEASE = "https://paas.woowtech.io"
private const val WOOW_PAAS_CLIENT_ID = "woow-ha-app"
private const val WOOW_PAAS_SCOPES = "ha:provision workspace:read smarthome:read"

/**
 * A convention plugin that applies common configurations to Android application modules.
 * This centralizes configuration, preventing duplication across multiple modules.
 *
 * This plugin applies several Gradle plugins that are commonly used in all application modules,
 * including the [AndroidCommonConventionPlugin].
 *
 * After applying this plugin, the configured values can be overridden if necessary. However,
 * if extensive overrides are required, it may indicate that the configuration should be moved
 * out of this convention plugin.
 *
 * The application's `versionCode` can be set via the `VERSION_CODE` environment variable.
 *
 * A `release` signing configuration is automatically created. The keystore information can be
 * provided through the following environment variables:
 * - `KEYSTORE_PATH`: The path to the keystore file.
 * - `KEYSTORE_PASSWORD`: The password for the keystore.
 * - `KEYSTORE_ALIAS`: The alias for the key within the keystore.
 * - `KEYSTORE_ALIAS_PASSWORD`: The password for the key alias.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.plugins.android.application.getPluginId())
            apply(plugin = libs.plugins.kotlin.android.getPluginId())
            apply(plugin = libs.plugins.ksp.getPluginId())
            apply(plugin = libs.plugins.hilt.getPluginId())
            AndroidCommonConventionPlugin().apply(target)
            AndroidComposeConventionPlugin().apply(target)

            extensions.configure<ApplicationExtension> {
                namespace = NAMESPACE

                defaultConfig {
                    applicationId = APPLICATION_ID
                    targetSdk = libs.versions.androidSdk.target.get().toInt()

                    versionName = project.version.toString()
                    versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1

                    val noStrictMode = project.findProperty("noStrictMode")?.toString()?.ifEmpty { "true" }
                        ?.toBoolean() ?: false
                    buildConfigField("Boolean", "NO_STRICT_MODE", noStrictMode.toString())
                }

                buildFeatures {
                    viewBinding = true
                }

                signingConfigs {
                    create("release") {
                        storeFile = file(System.getenv("KEYSTORE_PATH") ?: "release_keystore.keystore")
                        storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
                        keyAlias = System.getenv("KEYSTORE_ALIAS") ?: ""
                        keyPassword = System.getenv("KEYSTORE_ALIAS_PASSWORD") ?: ""
                        enableV1Signing = true
                        enableV2Signing = true
                    }
                }

                buildTypes {
                    named("debug").configure {
                        applicationIdSuffix = ".debug"
                        buildConfigField("String", "WOOW_PAAS_BASE_URL", "\"$WOOW_PAAS_BASE_URL_DEBUG\"")
                        buildConfigField("String", "WOOW_PAAS_CLIENT_ID", "\"$WOOW_PAAS_CLIENT_ID\"")
                        buildConfigField("String", "WOOW_PAAS_SCOPES", "\"$WOOW_PAAS_SCOPES\"")
                    }
                    named("release").configure {
                        isDebuggable = false
                        isJniDebuggable = false
                        signingConfig = signingConfigs.getByName("release")
                        buildConfigField("String", "WOOW_PAAS_BASE_URL", "\"$WOOW_PAAS_BASE_URL_RELEASE\"")
                        buildConfigField("String", "WOOW_PAAS_CLIENT_ID", "\"$WOOW_PAAS_CLIENT_ID\"")
                        buildConfigField("String", "WOOW_PAAS_SCOPES", "\"$WOOW_PAAS_SCOPES\"")
                    }
                }
            }
        }
    }
}
