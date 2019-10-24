import io.kotless.DSLType
import io.kotless.plugin.gradle.dsl.Webapp
import io.kotless.plugin.gradle.dsl.Webapp.Route53
import io.kotless.plugin.gradle.dsl.kotless

group = "io.kotless"
version = "0.1.0"

plugins {
    id("tanvd.kosogor") version "1.0.7" apply true

    kotlin("jvm") version "1.3.50" apply true

    id("io.kotless") version "0.1.2-SNAPSHOT" apply true
}

repositories {
    mavenLocal()
    //artifacts are located at JCenter
    jcenter()
}

dependencies {
    implementation("io.kotless", "lang", "0.1.2-SNAPSHOT")

    implementation("commons-validator", "commons-validator", "1.6")
    implementation("com.amazonaws", "aws-java-sdk-dynamodb", "1.11.650")

    implementation("org.jetbrains.kotlinx", "kotlinx-html-jvm", "0.6.11")
}

kotless {
    config {
        bucket = "eu.short.s3.ktls.aws.intellij.net"
        prefix = "short"

        workDirectory = file("src/main/static")

        dsl = DSLType.Kotless

        terraform {
            profile = "kotless-jetbrains"
            region = "eu-west-1"
        }
    }

    webapp {
        lambda {
            kotless(Webapp.Lambda.KotlessDSL(setOf("io.kotless.examples")))
        }

        route53 = Route53("short", "kotless.io")
    }

    extensions {
        terraform {
            files {
                add(file("src/main/tf/extensions.tf"))
            }
        }
    }
}
