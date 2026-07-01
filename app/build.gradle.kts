import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val propsFile = file("../local.properties")
    if (propsFile.exists()) propsFile.inputStream().use { load(it) }
}
val releaseStoreFile: String? = localProperties.getProperty("keystore.path")
val releaseStorePassword: String? = localProperties.getProperty("keystore.password")
val releaseKeyAlias: String? = localProperties.getProperty("keystore.alias")
val releaseKeyPassword: String? = localProperties.getProperty("keystore.keyPassword")
val hasReleaseSigning = listOf(
    releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "me.zhanghai.android.filesfork"
    buildToolsVersion = "36.0.0"
    compileSdk = 37
    ndkVersion = "29.0.14206865"
    defaultConfig {
        applicationId = "me.zhanghai.android.filesfork"
        minSdk = 23
        //noinspection OldTargetApi
        targetSdk = 34
        versionCode = 40
        versionName = "1.7.4+2"
        resValue("string", "app_version", "$versionName (${versionCode})")
        buildConfigField(
            "String", "FILE_PROVIDIER_AUTHORITY", "APPLICATION_ID + \".file_provider\""
        )
        resValue("string", "app_provider_authority", "${applicationId}.app_provider")
        resValue("string", "file_provider_authority", "${applicationId}.file_provider")
        versionNameSuffix = ".fork"
        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=none")
            }
        }
        ndk {
            abiFilters += "arm64-v8a"
        }
    }
    buildFeatures {
        aidl = true
        buildConfig = true
        viewBinding = true
        compose = true
        resValues = true
    }
    androidResources {
        generateLocaleConfig = true
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    externalNativeBuild {
        cmake {
            path("CMakeLists.txt")
        }
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }
    lint {
        warning += listOf("InvalidPackage", "MissingTranslation")
    }
    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
            )
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += listOf(
                "META-INF/DEPENDENCIES",
                "org/bouncycastle/pqc/crypto/picnic/*",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json"
            )
        }
    }
}

configurations.all {
    exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
    exclude(group = "org.bouncycastle", module = "bcpkix-jdk18on")
    exclude(group = "org.bouncycastle", module = "bcutil-jdk18on")
    exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    exclude(group = "org.bouncycastle", module = "bcpkix-jdk15on")
}

dependencies {
    implementation(libs.dav4jvm) {
        exclude(group = "org.ogce", module = "xpp3")
    }
    implementation(libs.libsu.service)
    implementation(libs.zoomable.image.coil)
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.kotlin.stdlib.jdk8)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.common.java8)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.material)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidsvg)
    implementation(libs.drakeet.drawer)
    implementation(libs.material.shadow.ninepatch)
    implementation(libs.adv.recyclerview)
    implementation(libs.smbj) {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk15on")
    }
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.sshj) {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
        exclude(group = "org.bouncycastle", module = "bcpkix-jdk18on")
    }
    implementation(libs.bouncycastle.bcpkix)
    implementation(libs.speed.dial)
    implementation(libs.dcerpc) {
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "com.hierynomus", module = "smbj")
        exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
    }
    implementation(libs.guava)
    implementation(libs.guava.listenablefuture)
    implementation(libs.preferencex)
    implementation(libs.commons.net)
    implementation(libs.licenses.dialog)
    implementation(libs.insetter.ktx)
    implementation(libs.simple.menu.preference)
    implementation(libs.shizuku.api)
    implementation(libs.jcifs.ng) {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
    }
    implementation(platform(libs.coil.bom))
    implementation(libs.coil)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.coil.video)
    implementation(libs.appiconloader)
    implementation(libs.fastscroll)
    implementation(libs.foreground.compat)
    implementation(libs.libarchive)
    implementation(libs.libselinux)
    implementation(libs.retrofile)
    implementation(libs.systemuihelper)
    implementation(libs.ftpserver.core)
    implementation(libs.mina.core)
    implementation(libs.slf4j.android)
    implementation(project(":sora-editor:editor"))
    implementation(project(":sora-editor:language-textmate"))
    implementation("androidx.datastore:datastore-preferences:1.2.1")
}
