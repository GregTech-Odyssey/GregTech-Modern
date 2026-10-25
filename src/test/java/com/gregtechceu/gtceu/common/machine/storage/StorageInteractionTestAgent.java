package com.gregtechceu.gtceu.common.machine.storage;

import net.minecraftforge.fml.common.asm.CapabilityTokenSubclass;

import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;

/** Apply Forge's real capability token transformer when checking containers without ModLauncher. */
public final class StorageInteractionTestAgent implements ClassFileTransformer {

    public static void premain(String arguments, Instrumentation instrumentation) {
        instrumentation.addTransformer(new StorageInteractionTestAgent());
    }

    @Override
    public byte[] transform(ClassLoader loader, String name, Class<?> redefining, ProtectionDomain domain, byte[] bytes) {
        if (!name.startsWith("net/minecraftforge/common/capabilities/")) return null;
        var node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        var transformer = new CapabilityTokenSubclass();
        int flags = transformer.processClassWithFlags(ILaunchPluginService.Phase.AFTER, node, Type.getObjectType(name), "test");
        if (flags == ILaunchPluginService.ComputeFlags.NO_REWRITE) return null;
        var writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
