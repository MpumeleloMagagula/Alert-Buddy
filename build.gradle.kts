// Top-level build file where you can add configuration options common to all sub-projects/modules.

plugins {
    // Android & Kotlin plugins are defined here but NOT applied
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false

    // Modern way: Centralized version management via Version Catalog
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.google.devtools.ksp) apply false

}
