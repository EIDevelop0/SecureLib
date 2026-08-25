plugins {
    alias(libs.plugins.android.library)
    `maven-publish`
}

// Coordinates used by Gradle source dependencies on the consumer side.
// A consumer declares:
//   sourceControl {
//     gitRepository(URI("git@github.com:<org>/SecureLib.git")) {
//       producesModule("com.securelib:securecheck")
//     }
//   }
// The (group, artifactId) below MUST match that producesModule string.
group = "com.securelib"
version = "0.1.0"

android {
    namespace = "com.securelib.securecheck"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.play.integrity)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "securecheck"
            version = project.version.toString()

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}
