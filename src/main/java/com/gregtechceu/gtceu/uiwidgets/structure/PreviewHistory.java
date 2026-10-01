package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Structure;
import com.gregtechceu.gtceu.uipro.view.scene.SceneView;

import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
final class PreviewHistory {

    static final class Page {

        final MultiblockMachineDefinition definition;
        final Structure structure;
        final int[] values;
        final boolean[] excluded;
        final ReferenceOpenHashSet<Item> shown = new ReferenceOpenHashSet<>();
        @Nullable
        SceneView.Camera camera;
        int layer;

        Page(MultiblockMachineDefinition definition, Structure structure) {
            this.definition = definition;
            this.structure = structure;
            this.values = StructureBuildFlow.remembered(definition, structure);
            this.excluded = StructureConfigView.defaultExcluded(structure.tree(), values);
        }
    }

    private final List<Page> pages = new ArrayList<>();
    private int index;

    PreviewHistory(Page first) {
        pages.add(first);
    }

    Page current() {
        return pages.get(index);
    }

    int index() {
        return index;
    }

    boolean isRoot() {
        return index == 0;
    }

    boolean hasMany() {
        return pages.size() > 1;
    }

    boolean canForward() {
        return index < pages.size() - 1;
    }

    int push(Page page) {
        while (pages.size() > index + 1) pages.remove(pages.size() - 1);
        pages.add(page);
        return pages.size() - 1;
    }

    Page select(int target) {
        index = target;
        return pages.get(target);
    }
}
