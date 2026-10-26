plugins {
	alias(libs.plugins.aboutlibraries)
	alias(libs.plugins.android.application)
	alias(libs.plugins.kotlin.compose)
	alias(libs.plugins.kotlin.serialization)
}

android {
	namespace = "org.jellyfin.androidtv"
	compileSdk = libs.versions.android.compileSdk.get().toInt()

	defaultConfig {
		minSdk = libs.versions.android.minSdk.get().toInt()
		targetSdk = libs.versions.android.targetSdk.get().toInt()

		// Release version
		// Jelly Cinema TV: replaces the earlier web-page version of the app (same id and signing key)
		applicationId = "app.jellycinema.tv"
		versionName = (project.findProperty("jc.versionName") as String?) ?: "2.0.0"
		versionCode = ((project.findProperty("jc.versionCode") as String?) ?: "100").toInt()
		// Where sideloaded builds look for updates first (https only), e.g. the latest GitHub release:
		// -Pjc.updateUrl=https://github.com/<owner>/<repo>/releases/latest/download/jellycinema-tv.json
		// Empty = only the connected server's web/hub folder.
		val updateUrl = (project.findProperty("jc.updateUrl") as String?).orEmpty().replace("\"", "")
		buildConfigField("String", "UPDATE_URL", "\"$updateUrl\"")
		// Where the app's source code can be downloaded (shown in Settings > About; GPL-2.0)
		// GPL: everyone who gets the app must be able to get its source code, so every build carries a link
		// (Settings → About). Defaults to the public repository; -Pjc.sourceUrl=... overrides it.
		val sourceUrl = ((project.findProperty("jc.sourceUrl") as String?) ?: "https://github.com/techsystems23-llc/jellycinema-tv").replace("\"", "")
		buildConfigField("String", "SOURCE_URL", "\"$sourceUrl\"")
		// "Support the developer" page (shown as a QR code), e.g. -Pjc.supportUrl=https://ko-fi.com/<name>.
		// Empty: the item is hidden. The Play and Amazon builds never show it (the Play version is paid instead).
		val supportUrl = (project.findProperty("jc.supportUrl") as String?).orEmpty().replace("\"", "")
		buildConfigField("String", "SUPPORT_URL", "\"$supportUrl\"")
	}

	buildFeatures {
		buildConfig = true
		viewBinding = true
		compose = true
		resValues = true
	}

	compileOptions {
		isCoreLibraryDesugaringEnabled = true
	}

	signingConfigs {
		val keystoreFile = getProperty("keystore.file")
		val keystorePassword = getProperty("keystore.password")
		val signingKeyAlias = getProperty("signing.key.alias")
		val signingKeyPassword = getProperty("signing.key.password")

		if (keystoreFile != null && keystorePassword != null && signingKeyAlias != null && signingKeyPassword != null) {
			create("release") {
				storeFile = file(keystoreFile)
				storePassword = keystorePassword
				keyAlias = signingKeyAlias
				keyPassword = signingKeyPassword
			}
		}
	}

	dependenciesInfo {
		includeInBundle = false
		includeInApk = false
	}

	// Smaller downloads: one APK per processor type (TVs are almost all ARM), plus a universal APK for
	// anything else. 32-bit Intel (x86) is left out: no TV uses it. Play bundles split by themselves.
	splits {
		abi {
			isEnable = true
			reset()
			include("arm64-v8a", "armeabi-v7a", "x86_64")
			isUniversalApk = true
		}
	}
	packaging {
		jniLibs {
			excludes += "lib/x86/**"
		}
	}

	buildTypes {
		release {
			isMinifyEnabled = true
			isShrinkResources = true
			proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")

			// Set package names used in various XML files
			resValue("string", "app_id", "app.jellycinema.tv")
			resValue("string", "app_search_suggest_authority", "app.jellycinema.tv.content")
			resValue("string", "app_search_suggest_intent_data", "content://app.jellycinema.tv.content/intent")

			// Set flavored application name
			resValue("string", "app_name", "@string/app_name_release")

			buildConfigField("boolean", "DEVELOPMENT", "false")
			// Sideloaded builds can update themselves from the Jellyfin server
			buildConfigField("boolean", "SELF_UPDATE", "true")

			signingConfig = signingConfigs.findByName("release")
		}

		// Google Play build: same as release, but no self-update and no install-apps permission,
		// because Play doesn't allow apps to update themselves outside the store.
		// Build with ./gradlew :app:bundlePlay
		create("play") {
			initWith(getByName("release"))
			matchingFallbacks += listOf("release")
			buildConfigField("boolean", "SELF_UPDATE", "false")
			buildConfigField("String", "SUPPORT_URL", "\"\"")
		}

		// Amazon Appstore build (Fire TV): same rules as Play, updates come from the Appstore.
		// Build with ./gradlew :app:assembleAmazon
		create("amazon") {
			initWith(getByName("release"))
			matchingFallbacks += listOf("release")
			buildConfigField("boolean", "SELF_UPDATE", "false")
			buildConfigField("String", "SUPPORT_URL", "\"\"")
		}

		debug {
			// Use different application id to run release and debug at the same time
			applicationIdSuffix = ".debug"

			// Set package names used in various XML files
			resValue("string", "app_id", "app.jellycinema.tv" + applicationIdSuffix)
			resValue("string", "app_search_suggest_authority", "app.jellycinema.tv${applicationIdSuffix}.content")
			resValue("string", "app_search_suggest_intent_data", "content://app.jellycinema.tv${applicationIdSuffix}.content/intent")

			// Set flavored application name
			resValue("string", "app_name", "@string/app_name_debug")

			buildConfigField("boolean", "DEVELOPMENT", (defaultConfig.versionCode!! < 100).toString())
			buildConfigField("boolean", "SELF_UPDATE", "true")
		}
	}


	lint {
		lintConfig = file("$rootDir/android-lint.xml")
		abortOnError = false
		checkDependencies = true
	}

	testOptions.unitTests.all {
		it.useJUnitPlatform()
	}
}

base.archivesName.set("jellycinema-tv")

tasks.register("versionTxt") {
	val path = layout.buildDirectory.asFile.get().resolve("version.txt")

	doLast {
		val versionString = "v${android.defaultConfig.versionName}=${android.defaultConfig.versionCode}"
		logger.info("Writing [$versionString] to $path")
		path.writeText("$versionString\n")
	}
}

dependencies {
	// Jellyfin
	implementation(projects.design)
	implementation(projects.playback.core)
	implementation(projects.playback.jellyfin)
	implementation(projects.playback.media3.exoplayer)
	implementation(projects.playback.media3.session)
	implementation(projects.preference)
	implementation(libs.jellyfin.sdk) {
		// Change version if desired
		val sdkVersion = findProperty("sdk.version")?.toString()
		when (sdkVersion) {
			"local" -> version { strictly("latest-SNAPSHOT") }
			"snapshot" -> version { strictly("master-SNAPSHOT") }
			"unstable-snapshot" -> version { strictly("openapi-unstable-SNAPSHOT") }
		}
	}

	// Kotlin
	implementation(libs.kotlinx.coroutines)
	implementation(libs.kotlinx.serialization.json)

	// Android(x)
	implementation(libs.androidx.core)
	implementation(libs.zxing.core)
	implementation(libs.androidx.activity)
	implementation(libs.androidx.activity.compose)
	implementation(libs.androidx.fragment)
	implementation(libs.androidx.fragment.compose)
	implementation(libs.androidx.leanback.core)
	implementation(libs.androidx.leanback.preference)
	implementation(libs.androidx.navigation3.ui)
	implementation(libs.androidx.preference)
	implementation(libs.androidx.appcompat)
	implementation(libs.androidx.tvprovider)
	implementation(libs.androidx.constraintlayout)
	implementation(libs.androidx.recyclerview)
	implementation(libs.androidx.work.runtime)
	implementation(libs.bundles.androidx.lifecycle)
	implementation(libs.androidx.window)
	implementation(libs.androidx.cardview)
	implementation(libs.androidx.startup)
	implementation(libs.bundles.androidx.compose)
	implementation(libs.accompanist.permissions)

	// Dependency Injection
	implementation(libs.bundles.koin)

	// Media players
	implementation(libs.androidx.media3.exoplayer)
	// YouTube trailers through YouTube's official embedded player (Apache-2.0 library)
	implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0")
	implementation(libs.androidx.media3.datasource.okhttp)
	implementation(libs.androidx.media3.exoplayer.hls)
	implementation(libs.androidx.media3.ui)
	implementation(libs.jellyfin.androidx.media3.ffmpeg.decoder)
	implementation(libs.libass.media3)

	// Markdown
	implementation(libs.bundles.markwon)

	// Image utility
	implementation(libs.bundles.coil)

	// Crash Reporting
	implementation(libs.bundles.acra)

	// Licenses
	implementation(libs.aboutlibraries)

	// Logging
	implementation(libs.timber)
	implementation(libs.slf4j.timber)

	// Compatibility (desugaring)
	coreLibraryDesugaring(libs.android.desugar)

	// Testing
	testImplementation(libs.kotest.runner.junit5)
	testImplementation(libs.kotest.assertions)
	testImplementation(libs.mockk)
}
