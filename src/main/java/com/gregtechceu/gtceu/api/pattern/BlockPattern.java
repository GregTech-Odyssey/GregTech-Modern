package com.gregtechceu.gtceu.api.pattern;

import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.blockentity.GTBlockEntity;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gregtechceu.gtceu.api.pattern.error.SinglePredicateError;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.core.ILevel;
import com.gregtechceu.gtceu.core.Iblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

import java.util.Collection;
import java.util.Set;

public class BlockPattern {

    private static final Set<Class<?>> WHITELIST = new ReferenceOpenHashSet<>();

    public static void addWhitelistBlockEntity(Class<?> clazz) {
        WHITELIST.add(clazz);
    }

    public final int[][] aisleRepetitions;
    public final RelativeDirection[] structureDir;
    public final TraceabilityPredicate[][][] blockMatches; // [z][y][x]
    public final int fingerLength; // z size
    public final int thumbLength; // y size
    public final int palmLength; // x size
    public final int[] centerOffset; // x, y, z, minZ, maxZ
    public final int[] formedRepetitionCount;
    public Collection<TraceabilityPredicate> predicates;

    public static final int CELL_PASS = 0;
    public static final int CELL_FAIL = 1;
    public static final int CELL_ABORT = 2;

    public BlockPattern(TraceabilityPredicate[][][] predicatesIn, RelativeDirection[] structureDir, int[][] aisleRepetitions, int[] centerOffset, int fingerLength, int thumbLength, int palmLength) {
        this.blockMatches = predicatesIn;
        this.structureDir = structureDir;
        this.aisleRepetitions = aisleRepetitions;
        this.formedRepetitionCount = new int[aisleRepetitions.length];
        this.centerOffset = centerOffset;
        this.fingerLength = fingerLength;
        this.thumbLength = thumbLength;
        this.palmLength = palmLength;
    }

    public boolean checkPatternAt(MultiblockState worldState, boolean savePredicate) {
        IMultiController controller = worldState.controller;
        BlockPos centerPos = worldState.controllerPos;
        Direction frontFacing = controller.self().getFrontFacing();
        Direction[] facings = controller.hasFrontFacing() ? new Direction[] { frontFacing } : new Direction[] { Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST };
        Direction upwardsFacing = controller.self().getUpwardsFacing();
        boolean allowsFlip = controller.self().allowFlip();
        worldState.errorRecord.clear();
        for (Direction direction : facings) {
            if (checkPatternAt(worldState, centerPos, direction, upwardsFacing, false, savePredicate)) {
                return true;
            } else {
                if (!savePredicate) worldState.errorRecord.add(worldState.error);
                if (allowsFlip) {
                    return checkPatternAt(worldState, centerPos, direction, upwardsFacing, true, savePredicate);
                }
            }
        }
        return false;
    }

    public boolean checkPatternAt(MultiblockState worldState, BlockPos centerPos, Direction frontFacing, Direction upwardsFacing, boolean isFlipped, boolean savePredicate) {
        boolean findFirstAisle = false;
        int minZ = -centerOffset[4];
        worldState.clear();
        var matchContext = worldState.getMatchContext();
        var ordinal = frontFacing.ordinal();
        var globalCount = worldState.getGlobalCount();
        var layerCount = worldState.getLayerCount();
        // Checking aisles
        for (int c = 0, z = minZ++, r; c < this.fingerLength; c++) {
            // Checking repeatable slices
            int validRepetitions = 0;
            loop:
            for (r = 0; (findFirstAisle ? r < aisleRepetitions[c][1] : z <= -centerOffset[3]); r++) {
                // Checking single slice
                layerCount.clear();
                for (int b = 0, y = -centerOffset[1]; b < this.thumbLength; b++, y++) {
                    for (int a = 0, x = -centerOffset[0]; a < this.palmLength; a++, x++) {
                        worldState.setError(null);
                        var bc = this.blockMatches[c];
                        if (bc == null) continue;
                        var bb = bc[b];
                        if (bb == null) continue;
                        TraceabilityPredicate predicate = bb[a];
                        if (predicate == null) continue;
                        BlockPos pos = setActualRelativeOffset(x, y, z, frontFacing, ordinal, upwardsFacing, isFlipped).offset(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                        int cell = testCell(worldState, pos, predicate, savePredicate);
                        if (cell == CELL_ABORT) return false;
                        if (cell == CELL_FAIL) {
                            // matching failed
                            if (findFirstAisle) {
                                if (r < aisleRepetitions[c][0]) {
                                    // retreat to see if the first aisle can start later
                                    r = c = 0;
                                    z = minZ++;
                                    matchContext.reset();
                                    findFirstAisle = false;
                                }
                            } else {
                                z++;// continue searching for the first aisle
                            }
                            continue loop;
                        }
                    }
                }
                findFirstAisle = true;
                z++;
                // Check layer-local matcher predicate
                for (var it = layerCount.reference2IntEntrySet().fastIterator(); it.hasNext();) {
                    var entry = it.next();
                    if (entry.getIntValue() < entry.getKey().minLayerCount) {
                        worldState.setError(new SinglePredicateError(entry.getKey(), 3));
                        return false;
                    }
                }
                validRepetitions++;
            }
            // Repetitions out of range
            if (r < aisleRepetitions[c][0] || worldState.hasError() || !findFirstAisle) {
                if (!worldState.hasError()) {
                    worldState.setError(new PatternError());
                }
                return false;
            }
            // finished checking the aisle, so store the repetitions
            formedRepetitionCount[c] = validRepetitions;
        }
        // Check count matches amount
        for (var it = globalCount.reference2IntEntrySet().fastIterator(); it.hasNext();) {
            var entry = it.next();
            if (entry.getIntValue() < entry.getKey().minCount) {
                worldState.setError(new SinglePredicateError(entry.getKey(), 1));
                return false;
            }
        }
        worldState.setNeededFlip(isFlipped);
        worldState.setError(null);
        return true;
    }

    public static int testCell(MultiblockState worldState, BlockPos pos, TraceabilityPredicate predicate, boolean savePredicate) {
        worldState.update(pos, predicate);
        long posLong = pos.asLong();
        boolean success = predicate.test(worldState);
        if (success && !predicate.testOnly()) {
            var matchContext = worldState.getMatchContext();
            if (savePredicate) {
                matchContext.getPredicates().put(posLong, predicate);
            }
            var block = worldState.getBlockState().getBlock();
            var data = worldState.data;
            if (data != null && !((Iblock) block).gtceu$canMultiShared()) {
                if (data.hasShared(posLong)) {
                    success = false;
                    worldState.setError(MultiblockState.SHARE_ERROR.copy());
                } else {
                    worldState.sharedCache.add(posLong);
                }
            }
            if (success) {
                if (block instanceof ActiveBlock) {
                    if (!savePredicate)
                        matchContext.getOrCreate(Predicates.DataKey.ACTIVE_BLOCKS, LongOpenHashSet::new).add(posLong);
                } else {
                    var blockentity = worldState.getTileEntity();
                    if (blockentity != null) {
                        if (blockentity instanceof MetaMachineBlockEntity machineBlockEntity) {
                            if (machineBlockEntity.metaMachine instanceof IMultiPart part && part != worldState.controller) {
                                if (!worldState.world.isLoaded(pos)) {
                                    worldState.setError(MultiblockState.UNLOAD_ERROR.copy());
                                    return CELL_ABORT;
                                }
                                matchContext.getParts().add(part);
                            }
                        } else if (!(blockentity instanceof GTBlockEntity) && !WHITELIST.contains(blockentity.getClass())) {
                            worldState.blockEntityCache.add(posLong);
                        }
                    }
                }
            }
        }
        if (success) {
            if (!savePredicate) worldState.cache.add(posLong);
            return CELL_PASS;
        }
        if (worldState.blockState == ILevel.OUTSIDE_WORLD_BLOCK) {
            worldState.setError(MultiblockState.UNLOAD_ERROR.copy());
        }
        return CELL_FAIL;
    }

    protected BlockPos setActualRelativeOffset(int x, int y, int z, Direction facing, int ordinal, Direction upwardsFacing, boolean isFlipped) {
        int[] c1 = new int[3];
        relativeToWorld(structureDir, x, y, z, facing, upwardsFacing, isFlipped, c1);
        return new BlockPos(c1[0], c1[1], c1[2]);
    }

    public static void relativeToWorld(RelativeDirection[] structureDir, int x, int y, int z, Direction facing, Direction upwardsFacing, boolean isFlipped, int[] c1) {
        int[] c0 = new int[] { x, y, z };
        c1[0] = 0;
        c1[1] = 0;
        c1[2] = 0;
        int ordinal = facing.ordinal();
        boolean down = ordinal == 0;
        if (down || ordinal == 1) {
            int of = down ? upwardsFacing.ordinal() : upwardsFacing.getOpposite().ordinal();
            for (int i = 0; i < 3; i++) {
                switch (structureDir[i].getActualOrdinal(of)) {
                    case 1 -> c1[1] = c0[i];
                    case 0 -> c1[1] = -c0[i];
                    case 4 -> c1[0] = -c0[i];
                    case 5 -> c1[0] = c0[i];
                    case 2 -> c1[2] = -c0[i];
                    case 3 -> c1[2] = c0[i];
                }
            }
            int xOffset = upwardsFacing.getStepX();
            int tmp;
            if (xOffset == 0) {
                tmp = c1[2];
                int zOffset = upwardsFacing.getStepZ();
                c1[2] = zOffset > 0 ? c1[1] : -c1[1];
                c1[1] = zOffset > 0 ? -tmp : tmp;
            } else {
                tmp = c1[0];
                c1[0] = xOffset > 0 ? c1[1] : -c1[1];
                c1[1] = xOffset > 0 ? -tmp : tmp;
            }
            if (isFlipped) {
                if (upwardsFacing == Direction.NORTH || upwardsFacing == Direction.SOUTH) {
                    c1[0] = -c1[0]; // flip X-axis
                } else {
                    c1[2] = -c1[2]; // flip Z-axis
                }
            }
        } else {
            for (int i = 0; i < 3; i++) {
                switch (structureDir[i].getActualOrdinal(ordinal)) {
                    case 1 -> c1[1] = c0[i];
                    case 0 -> c1[1] = -c0[i];
                    case 4 -> c1[0] = -c0[i];
                    case 5 -> c1[0] = c0[i];
                    case 2 -> c1[2] = -c0[i];
                    case 3 -> c1[2] = c0[i];
                }
            }
            boolean east = upwardsFacing == Direction.EAST;
            if (east || upwardsFacing == Direction.WEST) {
                int xOffset = east ? facing.getClockWise().getStepX() : facing.getClockWise().getOpposite().getStepX();
                int tmp;
                if (xOffset == 0) {
                    tmp = c1[2];
                    int zOffset = east ? facing.getClockWise().getStepZ() : facing.getClockWise().getOpposite().getStepZ();
                    c1[2] = zOffset > 0 ? -c1[1] : c1[1];
                    c1[1] = zOffset > 0 ? tmp : -tmp;
                } else {
                    tmp = c1[0];
                    c1[0] = xOffset > 0 ? -c1[1] : c1[1];
                    c1[1] = xOffset > 0 ? tmp : -tmp;
                }
            } else if (upwardsFacing == Direction.SOUTH) {
                c1[1] = -c1[1];
                if (facing.getStepX() == 0) {
                    c1[0] = -c1[0];
                } else {
                    c1[2] = -c1[2];
                }
            }
            if (isFlipped) {
                if (upwardsFacing == Direction.NORTH || upwardsFacing == Direction.SOUTH) {
                    if (ordinal == 2 || ordinal == 3) {
                        c1[0] = -c1[0]; // flip X-axis
                    } else {
                        c1[2] = -c1[2]; // flip Z-axis
                    }
                } else {
                    c1[1] = -c1[1]; // flip Y-axis
                }
            }
        }
    }
}
