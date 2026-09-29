package com.gregtechceu.gtceu.api.pattern.predicates;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.List;

public class PredicateAbilities extends PredicateBlocks {

    public static final String EXCLUDING = "gtceu.multiblock.pattern.excluding";

    @Getter
    private final PartAbility[] abilities;
    @Getter
    private final PartAbility[] excluded;

    public PredicateAbilities(PartAbility... abilities) {
        super(collect(abilities, new PartAbility[0]));
        this.abilities = abilities;
        this.excluded = new PartAbility[0];
    }

    public static PredicateAbilities of(PartAbility... abilities) {
        return new PredicateAbilities(abilities);
    }

    private PredicateAbilities(PredicateAbilities source, PartAbility[] excluded) {
        super(collect(source.abilities, excluded));
        this.abilities = source.abilities;
        this.excluded = excluded;
        this.toolTips = source.toolTips == null ? null : new ArrayList<>(source.toolTips);
        this.minCount = source.minCount;
        this.maxCount = source.maxCount;
        this.minLayerCount = source.minLayerCount;
        this.maxLayerCount = source.maxLayerCount;
        this.previewCount = source.previewCount;
        this.disableRenderFormed = source.disableRenderFormed;
    }

    public PredicateAbilities excluding(PartAbility... abilities) {
        return new PredicateAbilities(this, ArrayUtils.addAll(excluded, abilities));
    }

    public static Block[] collect(PartAbility[] abilities, PartAbility[] excluded) {
        var removed = new ReferenceOpenHashSet<Block>();
        for (var ability : excluded) removed.addAll(ability.getAllBlocks());
        var result = new ReferenceLinkedOpenHashSet<Block>();
        for (var ability : abilities) {
            for (var block : ability.getAllBlocks()) {
                if (!removed.contains(block)) result.add(block);
            }
        }
        return result.toArray(Block[]::new);
    }

    @Override
    public List<Component> getToolTips(TraceabilityPredicate predicates) {
        var result = super.getToolTips(predicates);
        if (excluded.length > 0) result.add(Component.translatable(EXCLUDING, PartAbility.join(excluded)));
        return result;
    }
}
