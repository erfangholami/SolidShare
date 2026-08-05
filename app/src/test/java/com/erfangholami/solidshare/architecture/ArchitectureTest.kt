package com.erfangholami.solidshare.architecture

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Executable architecture rules for `app/`.
 *
 * Each rule compares the violations found in `src/main/java` against a **baseline** of the ones
 * known when the rule was written. The comparison is an equality, not a subset, so the build
 * fails both when a new violation appears *and* when a baselined one is fixed without shrinking
 * the baseline. Baselines only ever get smaller; the modularization plan
 * (`documents/MODULARIZATION_PLAN.md`) says which phase empties each one.
 */
class ArchitectureTest {

    @Test
    fun `the data layer does not reach up into workers`() {
        val violations = sourceFiles()
            .filter { it.packageOf().startsWith("data.") }
            .filter { it.readText().contains("import $ROOT.worker.") }
            .map { it.relativePath() }
            .toSortedSet()

        assertEquals(
            "data/ must not schedule work directly — it asks OutboxTrigger for a drain and " +
                "worker/ decides how that happens (plan §6.3e).",
            emptySet<String>(),
            violations,
        )
    }

    @Test
    fun `generic presentation code does not name a specific data module`() {
        val violations = sourceFiles()
            .filter { file ->
                val pkg = file.packageOf()
                pkg.startsWith("presentation.") && DATA_MODULE_PACKAGES.none { pkg.startsWith(it) }
            }
            .filter { file ->
                DATA_MODULE_PACKAGES.any { file.readText().contains("import $ROOT.$it") }
            }
            .map { it.relativePath() }
            .toSortedSet()

        assertEquals(
            "generic screens must reach data modules through a registry, not by import " +
                "(plan §6.3c–d): SharedEntityRegistry for typed rows and hub cards, " +
                "ReceiverPickerRegistry for the receiver sheet, NavGraphRegistry for routes.",
            emptySet<String>(),
            violations,
        )
    }

    @Test
    fun `generic presentation code does not reach into a module's data layer`() {
        val violations = sourceFiles()
            .filter { file ->
                val pkg = file.packageOf()
                pkg.startsWith("presentation.") && DATA_MODULE_PACKAGES.none { pkg.startsWith(it) }
            }
            .filter { file ->
                DATA_MODULE_REPO_PACKAGES.any { file.readText().contains("import $ROOT.$it.") }
            }
            .map { it.relativePath() }
            .toSortedSet()

        assertEquals(
            "generic screens go through the registries; the QR-profile add-to-contacts flow " +
                "is the one remaining direct dependency, pending an add-to-module seam.",
            sortedSetOf("presentation/sharing/PublicProfileViewModel.kt"),
            violations,
        )
    }

    @Test
    fun `no screen builds its own message out of a throwable`() {
        val violations = sourceFiles()
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if (RAW_MESSAGE_FALLBACK.containsMatchIn(line)) {
                        "${file.relativePath()}:${index + 1}"
                    } else {
                        null
                    }
                }
            }
            .toSortedSet()

        assertEquals(
            "`something.message ?: fallback` puts a server or library diagnostic in front of a " +
                "user. Classify the throwable with ErrorPresenter instead (documents/ERRORS.md).",
            emptySet<String>(),
            violations,
        )
    }

    @Test
    fun `library error types stop at the data layer`() {
        val violations = sourceFiles()
            .filter { file -> UI_PACKAGES.any { file.packageOf().startsWith(it) } }
            .filter { file ->
                LIBRARY_ERROR_TYPES.any { file.readText().contains("import $LIBRARY_ROOT.$it") }
            }
            .map { it.relativePath() }
            .toSortedSet()

        assertEquals(
            "screens, workers and the sync adapter speak AppError, never SolidError or " +
                "SharingException — repositories throw and AppErrorMapper classifies " +
                "(documents/ERRORS.md).",
            emptySet<String>(),
            violations,
        )
    }

    @Test
    fun `production code carries no comments except KDoc`() {
        val violations = sourceFiles()
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val trimmed = line.trimStart()
                    val isComment = trimmed.startsWith("//") ||
                        (trimmed.startsWith("/*") && !trimmed.startsWith("/**"))
                    if (isComment) "${file.relativePath()}:${index + 1}" else null
                }
            }
            .toSortedSet()

        assertEquals(
            "app/ code is comment-free; explain intent in names, or in KDoc on a public " +
                "declaration (plan §2.7).",
            emptySet<String>(),
            violations,
        )
    }

    private fun sourceFiles(): List<File> =
        SOURCE_ROOT.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun File.relativePath(): String = relativeTo(PACKAGE_ROOT).path

    private fun File.packageOf(): String = relativeTo(PACKAGE_ROOT).parentFile?.path
        ?.replace(File.separatorChar, '.')
        .orEmpty()

    private companion object {
        const val ROOT = "com.erfangholami.solidshare"

        const val LIBRARY_ROOT = "com.erfangholami.androidsolidservices"

        val RAW_MESSAGE_FALLBACK = Regex("""\.message\s*\?:""")

        val UI_PACKAGES = listOf("presentation", "worker", "sync")

        val LIBRARY_ERROR_TYPES = listOf(
            "shared.result.SolidError",
            "shared.result.SolidResultException",
            "api.exceptions.SharingException",
        )

        val DATA_MODULE_PACKAGES = listOf("presentation.contacts", "presentation.wallet")
        val DATA_MODULE_REPO_PACKAGES = listOf("data.repo.contacts", "data.repo.tickets")

        val SOURCE_ROOT: File = listOf(File("src/main/java"), File("app/src/main/java"))
            .first { it.isDirectory }

        val PACKAGE_ROOT: File = File(SOURCE_ROOT, ROOT.replace('.', File.separatorChar))
    }
}
