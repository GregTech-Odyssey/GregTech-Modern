package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.core.BlockPos;

import com.gto.datasynclib.datastream.DataComponentKey;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMaps;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Assembly {

    public static final DataComponentKey<Assembly> KEY = DataComponentKey.createNoCodec("MultiblockProAssembly");

    private final Reference2IntMap<ParamKey> params;
    @Getter
    private final int optionalFormed;
    @Getter
    private final int optionalMissing;

    private final Reference2ObjectMap<MachineProtocol, List<MetaMachine>> machines;
    private final Reference2ObjectMap<MachineProtocol, List<BlockPos>> ports;

    Assembly(Reference2IntOpenHashMap<ParamKey> params, int optionalFormed, int optionalMissing, List<MachineProtocol> protocols,
             List<MetaMachine> machineList, List<MachineProtocol> portProtocols, List<BlockPos> portList) {
        this.params = params.isEmpty() ? Reference2IntMaps.emptyMap() : params.clone();
        this.optionalFormed = optionalFormed;
        this.optionalMissing = optionalMissing;
        this.machines = group(protocols, machineList);
        this.ports = group(portProtocols, portList);
    }

    private static <T> Reference2ObjectMap<MachineProtocol, List<T>> group(List<MachineProtocol> protocols, List<T> values) {
        if (protocols.isEmpty()) return Reference2ObjectMaps.emptyMap();
        var lists = new Reference2ObjectOpenHashMap<MachineProtocol, ArrayList<T>>();
        for (int i = 0; i < protocols.size(); i++) lists.computeIfAbsent(protocols.get(i), p -> new ArrayList<>()).add(values.get(i));
        var map = new Reference2ObjectOpenHashMap<MachineProtocol, List<T>>(lists.size());
        for (var it = lists.reference2ObjectEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            map.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
        }
        return map;
    }

    public List<BlockPos> ports(MachineProtocol protocol) {
        var list = ports.get(protocol);
        return list == null ? Collections.emptyList() : list;
    }

    public List<MetaMachine> machines(MachineProtocol protocol) {
        var list = machines.get(protocol);
        return list == null ? Collections.emptyList() : list;
    }

    public int get(ParamKey key) {
        return params.getInt(key);
    }

    public boolean has(ParamKey key) {
        return params.getInt(key) > 0;
    }

    @Override
    public String toString() {
        return "Assembly" + params + "+" + optionalFormed + "-" + optionalMissing;
    }
}
