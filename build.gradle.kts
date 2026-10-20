plugins {
    id("java-library")
    id("maven-publish")
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    // PlaceholderAPI
    maven("https://repo.extendedclip.com/content/repositories/placeholderapi/")
    // LuckPerms (optionale Abhängigkeit)
    maven("https://repo.lucko.me/")
    // JitPack (fuer VaultAPI etc.)
    maven("https://jitpack.io")
    // SimpleVoiceChat API
    maven("https://maven.maxhenkel.de/repository/public")
    // LiteBans API
    maven("https://repo.codemc.org/repository/maven-public/")
    // Geyser / Floodgate API
    maven("https://repo.opencollab.dev/main/")
}

dependencies {
    // Paper hat mit MC 26.x das alte "-R0.1-SNAPSHOT"-Schema durch <mcversion>.build.<build>
    // ersetzt. "26.2.build.+" lässt Gradle immer den neuesten 26.2-Build auflösen
    // (entspricht dem bisherigen SNAPSHOT-Verhalten).
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")

    // Optionale Abhängigkeiten (soft-depend, siehe plugin.yml)
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7") {
        exclude(group = "org.bukkit", module = "bukkit")
    }
    compileOnly("net.dmulloy2:ProtocolLib:5.4.0")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.24")
    compileOnly("litebans:api:0.6.1")
    compileOnly("org.geysermc.floodgate:api:2.2.3-SNAPSHOT")

    implementation("com.zaxxer:HikariCP:5.1.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    // waffle-jna (nur für Windows-GSSAPI-Login, hier ungenutzt) zieht net.java.dev.jna:jna-platform
    // mit rein. Dessen riesiges Jar (Zip64) lässt shadowJar mit
    // "Could not add zip entry ... WinGDI$BITMAP.class" fehlschlagen. Offizieller
    // MariaDB-Fix laut deren Doku: waffle-jna ausschließen, Funktionalität bleibt identisch,
    // da im Plugin ausschließlich normale User/Passwort-Verbindungen genutzt werden.
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.1") {
        exclude(group = "com.github.waffle", module = "waffle-jna")
    }
}

java {
    // MC/Paper 26.x läuft nur noch auf Java 25 (siehe Startfehler UnsupportedClassVersionError
    // bei älteren JDKs). Der Toolchain-Wechsel ändert nichts am Plugin-Code selbst.
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile> {
    // Verhindert, dass javac für String-Konkatenation "invokedynamic"-Bytecode
    // (StringConcatFactory) erzeugt. Das Shadow-Plugin (com.gradleup.shadow) hat
    // beim Relozieren von Klassen mit dieser Art von Bytecode einen bekannten Bug
    // (MissingMethodException: Remapper.mapValue ... Handle ...). Mit diesem Flag
    // kompiliert javac stattdessen klassische StringBuilder-Konkatenation.
    options.compilerArgs.add("-XDstringConcat=inline")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifact(tasks.named("shadowJar"))
            groupId = project.group.toString()
            artifactId = "Ztone"
            version = project.version.toString()
        }
    }
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("26.2")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    processResources {
        val props = mapOf("version" to version, "description" to project.description)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    jar {
        enabled = false
    }

    shadowJar {
        archiveClassifier.set("")

        // Eingebundene Bibliotheken relozieren, um Klassenkonflikte mit anderen
        // Plugins zu vermeiden, die ebenfalls HikariCP nutzen könnten.
        // Hinweis: org.sqlite wird bewusst NICHT reloziert, da der SQLite-JDBC-Treiber
        // native Bibliotheken (JNI) lädt, deren Ladepfad beim Relozieren brechen kann.
        relocate("com.zaxxer.hikari", "de.Z7534.ztone.libs.hikari")
    }

    build {
        dependsOn(shadowJar)
    }
}
