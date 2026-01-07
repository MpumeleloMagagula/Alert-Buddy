// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    // Android & Kotlin plugins are defined here but NOT applied
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false

    // Google Services plugin (Firebase)
    id("com.google.gms.google-services") version "4.4.0" apply false
}
