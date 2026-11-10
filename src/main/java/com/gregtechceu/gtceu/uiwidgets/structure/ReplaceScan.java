package com.gregtechceu.gtceu.uiwidgets.structure;

import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.multiblockpro.Layout;
import com.gregtechceu.gtceu.api.machine.multiblockpro.StructureBuild;
import com.gregtechceu.gtceu.api.pattern.ControllerPredicate;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;

import java.util.Set;

/**
 * 替换模式的客户端预判：按客户端世界逐格比对所选方块，得出将被替换的格子与"原方块→新方块"计数，预览高亮与建造配置页共用。
 */
@OnlyIn(Dist.CLIENT)
public final class ReplaceScan {

    public record Result(IntArrayList cells, Object2IntLinkedOpenHashMap<StructureBuild.Swap> swaps) {}

    private ReplaceScan() {}

    public static Result scan(IMultiController controller, Layout layout, Item[] items, boolean flip, Set<StructureBuild.Swap> kept) {
        var cells = new IntArrayList();
        var swaps = new Object2IntLinkedOpenHashMap<StructureBuild.Swap>();
        var level = Minecraft.getInstance().level;
        var machine = controller.self();
        if (level == null || machine.getLevel() != level) return new Result(cells, swaps);
        var origin = machine.getPos();
        var front = machine.getFrontFacing();
        var up = machine.getUpwardsFacing();
        boolean mirror = flip && machine.allowFlip();
        var layoutCells = layout.cells();
        var pos = new BlockPos.MutableBlockPos();
        int count = Math.min(items.length, layoutCells.size());
        for (int i = 0; i < count; i++) {
            var item = items[i];
            if (item == null || layoutCells.get(i).predicate() instanceof ControllerPredicate) continue;
            layout.worldPos(origin, i, front, up, mirror, pos);
            if (!level.isLoaded(pos)) continue;
            var from = StructureBuild.swapFrom(level, pos, item);
            if (from == null) continue;
            var swap = new StructureBuild.Swap(from, item);
            if (kept.contains(swap)) continue;
            cells.add(i);
            swaps.addTo(swap, 1);
        }
        return new Result(cells, swaps);
    }
}
