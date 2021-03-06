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

package me.itzsomebody.radon.transformers.invokedynamic;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import me.itzsomebody.radon.methods.InvokeDynamicBSM;
import me.itzsomebody.radon.transformers.AbstractTransformer;
import me.itzsomebody.radon.utils.BytecodeUtils;
import me.itzsomebody.radon.utils.LoggerUtils;
import me.itzsomebody.radon.utils.StringUtils;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * Transformer that applies an InvokeDynamic obfuscation to field and
 * (virtual and static) method access.
 *
 * @author ItzSomebody.
 */
public class HeavyInvokeDynamic extends AbstractTransformer {
    // Magic numbers
    private int METHOD_INVOCATION = 1;
    private int FIELD_INVOCATION = 0;

    private int STATIC_INVOCATION = 1;
    private int VIRTUAL_INVOCATION = 0;

    private int VIRTUAL_GETTER = 0;
    private int STATIC_GETTER = 1;
    private int VIRTUAL_SETTER = 2;
    private int STATIC_SETTER = 3;

    /**
     * Applies obfuscation.
     */
    public void obfuscate() {
        AtomicInteger counter = new AtomicInteger();
        long current = System.currentTimeMillis();
        this.logStrings.add(LoggerUtils.stdOut("------------------------------------------------"));
        this.logStrings.add(LoggerUtils.stdOut("Started heavy invokedynamic transformer"));
        String[] bsmPath = new String[]{StringUtils.randomClass(classNames()), StringUtils.randomString(this.dictionary)};
        Handle bsmHandle = new Handle(H_INVOKESTATIC,
                bsmPath[0],
                bsmPath[1],
                "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;" +
                        "Ljava/lang/Object;Ljava/lang/Object;" +
                        "Ljava/lang/Object;Ljava/lang/Object;" +
                        "Ljava/lang/Object;)Ljava/lang/Object;",
                false);

        ArrayList<String> finals = new ArrayList<>();
        this.classNodes().forEach(classNode -> {
            classNode.fields.stream().filter(fieldNode -> Modifier.isFinal(fieldNode.access)).forEach(fieldNode -> {
                finals.add(classNode.name + '.' + fieldNode.name);
            });
        });
        this.classNodes().stream().filter(classNode -> !this.exempted(classNode.name, "InvokeDynamic")
                && classNode.version >= 51).forEach(classNode -> {
            classNode.methods.stream().filter(methodNode ->
                    !this.exempted(classNode.name + '.' + methodNode.name + methodNode.desc, "InvokeDynamic")
                            && hasInstructions(methodNode)).forEach(methodNode -> {
                for (AbstractInsnNode insn : methodNode.instructions.toArray()) {
                    if (this.methodSize(methodNode) > 60000) break;
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode methodInsnNode = (MethodInsnNode) insn;
                        boolean isStatic = (methodInsnNode.getOpcode() == INVOKESTATIC);
                        String newSig =
                                isStatic ? methodInsnNode.desc : methodInsnNode.desc.replace("(", "(Ljava/lang/Object;");
                        Type returnType = Type.getReturnType(methodInsnNode.desc);
                        switch (methodInsnNode.getOpcode()) {
                            case INVOKESTATIC: {// invokestatic opcode
                                InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
                                        StringUtils.randomString(this.dictionary),
                                        newSig,
                                        bsmHandle,
                                        this.METHOD_INVOCATION,
                                        this.STATIC_INVOCATION,
                                        this.encOwner(methodInsnNode.owner.replaceAll("/", ".")),
                                        this.encName(methodInsnNode.name),
                                        this.encDesc(methodInsnNode.desc));
                                methodNode.instructions.set(insn, indy);
                                if (returnType.getSort() == Type.ARRAY) {
                                    methodNode.instructions.insert(indy, new TypeInsnNode(CHECKCAST, returnType.getInternalName()));
                                }
                                counter.incrementAndGet();
                                break;
                            }
                            case INVOKEVIRTUAL: // invokevirtual opcode
                            case INVOKEINTERFACE: {// invokeinterface opcode
                                InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
                                        StringUtils.randomString(this.dictionary),
                                        newSig,
                                        bsmHandle,
                                        this.METHOD_INVOCATION,
                                        this.VIRTUAL_INVOCATION,
                                        this.encOwner(methodInsnNode.owner.replaceAll("/", ".")),
                                        this.encName(methodInsnNode.name),
                                        this.encDesc(methodInsnNode.desc));
                                methodNode.instructions.set(insn, indy);
                                if (returnType.getSort() == Type.ARRAY) {
                                    methodNode.instructions.insert(indy, new TypeInsnNode(CHECKCAST, returnType.getInternalName()));
                                }
                                counter.incrementAndGet();
                                break;
                            }
                            default: {
                                break;
                            }
                        }
                    } else if (insn instanceof FieldInsnNode) {
                        FieldInsnNode fieldInsnNode = (FieldInsnNode) insn;

                        if (finals.contains(fieldInsnNode.owner + '.' + fieldInsnNode.name)) {
                            continue;
                        }
                        boolean isStatic = (fieldInsnNode.getOpcode() == GETSTATIC
                                || fieldInsnNode.getOpcode() == PUTSTATIC);
                        boolean isSetter = (fieldInsnNode.getOpcode() == PUTFIELD
                                || fieldInsnNode.getOpcode() == PUTSTATIC);
                        String newSig
                                = (isSetter) ? "(" + fieldInsnNode.desc + ")V" : "()" + fieldInsnNode.desc;
                        if (!isStatic)
                            newSig = newSig.replace("(", "(Ljava/lang/Object;");
                        Type type = Type.getType(fieldInsnNode.desc);
                        String wrappedDescription = type.getClassName();
                        switch (fieldInsnNode.getOpcode()) {
                            case GETFIELD: {
                                Type returnType = Type.getType(fieldInsnNode.desc);
                                InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
                                        StringUtils.randomString(this.dictionary),
                                        newSig,
                                        bsmHandle,
                                        this.FIELD_INVOCATION,
                                        this.VIRTUAL_GETTER,
                                        this.encOwner(fieldInsnNode.owner.replaceAll("/", ".")),
                                        this.encName(fieldInsnNode.name),
                                        this.encDesc(wrappedDescription));
                                methodNode.instructions.set(insn, indy);
                                if (returnType.getSort() == Type.ARRAY) {
                                    methodNode.instructions.insert(indy, new TypeInsnNode(CHECKCAST, returnType.getInternalName()));
                                }
                                counter.incrementAndGet();
                                break;
                            }
                            case GETSTATIC: {
                                Type returnType = Type.getType(fieldInsnNode.desc);
                                InvokeDynamicInsnNode indy = new InvokeDynamicInsnNode(
                                        StringUtils.randomString(this.dictionary),
                                        newSig,
                                        bsmHandle,
                                        this.FIELD_INVOCATION,
                                        this.STATIC_GETTER,
                                        this.encOwner(fieldInsnNode.owner.replaceAll("/", ".")),
                                        this.encName(fieldInsnNode.name),
                                        this.encDesc(wrappedDescription));
                                methodNode.instructions.set(insn, indy);
                                if (returnType.getSort() == Type.ARRAY) {
                                    methodNode.instructions.insert(indy, new TypeInsnNode(CHECKCAST, returnType.getInternalName()));
                                }
                                counter.incrementAndGet();
                                break;
                            }
                            case PUTFIELD: {
                                methodNode.instructions.set(insn, new InvokeDynamicInsnNode(
                                        StringUtils.randomString(this.dictionary),
                                        newSig,
                                        bsmHandle,
                                        this.FIELD_INVOCATION,
                                        this.VIRTUAL_SETTER,
                                        this.encOwner(fieldInsnNode.owner.replaceAll("/", ".")),
                                        this.encName(fieldInsnNode.name),
                                        this.encDesc(wrappedDescription)));
                                counter.incrementAndGet();
                                break;
                            }
                            case PUTSTATIC: {
                                methodNode.instructions.set(insn, new InvokeDynamicInsnNode(
                                        StringUtils.randomString(this.dictionary),
                                        newSig,
                                        bsmHandle,
                                        this.FIELD_INVOCATION,
                                        this.STATIC_SETTER,
                                        this.encOwner(fieldInsnNode.owner.replaceAll("/", ".")),
                                        this.encName(fieldInsnNode.name),
                                        this.encDesc(wrappedDescription)));
                                counter.incrementAndGet();
                                break;
                            }
                            default: {
                                break;
                            }
                        }
                    }
                }
            });
        });

        this.classNodes().stream().filter(classNode -> classNode.name.equals(bsmPath[0])).forEach(classNode -> {
            classNode.methods.add(InvokeDynamicBSM.heavyBSM(bsmPath[1], classNode.name));
            classNode.access = BytecodeUtils.accessFixer(classNode.access);
        });
        this.logStrings.add(LoggerUtils.stdOut("Hid " + counter + " field and/or method accesses with invokedynamics."));
        this.logStrings.add(LoggerUtils.stdOut("Finished. [" + tookThisLong(current) + "ms]"));
    }

    /**
     * Returns string with a simple encryption.
     *
     * @param msg inputed string to be encrypted.
     * @return string with a simple encryption.
     */
    private String encOwner(String msg) {
        char[] chars = msg.toCharArray();
        char[] encChars = new char[chars.length];

        for (int i = 0; i < chars.length; i++) {
            encChars[i] = (char) (chars[i] ^ 4382);
        }

        return new String(encChars);
    }

    /**
     * Returns string with a simple encryption.
     *
     * @param msg inputed string to be encrypted.
     * @return string with a simple encryption.
     */
    private String encName(String msg) {
        char[] chars = msg.toCharArray();
        char[] encChars = new char[chars.length];

        for (int i = 0; i < chars.length; i++) {
            encChars[i] = (char) (chars[i] ^ 3940);
        }

        return new String(encChars);
    }

    /**
     * Returns string with a simple encryption.
     *
     * @param msg inputed string to be encrypted.
     * @return string with a simple encryption.
     */
    private String encDesc(String msg) {
        char[] chars = msg.toCharArray();
        char[] encChars = new char[chars.length];

        for (int i = 0; i < chars.length; i++) {
            encChars[i] = (char) (chars[i] ^ 5739);
        }

        return new String(encChars);
    }
}