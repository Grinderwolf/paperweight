/*
 * paperweight is a Gradle plugin for the PaperMC project.
 *
 * Copyright (c) 2021 Kyle Wood (DemonWav)
 *                    Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation;
 * version 2.1 only, no later versions.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package io.papermc.paperweight.core.taskcontainers

import io.papermc.paperweight.core.ext
import io.papermc.paperweight.core.extension.PaperweightCoreExtension
import io.papermc.paperweight.tasks.*
import io.papermc.paperweight.tasks.patchremap.ApplyAccessTransform
import io.papermc.paperweight.util.Constants
import io.papermc.paperweight.util.cache
import io.papermc.paperweight.util.path
import io.papermc.paperweight.util.registering
import io.papermc.paperweight.util.set
import java.nio.file.Path
import kotlin.io.path.exists
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.*

@Suppress("MemberVisibilityCanBePrivate")
open class AllTasks(
    project: Project,
    tasks: TaskContainer = project.tasks,
    cache: Path = project.layout.cache,
    extension: PaperweightCoreExtension = project.ext,
) : SpigotTasks(project) {

    val applyMergedAt by tasks.registering<ApplyAccessTransform> {
        inputJar.set(fixJar.flatMap { it.outputJar })
        atFile.set(mergeGeneratedAts.flatMap { it.outputFile })
    }

    val copyResources by tasks.registering<CopyResources> {
        inputJar.set(applyMergedAt.flatMap { it.outputJar })
        vanillaJar.set(downloadServerJar.flatMap { it.outputJar })
        includes.set(listOf("/data/**", "/assets/**", "version.json", "yggdrasil_session_pubkey.der", "pack.mcmeta"))

        outputJar.set(cache.resolve(Constants.FINAL_REMAPPED_JAR))
    }

    val decompileJar by tasks.registering<RunForgeFlower> {
        executable.from(project.configurations.named(Constants.DECOMPILER_CONFIG))

        inputJar.set(copyResources.flatMap { it.outputJar })
        libraries.set(downloadMcLibraries.flatMap { it.outputDir })
    }

    val applyApiPatches by tasks.registering<ApplyGitPatches> {
        group = "paper"
        description = "Setup the Paper-API project"
        branch.set("HEAD")
        upstreamBranch.set("upstream")
        upstream.set(patchSpigotApi.flatMap { it.outputDir })
        patchDir.set(extension.paper.spigotApiPatchDir)
        printOutput.set(true)

        outputDir.set(extension.paper.paperApiDir)
    }

    @Suppress("DuplicatedCode")
    val applyServerPatches by tasks.registering<ApplyPaperPatches> {
        group = "paper"
        description = "Setup the Paper-Server project"
        patchDir.set(extension.paper.spigotServerPatchDir)
        remappedSource.set(remapSpigotSources.flatMap { it.sourcesOutputZip })
        remappedTests.set(remapSpigotSources.flatMap { it.testsOutputZip })
        caseOnlyClassNameChanges.set(cleanupMappings.flatMap { it.caseOnlyNameChanges })
        spigotServerDir.set(patchSpigotServer.flatMap { it.outputDir })
        sourceMcDevJar.set(decompileJar.flatMap { it.outputJar })
        mcLibrariesDir.set(downloadMcLibraries.flatMap { it.sourcesOutputDir })
        libraryImports.set(extension.paper.libraryClassImports)
        mcdevImports.set(extension.paper.mcdevClassImports.flatMap { project.provider { if (it.path.exists()) it else null } })

        outputDir.set(extension.paper.paperServerDir)
    }

    val applyPatches by tasks.registering<Task> {
        group = "paper"
        description = "Set up the Paper development environment"
        dependsOn(applyApiPatches, applyServerPatches)
    }

    val rebuildApiPatches by tasks.registering<RebuildPaperPatches> {
        group = "paper"
        description = "Rebuilds patches to api"
        inputDir.set(extension.paper.paperApiDir)

        patchDir.set(extension.paper.spigotApiPatchDir)
    }

    val rebuildServerPatches by tasks.registering<RebuildPaperPatches> {
        group = "paper"
        description = "Rebuilds patches to server"
        inputDir.set(extension.paper.paperServerDir)
        server.set(true)

        patchDir.set(extension.paper.spigotServerPatchDir)
    }

    @Suppress("unused")
    val rebuildPatches by tasks.registering<Task> {
        group = "paper"
        description = "Rebuilds patches to api and server"
        dependsOn(rebuildApiPatches, rebuildServerPatches)
    }

    @Suppress("unused")
    val generateDevelopmentBundle by tasks.registering<GenerateDevBundle> {
        // dependsOn(applyPatches)

        decompiledJar.set(decompileJar.flatMap { it.outputJar }.get())
        sourceDir.set(extension.paper.paperServerDir.map { it.dir("src/main/java") }.get())
        minecraftVersion.set(extension.minecraftVersion)

        buildDataDir.set(extension.craftBukkit.buildDataDir)
        spigotClassMappingsFile.set(extension.craftBukkit.mappingsDir.file(buildDataInfo.map { it.classMappings }))
        spigotMemberMappingsFile.set(extension.craftBukkit.mappingsDir.file(buildDataInfo.map { it.memberMappings }))
        spigotAtFile.set(extension.craftBukkit.mappingsDir.file(buildDataInfo.map { it.accessTransforms }))

        paramMappingsUrl.set(extension.paramMappingsRepo)
        decompilerUrl.set(extension.paramMappingsRepo)
        remapperUrl.set(extension.paramMappingsRepo)

        paramMappingsConfig.set(project.configurations.named(Constants.PARAM_MAPPINGS_CONFIG))
        decompilerConfig.set(project.configurations.named(Constants.DECOMPILER_CONFIG))
        remapperConfig.set(project.configurations.named(Constants.REMAPPER_CONFIG))

        additionalSpigotClassMappingsFile.set(extension.paper.additionalSpigotClassMappings)
        additionalSpigotMemberMappingsFile.set(extension.paper.additionalSpigotMemberMappings)
        mappingsPatchFile.set(extension.paper.mappingsPatch)

        devBundleFile.set(project.layout.buildDirectory.map { it.file("libs/paperDevBundle-${project.version}.zip") })
    }
}
