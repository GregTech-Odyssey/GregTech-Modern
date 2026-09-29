package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Layout;
import com.gregtechceu.gtceu.uiwidgets.patternbuilder.PatternBuilderModel;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
final class PreviewPipeline {

    static final long REFRESH_DELAY_MS = 200;

    private long rebuildAt;
    private long layerAt;
    @Nullable
    private Layout wholeLayout;
    @Nullable
    private StructurePlans.Preview whole;
    @Nullable
    private Layout lastListed;
    @Nullable
    private StructurePlans.Preview last;
    @Nullable
    private PatternBuilderModel builtModel;
    private int builtVersion;
    @Nullable
    private PatternBuilderModel model;
    private int modelVersion;

    void scheduleRebuild() {
        rebuildAt = System.currentTimeMillis() + REFRESH_DELAY_MS;
    }

    void scheduleLayer() {
        layerAt = System.currentTimeMillis() + REFRESH_DELAY_MS;
    }

    void cancel() {
        rebuildAt = 0;
        layerAt = 0;
    }

    boolean rebuildDue(long now) {
        if (rebuildAt <= 0 || now < rebuildAt) return false;
        cancel();
        return true;
    }

    boolean layerDue(long now) {
        if (layerAt <= 0 || now < layerAt) return false;
        layerAt = 0;
        return true;
    }

    double waitProgress() {
        long target = Math.max(rebuildAt, layerAt);
        if (target == 0) return -1;
        return Math.max(0, 1 - (target - System.currentTimeMillis()) / (double) REFRESH_DELAY_MS);
    }

    boolean modelChanged(@Nullable PatternBuilderModel current) {
        int version = current == null ? 0 : current.getVersion();
        if (current == model && version == modelVersion) return false;
        model = current;
        modelVersion = version;
        return true;
    }

    StructurePlans.Preview build(MultiblockMachineDefinition definition, Layout layout, @Nullable Layout listed,
                                 @Nullable PatternBuilderModel chosen) {
        int version = chosen == null ? 0 : chosen.getVersion();
        model = chosen;
        modelVersion = version;
        if (layout == wholeLayout && listed == lastListed && chosen == builtModel && version == builtVersion && last != null) return last;
        if (layout != wholeLayout || whole == null) {
            whole = StructurePlans.preview(definition, layout);
            wholeLayout = layout;
        }
        lastListed = listed;
        builtModel = chosen;
        builtVersion = version;
        var target = listed == null ? layout : listed;
        if (chosen == null && target == layout) {
            last = whole;
        } else {
            var items = chosen == null ? whole.items() : StructurePlans.merge(StructurePlans.assign(chosen, target), whole.items());
            last = new StructurePlans.Preview(layout, items, whole.controller(), StructurePlans.parts(target, items, definition.asStack().getItem()));
        }
        return last;
    }
}
