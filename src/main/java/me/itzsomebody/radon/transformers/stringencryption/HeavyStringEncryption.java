/*
 * Copyright (C) 2018 ItzSomebody
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package me.itzsomebody.radon.transformers.stringencryption;

import java.util.concurrent.atomic.AtomicInteger;
import me.itzsomebody.radon.classes.StringDecryptionClass;
import me.itzsomebody.radon.transformers.AbstractTransformer;
import me.itzsomebody.radon.utils.BytecodeUtils;
import me.itzsomebody.radon.utils.LoggerUtils;
import me.itzsomebody.radon.utils.NumberUtils;
import me.itzsomebody.radon.utils.StringUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

public class HeavyStringEncryption extends AbstractTransformer {
    /**
     * Indication to not encrypt strings containing Spigot placeholders
     * (%%__USER__%%, %%__RESOURCE__%% and %%__NONCE__%%).
     */
    private boolean spigotMode;

    /**
     * Constructor used to create a {@link HeavyStringEncryption} object.
     *
     * @param spigotMode indication to not encrypt strings containing Spigot
     *                   placeholders (%%__USER__%%, %%__RESOURCE__%% and
     *                   %%__NONCE__%%).
     */
    public HeavyStringEncryption(boolean spigotMode) {
        this.spigotMode = spigotMode;
    }

    /**
     * Applies obfuscation.
     */
    public void obfuscate() {
        AtomicInteger counter = new AtomicInteger();
        long current = System.currentTimeMillis();
        this.logStrings.add(LoggerUtils.stdOut("------------------------------------------------"));
        this.logStrings.add(LoggerUtils.stdOut("Started heavy string encryption transformer"));
        String[] decryptorPath = new String[]{StringUtils.randomClassName(classNames(), this.dictionary), StringUtils.randomString(this.dictionary)};
        this.classNodes().stream().filter(classNode -> !this.exempted(classNode.name, "StringEncryption")).forEach(classNode ->
                classNode.methods.stream().filter(methodNode ->
                        !this.exempted(classNode.name + '.' + methodNode.name + methodNode.name, "StringEncryption")
                                && hasInstructions(methodNode)).forEach(methodNode -> {
                    for (AbstractInsnNode insn : methodNode.instructions.toArray()) {
                        if (methodSize(methodNode) > 60000) break;
                        if (insn instanceof LdcInsnNode) {
                            Object cst = ((LdcInsnNode) insn).cst;

                            if (cst instanceof String) {
                                if (this.spigotMode &&
                                        ((String) cst).contains("%%__USER__%%")
                                        || ((String) cst).contains("%%__RESOURCE__%%")
                                        || ((String) cst).contains("%%__NONCE__%%"))
                                    continue;

                                int key1 = decryptorPath[0].replace("/", ".").hashCode();
                                int key2 = "<clinit>".hashCode();
                                int key3 = classNode.name.replace("/", ".").hashCode();
                                int key4 = methodNode.name.hashCode();
                                int key5 = NumberUtils.getRandomInt();
                                ((LdcInsnNode) insn).cst =
                                        StringUtils.heavyEncrypt(((String) ((LdcInsnNode) insn).cst), key1, key2, key3, key4, key5);
                                methodNode.instructions.insert(insn,
                                        new MethodInsnNode(Opcodes.INVOKESTATIC,
                                                decryptorPath[0], decryptorPath[1],
                                                "(Ljava/lang/Object;Ljava/lang/Object;I)Ljava/lang/String;",
                                                false));
                                methodNode.instructions.insert(insn, BytecodeUtils.getNumberInsn(key5));
                                methodNode.instructions.insert(insn, new InsnNode(SWAP));
                                methodNode.instructions.insert(insn, new InsnNode(POP));
                                methodNode.instructions.insert(insn, new InsnNode(DUP_X1));
                                methodNode.instructions.insert(insn, new InsnNode(ACONST_NULL));
                                counter.incrementAndGet();
                            }
                        }
                    }
                })
        );

        ClassNode decryptor = StringDecryptionClass.getHeavyDecrypt(decryptorPath[0], decryptorPath[1],
                StringUtils.randomString(this.dictionary), StringUtils.randomString(this.dictionary),
                StringUtils.randomString(this.dictionary), StringUtils.randomString(this.dictionary),
                StringUtils.randomString(this.dictionary), StringUtils.randomString(this.dictionary));
        this.getClassMap().put(decryptor.name, decryptor);
        logStrings.add(LoggerUtils.stdOut("Encrypted " + counter + " strings."));
        logStrings.add(LoggerUtils.stdOut("Finished. [" + tookThisLong(current) + "ms]"));
    }
}
