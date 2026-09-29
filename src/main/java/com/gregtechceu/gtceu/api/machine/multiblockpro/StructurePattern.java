package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.TraceabilityPredicate;
import com.gregtechceu.gtceu.api.pattern.predicates.PredicateDirections;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class StructurePattern extends BlockPattern {

    private static final RelativeDirection[] DIRS = { RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.BACK };
    private static final int[][] MIRRORS = mirrors();

    @Getter
    private final Structure structure;
    @Nullable
    private volatile int[] size;
    @Nullable
    private volatile Boolean mirrorSymmetric;

    private StructurePattern(Structure structure) {
        super(new TraceabilityPredicate[0][][], DIRS, new int[0][], new int[5], 0, 0, 0);
        this.structure = structure;
    }

    @Nullable
    public static Structure of(MultiblockMachineDefinition definition) {
        return definition.displayPattern() instanceof StructurePattern pattern ? pattern.structure : null;
    }

    static StructurePattern create(Structure structure) {
        return new StructurePattern(structure);
    }

    private int[] size() {
        var result = size;
        if (result == null) {
            var layout = structure.layout(structure.defaultValues());
            result = layout == null ? new int[3] : new int[] { layout.width(), layout.height(), layout.depth() };
            size = result;
        }
        return result;
    }

    public int getWidth() {
        return size()[0];
    }

    public int getHeight() {
        return size()[1];
    }

    public int getDepth() {
        return size()[2];
    }

    private boolean mirrorSymmetric() {
        var result = mirrorSymmetric;
        if (result == null) {
            result = mirrorSymmetric(structure);
            mirrorSymmetric = result;
        }
        return result;
    }

    @Override
    public boolean checkPatternAt(MultiblockState worldState, BlockPos centerPos, Direction frontFacing, Direction upwardsFacing, boolean isFlipped, boolean savePredicate) {
        boolean result = StructureMatcher.acquire().match(structure, worldState, centerPos, frontFacing, upwardsFacing, isFlipped, savePredicate);
        if (result && !isFlipped && structure.optionalSlots && fuller(worldState, centerPos, frontFacing, upwardsFacing)) {
            return StructureMatcher.acquire().match(structure, worldState, centerPos, frontFacing, upwardsFacing, true, savePredicate);
        }
        return result;
    }

    private boolean fuller(MultiblockState worldState, BlockPos centerPos, Direction frontFacing, Direction upwardsFacing) {
        if (mirrorSymmetric()) return false;
        var assembly = worldState.getMatchContext().get(Assembly.KEY);
        if (assembly == null || assembly.getOptionalMissing() == 0 || !worldState.controller.self().allowFlip()) return false;
        var probe = MultiblockState.probe(worldState);
        if (!StructureMatcher.acquire().match(structure, probe, centerPos, frontFacing, upwardsFacing, true, false)) return false;
        var flipped = probe.getMatchContext().get(Assembly.KEY);
        return flipped != null && flipped.getOptionalFormed() > assembly.getOptionalFormed();
    }

    private static boolean mirrorSymmetric(Structure structure) {
        if (!structure.optionalSlots || structure.perSize) return false;
        var values = structure.defaultValues();
        for (var node : structure.tree().nodes()) {
            int option = node.option();
            if (option < 0 || option >= values.length) continue;
            if (node.kind() == StructureTree.Kind.CHOICE && node.max() > 1) return false;
            if (node.isOptionalModule()) values[option] = 1;
        }
        var layout = structure.layout(values);
        if (layout == null) return false;
        var cells = layout.cells();
        var index = new Long2IntOpenHashMap(cells.size());
        index.defaultReturnValue(-1);
        for (int i = 0; i < cells.size(); i++) {
            var cell = cells.get(i);
            if (directional(cell.predicate())) return false;
            index.put(BlockPos.asLong(cell.x(), cell.y(), cell.z()), i);
        }
        for (var m : MIRRORS) {
            for (var cell : cells) {
                int x = m[0] * cell.x() + m[1] * cell.y() + m[2] * cell.z();
                int y = m[3] * cell.x() + m[4] * cell.y() + m[5] * cell.z();
                int z = m[6] * cell.x() + m[7] * cell.y() + m[8] * cell.z();
                int j = index.get(BlockPos.asLong(x, y, z));
                if (j < 0) return false;
                var other = cells.get(j);
                if (other.predicate() != cell.predicate() || other.node() != cell.node()) return false;
            }
        }
        return true;
    }

    private static boolean directional(TraceabilityPredicate predicate) {
        if (predicate.direction != GTUtil.NULL_FUNCTION) return true;
        for (var simple : predicate.common) {
            if (simple instanceof PredicateDirections) return true;
        }
        for (var simple : predicate.limited) {
            if (simple instanceof PredicateDirections) return true;
        }
        return false;
    }

    private static int[][] mirrors() {
        List<int[]> mirrors = new ArrayList<>();
        for (var front : Direction.values()) {
            for (var up : Direction.Plane.HORIZONTAL) {
                int[] plain = Orientation.world(front, up, false);
                int[] flipped = Orientation.world(front, up, true);
                int[] m = new int[9];
                for (int row = 0; row < 3; row++) {
                    for (int column = 0; column < 3; column++) {
                        m[row * 3 + column] = plain[row * 3] * flipped[column * 3] + plain[row * 3 + 1] * flipped[column * 3 + 1] +
                                plain[row * 3 + 2] * flipped[column * 3 + 2];
                    }
                }
                boolean known = false;
                for (var existing : mirrors) known |= Arrays.equals(existing, m);
                if (!known) mirrors.add(m);
            }
        }
        return mirrors.toArray(int[][]::new);
    }
}
