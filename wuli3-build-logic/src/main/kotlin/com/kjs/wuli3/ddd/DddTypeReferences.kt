package com.kjs.wuli3.ddd

import java.io.File
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.signature.SignatureReader
import org.objectweb.asm.signature.SignatureVisitor

/** 补齐 ArchUnit 尚未建模的强转、数组创建、异常捕获及被调用成员描述符中的类型引用。 */
internal object DddTypeReferences {
    /** 仅读取类、成员描述符常量及声明签名，不将普通字符串视为类型。 */
    fun collect(directories: Collection<File>): Map<String, Set<String>> =
        directories.filter(File::exists).flatMap { directory ->
            directory.walkTopDown().filter { it.extension == "class" && it.name != "module-info.class" }
                .map { file ->
                    val reader = ClassReader(file.readBytes())
                    val characters = CharArray(reader.maxStringLength)
                    val targets = mutableSetOf<String>()
                    for (index in 1 until reader.itemCount) {
                        val offset = reader.getItem(index)
                        // long 和 double 占两个常量池槽，第二槽没有内容。
                        if (offset == 0) continue
                        when (reader.readByte(offset - 1)) {
                            // CONSTANT_Class 与 CONSTANT_NameAndType。
                            7 -> collectType(reader.readConst(index, characters) as Type, targets)
                            12 -> collectType(Type.getType(reader.readUTF8(offset + 2, characters)), targets)
                        }
                    }
                    collectSignatures(reader, targets)
                    reader.className.replace('/', '.') to targets.toSet()
                }.toList()
        }.toMap()

    /** 规范化数组、方法描述符，仅保留对象类型。 */
    private fun collectType(type: Type, targets: MutableSet<String>) {
        when (type.sort) {
            Type.OBJECT -> targets.add(type.className)
            Type.ARRAY -> collectType(type.elementType, targets)
            Type.METHOD -> {
                type.argumentTypes.forEach { collectType(it, targets) }
                collectType(type.returnType, targets)
            }
        }
    }

    /** 补充泛型数组等未进入 ArchUnit 依赖集合的声明签名，不扫描代码或新增注解规则。 */
    private fun collectSignatures(reader: ClassReader, targets: MutableSet<String>) {
        reader.accept(object : ClassVisitor(Opcodes.ASM9) {
            override fun visit(version: Int, access: Int, name: String, signature: String?, superName: String?, interfaces: Array<out String>?) {
                signature?.let { SignatureReader(it).accept(SignatureTypes(targets)) }
            }

            override fun visitField(access: Int, name: String, descriptor: String, signature: String?, value: Any?): FieldVisitor? {
                signature?.let { SignatureReader(it).acceptType(SignatureTypes(targets)) }
                return null
            }

            override fun visitMethod(access: Int, name: String, descriptor: String, signature: String?, exceptions: Array<out String>?): MethodVisitor? {
                signature?.let { SignatureReader(it).accept(SignatureTypes(targets)) }
                return null
            }
        }, ClassReader.SKIP_CODE or ClassReader.SKIP_DEBUG or ClassReader.SKIP_FRAMES)
    }

    /** 类型参数单独遍历，避免其类名覆盖外层泛型内部类的二进制名称。 */
    private class SignatureTypes(private val targets: MutableSet<String>) : SignatureVisitor(Opcodes.ASM9) {
        private var enclosingType = ""

        override fun visitClassType(name: String) {
            enclosingType = name
            targets.add(name.replace('/', '.'))
        }

        override fun visitInnerClassType(name: String) {
            enclosingType += "\$$name"
            targets.add(enclosingType.replace('/', '.'))
        }

        override fun visitTypeArgument(wildcard: Char): SignatureVisitor = SignatureTypes(targets)
    }
}
