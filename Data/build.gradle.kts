import com.squareup.wire.gradle.WireExtension

buildscript {
	repositories {
		mavenCentral()
	}
	
	dependencies {
		classpath(group = "com.squareup.wire", name = "wire-gradle-plugin", version = "3.5.0")
	}
}

plugins {
	idea
}

// Wire doesn't resolve when using the plugin DSL, so it'll have to done by Apply.
// Apparently Gradle thinks it's a good idea to try resolving "com.squareup.wire.gradle.plugin",
// then complains when it isn't found.
apply(plugin = "com.squareup.wire")

dependencies {
	implementation("com.squareup.retrofit2:retrofit:2.9.0")
	implementation("com.squareup.retrofit2:converter-jackson:2.9.0")
	
	implementation(kotlin("reflect"))
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.3")
	
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
	
	implementation("strixpyrr.abstrakt:Abstrakt:0.1.3")
	implementation("strixpyrr.abstrakt:Abstrakt.Collections:0.0.6")
}

kotlin {
	sourceSets {
		main {
			// Feels weird adding non-Kotlin files to the Kotlin directory set.
			// Ive tried to figure out how to create a new directory set, to no
			// avail.
			kotlin.srcDir("src/main/proto")
		}
	}
}

val wire: WireExtension by extensions

wire.run()
{
	sourcePath {
		srcDir("src/main/proto")
	}
	
	kotlin { }
	
	sinceVersion("1")
}