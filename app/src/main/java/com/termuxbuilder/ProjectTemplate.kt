package com.termuxbuilder

import android.content.Context
import java.io.File

object ProjectTemplate {

    fun create(ctx: Context, root: File, name: String, pkg: String, minSdk: Int, templateIdx: Int) {
        root.mkdirs()

        val pkgPath = pkg.replace(".", "/")
        val srcDir = File(root, "app/src/main/java/$pkgPath")
        val resDir = File(root, "app/src/main/res")
        val resLayout = File(resDir, "layout")
        val resValues = File(resDir, "values")
        val resDrawable = File(resDir, "drawable")
        val resMipmap = File(resDir, "mipmap-anydpi-v26")

        listOf(srcDir, resLayout, resValues, resDrawable, resMipmap).forEach { it.mkdirs() }

        // Project build.gradle.kts
        File(root, "build.gradle.kts").writeText("""plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
""")

        // settings.gradle.kts
        File(root, "settings.gradle.kts").writeText("""pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "$name"
include(":app")
""")

        // gradle.properties
        File(root, "gradle.properties").writeText("""org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
""")

        // gradle wrapper properties
        val wrapperDir = File(root, "gradle/wrapper")
        wrapperDir.mkdirs()
        File(wrapperDir, "gradle-wrapper.properties").writeText("""distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
networkTimeout=10000
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
""")

        // gradlew script
        val D = "${'$'}"
        val gradlewScript = """#!/data/data/com.termux/files/usr/bin/bash
APP_HOME="${D}( cd -- "${D}( dirname -- "${D}0" )" &> /dev/null && pwd )"
APP_NAME="Gradle"
APP_BASE_NAME=${D}0
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
MAX_FD="maximum"
warn () { echo "${D}*"; }
die () { echo; echo "${D}*"; echo; exit 1; }
cygwin=false
msys=false
darwin=false
nonstop=false
CLASSPATH=${D}APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ -n "${D}JAVA_HOME" ] ; then
    JAVACMD=${D}JAVA_HOME/bin/java
    if [ ! -x "${D}JAVACMD" ] ; then die "ERROR: JAVA_HOME is set to an invalid directory: ${D}JAVA_HOME"; fi
else
    JAVACMD=java
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no java command found."
fi
if [ "${D}cygwin" = "false" -a "${D}darwin" = "false" -a "${D}nonstop" = "false" ] ; then
    MAX_FD_LIMIT=${D}(ulimit -H -n)
    if [ ${D}? -eq 0 ] ; then
        if [ "${D}MAX_FD" = "maximum" -o "${D}MAX_FD" = "max" ] ; then MAX_FD="${D}MAX_FD_LIMIT"; fi
        ulimit -n ${D}MAX_FD
        if [ ${D}? -ne 0 ] ; then warn "Could not set maximum file descriptor limit: ${D}MAX_FD"; fi
    fi
fi
exec "${D}JAVACMD" ${D}DEFAULT_JVM_OPTS ${D}JAVA_OPTS ${D}GRADLE_OPTS "-Dorg.gradle.appname=${D}APP_BASE_NAME" -classpath "${D}CLASSPATH" org.gradle.wrapper.GradleWrapperMain "${D}@"
"""
        File(root, "gradlew").writeText(gradlewScript)
        File(root, "gradlew").setExecutable(true)


// app/build.gradle.kts
        File(root, "app/build.gradle.kts").writeText("""plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "$pkg"
    compileSdk = 34
    defaultConfig {
        applicationId = "$pkg"
        minSdk = $minSdk
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}
""")

        // AndroidManifest.xml
        File(root, "app/src/main/AndroidManifest.xml").writeText("""<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="true"
        android:label="$name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Material3.DayNight.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
""")

        // MainActivity.kt
        File(srcDir, "MainActivity.kt").writeText("""package $pkg

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
""")

        // activity_main.xml
        File(resLayout, "activity_main.xml").writeText("""<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Hello, $name!"
        android:textSize="24sp"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintLeft_toLeftOf="parent"
        app:layout_constraintRight_toRightOf="parent"
        app:layout_constraintTop_toTopOf="parent" />

</androidx.constraintlayout.widget.ConstraintLayout>
""")

        // strings.xml
        File(resValues, "strings.xml").writeText("""<resources>
    <string name="app_name">$name</string>
</resources>
""")

        // colors.xml
        File(resValues, "colors.xml").writeText("""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
</resources>
""")

        // themes.xml
        File(resValues, "themes.xml").writeText("""<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.${name.replace(" ", "_")}" parent="Theme.Material3.DayNight.NoActionBar" />
</resources>
""")

        // ic_launcher.xml
        File(resMipmap, "ic_launcher.xml").writeText("""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/white"/>
    <foreground android:drawable="@color/black"/>
</adaptive-icon>
""")

        // build.sh
        File(root, "build.sh").writeText("""#!/data/data/com.termux/files/usr/bin/bash
echo ">> 编译: $name"
cd "$root" && ./gradlew assembleDebug
echo ">> APK: app/build/outputs/apk/debug/"
""")
        File(root, "build.sh").setExecutable(true)
    }
}
