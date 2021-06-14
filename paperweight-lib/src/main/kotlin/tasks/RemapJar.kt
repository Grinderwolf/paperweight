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

package io.papermc.paperweight.tasks

import io.papermc.paperweight.util.Constants.paperTaskOutput
import io.papermc.paperweight.util.cache
import io.papermc.paperweight.util.defaultOutput
import io.papermc.paperweight.util.ensureDeleted
import io.papermc.paperweight.util.ensureParentExists
import io.papermc.paperweight.util.path
import io.papermc.paperweight.util.runJar
import kotlin.io.path.absolutePathString
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

fun tinyRemapperArgsList(): List<String> {
    return listOf(
        "{input}",
        "{output}",
        "{mappings}",
        "{from}",
        "{to}",
        "--fixpackageaccess",
        "--renameinvalidlocals",
        "--threads=1",
        "--rebuildsourcefilenames"
    )
}

fun createTinyRemapperArgs(input: String, output: String, mappings: String, from: String, to: String): List<String> {
    return tinyRemapperArgsList().map {
        when (it) {
            "{input}" -> input
            "{output}" -> output
            "{mappings}" -> mappings
            "{from}" -> from
            "{to}" -> to
            else -> it
        }
    }
}

abstract class RemapJar : BaseTask() {

    @get:InputFile
    abstract val inputJar: RegularFileProperty

    @get:InputFile
    abstract val mappingsFile: RegularFileProperty

    @get:Input
    abstract val fromNamespace: Property<String>

    @get:Input
    abstract val toNamespace: Property<String>

    @get:Internal
    abstract val jvmargs: ListProperty<String>

    @get:Classpath
    abstract val remapper: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    override fun init() {
        outputJar.convention(defaultOutput())
        jvmargs.convention(listOf("-Xmx1G"))
    }

    @TaskAction
    fun run() {
        val logFile = layout.cache.resolve(paperTaskOutput("log"))
        ensureDeleted(logFile)

        val args = createTinyRemapperArgs(
            inputJar.path.absolutePathString(),
            outputJar.path.absolutePathString(),
            mappingsFile.path.absolutePathString(),
            fromNamespace.get(),
            toNamespace.get(),
        )

        ensureParentExists(logFile)
        runJar(remapper, layout.cache, logFile, jvmArgs = jvmargs.get(), args = args.toTypedArray())
    }
}
