import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm") version "1.4.21"
}

allprojects()
{
	apply(plugin = "org.jetbrains.kotlin.jvm")
	
	group = "strixpyrr.eagleeye"
	version = "0.0.1"
	
	repositories()
	{
		mavenCentral()
		mavenLocal()
		maven(url = "https://kotlin.bintray.com/kotlinx")
	}
	
	dependencies {
		implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3")
		implementation("com.github.ajalt.mordant:mordant:2.0.0-alpha1")
		
		implementation("strixpyrr.abstrakt:Abstrakt:0.1.2")
		implementation("uy.kohesive.klutter:klutter-core:3.0.0")
	}
	
	val compileKotlin: KotlinCompile by tasks
	
	compileKotlin.kotlinOptions()
	{
		jvmTarget = "9"
		
		useIR = true
		
		noStdlib = false
		
		freeCompilerArgs = listOf("-Xopt-in=kotlinx.cli.ExperimentalCli", "-Xopt-in=kotlin.RequiresOptIn", "-Xjvm-default=all", "-Xstring-concat=indy-with-constants")
	}
}

dependencies {
	implementation(project(":Common"))
	implementation(project(":DataAggregator"))
}