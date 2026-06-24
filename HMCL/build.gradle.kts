import org.jackhuang.hmcl.gradle.mod.ParseModDataTask
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.ZipFile


plugins {
    alias(libs.plugins.shadow)
}

val isOfficial = System.getenv("HMCL_SIGNATURE_KEY") != null
        || (System.getenv("GITHUB_REPOSITORY_OWNER") == "HMCL-dev" && System.getenv("GITHUB_BASE_REF")
    .isNullOrEmpty())

val signingPropsFile = rootProject.file("signing.properties")
if (signingPropsFile.exists()) {
    signingPropsFile.readLines().forEach { line ->
        val trimmed = line.trim()
        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
            val idx = trimmed.indexOf('=')
            if (idx != -1) {
                val key = trimmed.substring(0, idx).trim()
                val value = trimmed.substring(idx + 1).trim()
                if (System.getenv(key) == null) {
                    System.setProperty(key, value)
                }
            }
        }
    }
}
val signatureKey = System.getenv("HMCL_SIGNATURE_KEY")
    ?: System.getProperty("HMCL_SIGNATURE_KEY")
    ?: rootProject.file("mi_firma_hmcl.priv").takeIf { it.exists() }?.absolutePath

val buildNumber = System.getenv("BUILD_NUMBER")?.toInt().let { number ->
    val offset = System.getenv("BUILD_NUMBER_OFFSET")?.toInt() ?: 0
    if (number != null) {
        (number - offset).toString()
    } else {
        val shortCommit = System.getenv("GITHUB_SHA")?.lowercase()?.substring(0, 7)
        val prefix = if (isOfficial) "dev" else "unofficial"
        if (!shortCommit.isNullOrEmpty()) "$prefix-$shortCommit" else "SNAPSHOT"
    }
}
val versionRoot = System.getenv("VERSION_ROOT") ?: "1.0"
val versionType = System.getenv("VERSION_TYPE") ?: if (isOfficial) "nightly" else "unofficial"

val microsoftAuthId = System.getenv("MICROSOFT_AUTH_ID") ?: ""
val microsoftAuthSecret = System.getenv("MICROSOFT_AUTH_SECRET") ?: ""
val curseForgeApiKey = System.getenv("CURSEFORGE_API_KEY") ?: ""

val launcherExe = System.getenv("HMCL_LAUNCHER_EXE")

version = versionRoot

val embedResources by configurations.registering

dependencies {
    implementation(project(":HMCLCore"))
    implementation(project(":HMCLBoot"))
    implementation("libs:JFoenix")
    implementation(libs.twelvemonkeys.imageio.webp)
    implementation(libs.java.info)
    implementation(libs.discord.rpc)

    if (launcherExe == null) {
        implementation(libs.hmclauncher)
    }

    embedResources(libs.authlib.injector)
}

fun digest(algorithm: String, bytes: ByteArray): ByteArray = MessageDigest.getInstance(algorithm).digest(bytes)

fun createChecksum(file: File) {
    val algorithms = linkedMapOf(
        "SHA-1" to "sha1",
        "SHA-256" to "sha256",
        "SHA-512" to "sha512"
    )

    algorithms.forEach { (algorithm, ext) ->
        File(file.parentFile, "${file.name}.$ext").writeText(
            digest(algorithm, file.readBytes()).joinToString(separator = "", postfix = "\n") { "%02x".format(it) }
        )
    }
}

fun attachSignature(jar: File) {
    val keyLocation = signatureKey
    if (keyLocation == null) {
        logger.warn("Missing signature key")
        return
    }

    val privatekey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(File(keyLocation).readBytes()))
    val signer = Signature.getInstance("SHA512withRSA")
    signer.initSign(privatekey)
    ZipFile(jar).use { zip ->
        zip.stream()
            .sorted(Comparator.comparing { it.name })
            .filter { it.name != "META-INF/hmcl_signature" }
            .forEach {
                signer.update(digest("SHA-512", it.name.toByteArray()))
                signer.update(digest("SHA-512", zip.getInputStream(it).readBytes()))
            }
    }
    val signature = signer.sign()
    FileSystems.newFileSystem(URI.create("jar:" + jar.toURI()), emptyMap<String, Any>()).use { zipfs ->
        Files.newOutputStream(zipfs.getPath("META-INF/hmcl_signature")).use { it.write(signature) }
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "11"
    targetCompatibility = "11"
}

tasks.checkstyleMain {
    // Third-party code is not checked
    exclude("**/org/jackhuang/hmcl/ui/image/apng/**")
}

tasks.compileJava {
    options.compilerArgs.add("--add-exports=java.base/jdk.internal.loader=ALL-UNNAMED")
}

val hmclProperties = buildList {
    add("hmcl.version" to project.version.toString())
    System.getenv("GITHUB_SHA")?.let {
        add("hmcl.version.hash" to it)
    }
    add("hmcl.version.type" to versionType)
    add("hmcl.microsoft.auth.id" to microsoftAuthId)
    add("hmcl.microsoft.auth.secret" to microsoftAuthSecret)
    add("hmcl.curseforge.apikey" to curseForgeApiKey)
    add("hmcl.authlib-injector.version" to libs.authlib.injector.get().version!!)
}

val hmclPropertiesFile = layout.buildDirectory.file("hmcl.properties")
val createPropertiesFile by tasks.registering {
    outputs.file(hmclPropertiesFile)
    hmclProperties.forEach { (k, v) -> inputs.property(k, v) }

    doLast {
        val targetFile = hmclPropertiesFile.get().asFile
        targetFile.parentFile.mkdir()
        targetFile.bufferedWriter().use {
            for ((k, v) in hmclProperties) {
                it.write("$k=$v\n")
            }
        }
    }
}

val addOpens = listOf(
    "java.base/java.lang",
    "java.base/java.lang.reflect",
    "java.base/jdk.internal.loader",
    "javafx.base/com.sun.javafx.binding",
    "javafx.base/com.sun.javafx.event",
    "javafx.base/com.sun.javafx.runtime",
    "javafx.graphics/javafx.css",
    "javafx.graphics/com.sun.javafx.stage",
    "javafx.graphics/com.sun.prism",
    "javafx.controls/com.sun.javafx.scene.control",
    "javafx.controls/com.sun.javafx.scene.control.behavior",
    "javafx.controls/javafx.scene.control.skin",
    "jdk.attach/sun.tools.attach",
)

tasks.jar {
    enabled = false
    dependsOn(tasks["shadowJar"])
}

val jarPath = layout.buildDirectory.file("libs/papi-launcher-${project.version}.jar").map { it.asFile }

tasks.shadowJar {
    dependsOn(createPropertiesFile)

    archiveClassifier.set(null as String?)
    archiveBaseName.set("papi-launcher") // Para buildear: VERSION_ROOT=1.0.0 ./gradlew build

    exclude("**/package-info.class")
    exclude("META-INF/maven/**")

    exclude("META-INF/services/javax.imageio.spi.ImageReaderSpi")
    exclude("META-INF/services/javax.imageio.spi.ImageInputStreamSpi")

    minimize {
        exclude(dependency("com.google.code.gson:.*:.*"))
        exclude(dependency("net.java.dev.jna:jna:.*"))
        exclude(dependency("libs:JFoenix:.*"))
        exclude(dependency("com.github.MinnDevelopment:java-discord-rpc:.*"))
        exclude(project(":HMCLBoot"))
    }

    manifest.attributes(
        "Created-By" to "Copyright(c) 2013-2025 huangyuhui.",
        "Implementation-Version" to project.version.toString(),
        "Main-Class" to "org.jackhuang.hmcl.Main",
        "Multi-Release" to "true",
        "Add-Opens" to addOpens.joinToString(" "),
        "Enable-Native-Access" to "ALL-UNNAMED"
    )

    if (launcherExe != null) {
        into("assets") {
            from(file(launcherExe))
        }
    }

    doLast {
        val jarFile = archiveFile.get().asFile
        attachSignature(jarFile)
        createChecksum(jarFile)
    }
}

tasks.processResources {
    dependsOn(createPropertiesFile)

    into("assets/") {
        from(hmclPropertiesFile)
        from(embedResources)
    }
}

val patchLauncherIcon by tasks.registering {
    val extensions = listOf("exe", "sh")

    dependsOn(tasks.jar)

    val iconPngFile = layout.projectDirectory.file("src/main/resources/assets/img/icon.png").asFile
    val scriptFile = layout.projectDirectory.file("scripts/patch-exe-icon.py").asFile
    val outputDir = layout.buildDirectory.dir("patched")

    inputs.file(jarPath)
    inputs.file(iconPngFile).withPathSensitivity(PathSensitivity.ABSOLUTE)
    outputs.files(jarPath.map { jar ->
        extensions.map { outputDir.get().file("HMCLauncher.$it").asFile }
    })

    doLast {
        if (!iconPngFile.exists()) {
            logger.warn("icon.png not found at ${iconPngFile.path}, skipping icon patching")
            return@doLast
        }
        if (!scriptFile.exists()) {
            logger.warn("patch-exe-icon.py not found at ${scriptFile.path}, skipping icon patching")
            return@doLast
        }

        outputDir.get().asFile.mkdirs()

        ZipFile(jarPath.get()).use { zipFile ->
            for (extension in extensions) {
                val entry = zipFile.getEntry("assets/HMCLauncher.$extension")
                    ?: continue

                val tempFile = File(outputDir.get().asFile, "HMCLauncher_original.$extension")
                zipFile.getInputStream(entry).use { input ->
                    tempFile.outputStream().use { it.write(input.readAllBytes()) }
                }

                val outputFile = outputDir.get().file("HMCLauncher.$extension").asFile

                if (extension == "exe") {
                    val pb = ProcessBuilder(
                        "python3", scriptFile.absolutePath,
                        tempFile.absolutePath,
                        outputFile.absolutePath,
                        iconPngFile.absolutePath
                    )
                    pb.redirectErrorStream(true)
                    val process = pb.start()
                    val output = process.inputStream.reader().readText()
                    val exitCode = process.waitFor()

                    if (exitCode != 0) {
                        logger.warn("Icon patching failed (exit=$exitCode): $output")
                        tempFile.copyTo(outputFile, overwrite = true)
                    } else {
                        logger.lifecycle("Icon patched: ${outputFile.name}")
                    }
                } else {
                    tempFile.copyTo(outputFile, overwrite = true)
                }
            }
        }
    }
}

val makeExecutables by tasks.registering {
    val extensions = listOf("exe", "sh")

    dependsOn(tasks.jar, patchLauncherIcon)

    inputs.file(jarPath)
    outputs.files(jarPath.map { jar -> extensions.map { File(jar.parentFile, jar.nameWithoutExtension + '.' + it) } })

    val patchedDir = layout.buildDirectory.dir("patched").get().asFile

    doLast {
        val jarContent = jarPath.get().readBytes()

        ZipFile(jarPath.get()).use { zipFile ->
            for (extension in extensions) {
                val output = File(jarPath.get().parentFile, jarPath.get().nameWithoutExtension + '.' + extension)
                val patchedFile = File(patchedDir, "HMCLauncher.$extension")

                val exeBytes = if (patchedFile.exists()) {
                    patchedFile.readBytes()
                } else {
                    val entry = zipFile.getEntry("assets/HMCLauncher.$extension")
                        ?: throw GradleException("HMCLauncher.$extension not found")
                    zipFile.getInputStream(entry).readAllBytes()
                }

                output.outputStream().use { outputStream ->
                    outputStream.write(exeBytes)
                    outputStream.write(jarContent)
                }

                createChecksum(output)
            }
        }
    }
}

tasks.build {
    dependsOn(makeExecutables)
}

fun parseToolOptions(options: String?): MutableList<String> {
    if (options == null)
        return mutableListOf()

    val builder = StringBuilder()
    val result = mutableListOf<String>()

    var offset = 0

    loop@ while (offset < options.length) {
        val ch = options[offset]
        if (Character.isWhitespace(ch)) {
            if (builder.isNotEmpty()) {
                result += builder.toString()
                builder.clear()
            }

            while (offset < options.length && Character.isWhitespace(options[offset])) {
                offset++
            }

            continue@loop
        }

        if (ch == '\'' || ch == '"') {
            offset++

            while (offset < options.length) {
                val ch2 = options[offset++]
                if (ch2 != ch) {
                    builder.append(ch2)
                } else {
                    continue@loop
                }
            }

            throw GradleException("Unmatched quote in $options")
        }

        builder.append(ch)
        offset++
    }

    if (builder.isNotEmpty()) {
        result += builder.toString()
    }

    return result
}

// For IntelliJ IDEA
tasks.withType<JavaExec> {
    if (name != "run") {
        jvmArgs(addOpens.map { "--add-opens=$it=ALL-UNNAMED" })
//        if (javaVersion >= JavaVersion.VERSION_24) {
//            jvmArgs("--enable-native-access=ALL-UNNAMED")
//        }
    }
}

tasks.register<JavaExec>("run") {
    dependsOn(tasks.jar)

    group = "application"

    classpath = files(jarPath)
    workingDir = rootProject.rootDir

    val vmOptions = parseToolOptions(System.getenv("HMCL_JAVA_OPTS"))
    if (vmOptions.none { it.startsWith("-Dhmcl.offline.auth.restricted=") })
        vmOptions += "-Dhmcl.offline.auth.restricted=false"

    jvmArgs(vmOptions)

    val hmclJavaHome = System.getenv("HMCL_JAVA_HOME")
    if (hmclJavaHome != null) {
        this.executable(
            file(hmclJavaHome).resolve("bin")
                .resolve(if (System.getProperty("os.name").lowercase().startsWith("windows")) "java.exe" else "java")
        )
    }

    doFirst {
        logger.quiet("HMCL_JAVA_OPTS: {}", vmOptions)
        logger.quiet("HMCL_JAVA_HOME: {}", hmclJavaHome ?: System.getProperty("java.home"))
    }
}

// mcmod data

tasks.register<ParseModDataTask>("parseModData") {
    inputFile.set(layout.projectDirectory.file("mod.json"))
    outputFile.set(layout.projectDirectory.file("src/main/resources/assets/mod_data.txt"))
}

tasks.register<ParseModDataTask>("parseModPackData") {
    inputFile.set(layout.projectDirectory.file("modpack.json"))
    outputFile.set(layout.projectDirectory.file("src/main/resources/assets/modpack_data.txt"))
}
