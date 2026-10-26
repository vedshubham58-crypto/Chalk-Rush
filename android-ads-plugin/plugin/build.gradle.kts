// A small Godot Android plugin (v2) that shows Google AdMob interstitial and
// rewarded ads. Built by CI into godot/addons/neon_ads/bin/neon-ads-release.aar.
plugins {
    id("com.android.library")
}

android {
    namespace = "com.vedshubham.neonads"
    compileSdk = 34
    defaultConfig {
        minSdk = 21
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    compileOnly("org.godotengine:godot:4.3.0.stable")
    // Godot adds this to the game too (see godot/addons/neon_ads/plugin.gd); keep the versions in step.
    compileOnly("com.google.android.gms:play-services-ads:23.6.0")
}
