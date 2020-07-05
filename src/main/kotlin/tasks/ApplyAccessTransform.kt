package io.papermc.paperweight.tasks

import io.papermc.paperweight.util.defaultOutput
import io.papermc.paperweight.util.ensureDeleted
import io.papermc.paperweight.util.ensureParentExists
import io.papermc.paperweight.util.file
import io.papermc.paperweight.util.orNull
import org.cadixdev.at.AccessChange
import org.cadixdev.at.AccessTransform
import org.cadixdev.at.AccessTransformSet
import org.cadixdev.at.ModifierChange
import org.cadixdev.at.io.AccessTransformFormats
import org.cadixdev.atlas.Atlas
import org.cadixdev.bombe.jar.JarClassEntry
import org.cadixdev.bombe.jar.JarEntryTransformer
import org.cadixdev.bombe.type.signature.MethodSignature
import org.cadixdev.lorenz.io.MappingFormats
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

open class ApplyAccessTransform : DefaultTask() {

    @InputFile
    val inputJar: RegularFileProperty = project.objects.fileProperty()

    @InputFile
    val atFile: RegularFileProperty = project.objects.fileProperty()

    @InputFile
    val mapping: RegularFileProperty = project.objects.fileProperty()

    @OutputFile
    val outputJar: RegularFileProperty = defaultOutput()

    @TaskAction
    fun run() {
        ensureParentExists(outputJar.file)
        ensureDeleted(outputJar.file)

        val at = AccessTransformFormats.FML.read(atFile.file.toPath())
        val mappings = MappingFormats.TSRG.createReader(mapping.file.toPath()).use { it.read() }
        val remappedAt = at.remap(mappings)

        Atlas().apply {
            install {
                AtJarEntryTransformer(remappedAt)
            }
            run(inputJar.file.toPath(), outputJar.file.toPath())
        }
    }
}

class AtJarEntryTransformer(private val at: AccessTransformSet) : JarEntryTransformer {
    override fun transform(entry: JarClassEntry): JarClassEntry {
        val reader = ClassReader(entry.contents)
        val writer = ClassWriter(reader, 0)
        reader.accept(AccessTransformerVisitor(at, writer), 0)
        return JarClassEntry(entry.name, entry.time, writer.toByteArray())
    }
}

class AccessTransformerVisitor(
    private val at: AccessTransformSet,
    writer: ClassWriter
) : ClassVisitor(Opcodes.ASM7, writer) {

    var classTransform: AccessTransformSet.Class? = null

    override fun visit(
        version: Int,
        access: Int,
        name: String,
        signature: String?,
        superName: String?,
        interfaces: Array<out String>?
    ) {
        classTransform = at.getClass(name).orNull
        super.visit(version, classTransform?.get().apply(access), name, signature, superName, interfaces)
    }

    override fun visitField(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        value: Any?
    ): FieldVisitor {
        return super.visitField(classTransform?.getField(name).apply(access), name, descriptor, signature, value)
    }

    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        val newAccess = classTransform?.getMethod(MethodSignature.of(name, descriptor)).apply(access)
        return super.visitMethod(newAccess, name, descriptor, signature, exceptions)
    }

    override fun visitInnerClass(name: String, outerName: String?, innerName: String?, access: Int) {
        super.visitInnerClass(name, outerName, innerName, at.getClass(name).orNull?.get().apply(access))
    }
}

private const val RESET_ACCESS: Int = (Opcodes.ACC_PUBLIC or Opcodes.ACC_PRIVATE or Opcodes.ACC_PROTECTED).inv()
fun AccessTransform?.apply(currentModifier: Int): Int {
    if (this == null) {
        return currentModifier
    }
    var value = currentModifier
    if (this.access != AccessChange.NONE) {
        value = value and RESET_ACCESS
        value = value or this.access.modifier
    }
    when (this.final) {
        ModifierChange.REMOVE -> {
            value = value and Opcodes.ACC_FINAL.inv()
        }
        ModifierChange.ADD -> {
            value = value or Opcodes.ACC_FINAL
        }
        else -> {}
    }
    return value
}
