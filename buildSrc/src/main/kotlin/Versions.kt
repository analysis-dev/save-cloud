object Versions {
    const val jdk = "11"  // jdk version that will be used as kotlin compiler target
    const val BP_JVM_VERSION = "11"  // jvm version for spring boot image build
    const val kotlin = "1.4.31"
    const val springBoot = "2.4.4"
    const val springSecurity = "5.4.5"
    const val hibernate = "5.4.2.Final"
    const val liquibase = "4.3.1"
    const val slf4j = "1.7.30"
    const val logback = "1.2.3"
    const val dockerJavaApi = "3.2.7"
    const val ktor = "1.5.2"
    const val coroutines = "1.4.3"
    const val serialization = "1.1.0"  // serialization is compiled by 1.4.30 since version 1.1.0 and for native ABI is different. We can update serialization only after we update kotlin.
    const val micrometer = "1.6.5"
    const val mySql = "8.0.20"
    const val jpa = "1.0.2.Final"
    const val liquibaseGradlePlugin = "2.0.4"
    const val testcontainers = "1.15.2"
    const val react = "17.0.1"
    const val kotlinJsWrappersSuffix = "-pre.148-kotlin-1.4.30"
    const val kotlinReact = "$react$kotlinJsWrappersSuffix"
    const val jgit = "5.11.0.202103091610-r"
    const val okhttp3 = "4.9.1"
    const val kotlinxDatetime = "0.1.1"
}
