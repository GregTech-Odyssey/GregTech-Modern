package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.Predicates;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

public final class MachineProtocol {

    private final ResourceLocation id;
    private final String translationKey;
    private final CopyOnWriteArrayList<MachineDefinition> members = new CopyOnWriteArrayList<>();
    private final List<MachineDefinition> memberView = Collections.unmodifiableList(members);
    private final CopyOnWriteArrayList<MultiblockMachineDefinition> hosts = new CopyOnWriteArrayList<>();
    private final List<MultiblockMachineDefinition> hostView = Collections.unmodifiableList(hosts);
    @Nullable
    private final Supplier<TraceabilityPredicate> vacant;
    private volatile TraceabilityPredicate cell;

    private MachineProtocol(ResourceLocation id, String translationKey, @Nullable Supplier<TraceabilityPredicate> vacant) {
        this.id = id;
        this.translationKey = translationKey;
        this.vacant = vacant;
    }

    public static MachineProtocol parentChild(ResourceLocation id, String translationKey) {
        return new MachineProtocol(id, translationKey, null);
    }

    public static MachineProtocol parentChild(ResourceLocation id, String translationKey, Supplier<TraceabilityPredicate> vacant) {
        return new MachineProtocol(id, translationKey, vacant);
    }

    public ResourceLocation getId() {
        return id;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public Component getName() {
        return Component.translatable(translationKey);
    }

    public List<MachineDefinition> getMembers() {
        return memberView;
    }

    public List<MultiblockMachineDefinition> getHosts() {
        return hostView;
    }

    void addHost(MultiblockMachineDefinition definition) {
        hosts.addIfAbsent(definition);
    }

    public boolean accepts(MachineDefinition definition) {
        return members.contains(definition);
    }

    public void mount(MachineDefinition definition) {
        members.addIfAbsent(definition);
    }

    TraceabilityPredicate cell() {
        var predicate = cell;
        if (predicate == null) {
            synchronized (this) {
                predicate = cell;
                if (predicate == null) {
                    var machines = new TraceabilityPredicate(state -> isMember(state.getBlockState().getBlock()), () -> BlockInfo.fromBlock(Blocks.BARRIER),
                            this::blocks).setPreviewCount(0);
                    var empty = vacant != null ? vacant.get() : Predicates.blocks(Blocks.BARRIER).or(Predicates.air().setPreviewCount(0));
                    cell = predicate = empty.or(machines);
                }
            }
        }
        return predicate;
    }

    private boolean isMember(Block block) {
        for (var member : members) {
            if (member.get() == block) return true;
        }
        return false;
    }

    private Block[] blocks() {
        var blocks = new Block[members.size()];
        for (int i = 0; i < blocks.length; i++) blocks[i] = members.get(i).get();
        return blocks;
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
