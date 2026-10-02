package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblockpro.MachineProtocol;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class StructureHosts {

    @Nullable
    private static Reference2ObjectOpenHashMap<MachineProtocol, List<MultiblockMachineDefinition>> index;

    private StructureHosts() {}

    public static List<MultiblockMachineDefinition> of(MachineProtocol protocol) {
        if (index == null) index = build();
        var hosts = index.get(protocol);
        return hosts == null ? Collections.emptyList() : hosts;
    }

    private static Reference2ObjectOpenHashMap<MachineProtocol, List<MultiblockMachineDefinition>> build() {
        var map = new Reference2ObjectOpenHashMap<MachineProtocol, List<MultiblockMachineDefinition>>();
        for (var machine : GTRegistries.MACHINES) {
            if (!(machine instanceof MultiblockMachineDefinition definition)) continue;
            var structure = definition.displayStructure();
            if (structure == null) continue;
            for (var node : structure.tree().nodes()) {
                var protocol = node.protocol();
                if (protocol == null) continue;
                var hosts = map.computeIfAbsent(protocol, p -> new ArrayList<>());
                if (!hosts.contains(definition)) hosts.add(definition);
            }
        }
        return map;
    }
}
