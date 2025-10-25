package com.gregtechceu.gtceu.api.pattern;

import com.gregtechceu.gtceu.api.block.ActiveBlock;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.pattern.error.PatternError;
import com.gregtechceu.gtceu.api.pattern.error.PatternStringError;
import com.gregtechceu.gtceu.api.pattern.error.SinglePredicateError;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.pattern.util.PatternMatchContext;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.utils.collection.O2IOpenCacheHashMap;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;

import it.unimi.dsi.fastutil.ints.IntObjectPair;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public class BlockPattern {

    public final static Direction[] FACINGS = { Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN };
    public final static Direction[] FACINGS_H = { Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST };
    public final int[][] aisleRepetitions;
    public final RelativeDirection[] structureDir;
    public final TraceabilityPredicate[][][] blockMatches; // [z][y][x]
    public final int fingerLength; // z size
    public final int thumbLength; // y size
    public final int palmLength; // x size
    public final int[] centerOffset; // x, y, z, minZ, maxZ
    public int[] formedRepetitionCount;
    public Collection<TraceabilityPredicate> predicates;
    public PatternCondition condition;

    public BlockPattern(TraceabilityPredicate[][][] predicatesIn, RelativeDirection[] structureDir, int[][] aisleRepetitions, int[] centerOffset) {
        this.blockMatches = predicatesIn;
        this.structureDir = structureDir;
        this.aisleRepetitions = aisleRepetitions;
        this.formedRepetitionCount = new int[aisleRepetitions.length];
        this.fingerLength = predicatesIn.length;
        this.thumbLength = predicatesIn[0].length;
        this.palmLength = predicatesIn[0][0].length;
        this.centerOffset = centerOffset;
    }

    public boolean checkPatternAt(MultiblockState worldState, boolean savePredicate) {
        if (condition != null && !condition.condition().test(worldState)) {
            worldState.setError(new PatternStringError(condition.translateKey()));
            return false;
        }
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
        worldState.clean();
        PatternMatchContext matchContext = worldState.getMatchContext();
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
                        TraceabilityPredicate predicate = this.blockMatches[c][b][a];
                        worldState.setError(null);
                        if (predicate != null) {
                            BlockPos pos = setActualRelativeOffset(x, y, z, frontFacing, upwardsFacing, isFlipped).offset(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                            worldState.update(pos, predicate);
                            long posLong = pos.asLong();
                            worldState.addPosCache(posLong);

                            boolean error = !predicate.test(worldState);
                            if (!error && !predicate.testOnly()) {
                                if (savePredicate) {
                                    matchContext.getPredicates().put(posLong, predicate);
                                }
                                if (worldState.getBlockState().getBlock() instanceof ActiveBlock) {
                                    matchContext.vaBlocks.add(posLong);
                                } else if (worldState.getTileEntity() instanceof MetaMachineBlockEntity machineBlockEntity && machineBlockEntity.getMetaMachine() instanceof IMultiPart part) {
                                    if (!worldState.world.isLoaded(pos)) {
                                        worldState.setError(MultiblockState.UNLOAD_ERROR);
                                        return false;
                                    }
                                    // add detected parts
                                    if (part.isFormed() && !part.canShared() && !part.hasController(worldState.controllerPos)) {
                                        // check part can be shared
                                        error = true;
                                        worldState.setError(MultiblockState.SHARE_ERROR);
                                    } else {
                                        matchContext.parts.add(part);
                                    }
                                }
                            }
                            if (error) {
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
        worldState.setError(null);
        worldState.setNeededFlip(isFlipped);
        return true;
    }

    public void autoBuild(Player player, MultiblockState worldState) {
        Level world = player.level();
        int minZ = -centerOffset[4];
        worldState.cleanCache();
        worldState.clean();
        IMultiController controller = worldState.controller;
        BlockPos centerPos = worldState.controllerPos;
        Direction facing = controller.self().getFrontFacing();
        Direction upwardsFacing = controller.self().getUpwardsFacing();
        boolean isFlipped = controller.self().isFlipped();
        var cacheGlobal = worldState.getGlobalCount();
        var cacheLayer = worldState.getLayerCount();
        LongOpenHashSet blocks = new LongOpenHashSet(1024, 0.5F);
        Long2ObjectOpenHashMap<MetaMachine> machines = new Long2ObjectOpenHashMap<>();
        blocks.add(centerPos.asLong());
        for (int c = 0, z = minZ++, r; c < this.fingerLength; c++) {
            for (r = 0; r < aisleRepetitions[c][0]; r++) {
                cacheLayer.clear();
                for (int b = 0, y = -centerOffset[1]; b < this.thumbLength; b++, y++) {
                    for (int a = 0, x = -centerOffset[0]; a < this.palmLength; a++, x++) {
                        TraceabilityPredicate predicate = this.blockMatches[c][b][a];
                        if (predicate != null) {
                            BlockPos pos = setActualRelativeOffset(x, y, z, facing, upwardsFacing, isFlipped).offset(centerPos.getX(), centerPos.getY(), centerPos.getZ());
                            worldState.update(pos, predicate);
                            long posLong = pos.asLong();
                            if (!world.isEmptyBlock(pos)) {
                                blocks.add(posLong);
                                for (SimplePredicate limit : predicate.limited) {
                                    limit.testLimited(worldState);
                                }
                            } else {
                                boolean find = false;
                                Block[] infos = new Block[0];
                                for (SimplePredicate limit : predicate.limited) {
                                    if (limit.minLayerCount > 0) {
                                        int curr = cacheLayer.getInt(limit);
                                        if (curr < limit.minLayerCount && (limit.maxLayerCount == -1 || curr < limit.maxLayerCount)) {
                                            cacheLayer.addTo(limit, 1);
                                        } else {
                                            continue;
                                        }
                                    } else {
                                        continue;
                                    }
                                    infos = limit.candidates == null ? null : limit.candidates.get();
                                    find = true;
                                    break;
                                }
                                if (!find) {
                                    for (SimplePredicate limit : predicate.limited) {
                                        if (limit.minCount > 0) {
                                            int curr = cacheGlobal.getInt(limit);
                                            if (curr < limit.minCount && (limit.maxCount == -1 || curr < limit.maxCount)) {
                                                cacheGlobal.addTo(limit, 1);
                                            } else {
                                                continue;
                                            }
                                        } else {
                                            continue;
                                        }
                                        infos = limit.candidates == null ? null : limit.candidates.get();
                                        find = true;
                                        break;
                                    }
                                }
                                if (!find) {
                                    // no limited
                                    for (SimplePredicate limit : predicate.limited) {
                                        if (limit.maxLayerCount != -1 && cacheLayer.getOrDefault(limit, Integer.MAX_VALUE) == limit.maxLayerCount) {
                                            continue;
                                        }
                                        if (limit.maxCount != -1 && cacheGlobal.getOrDefault(limit, Integer.MAX_VALUE) == limit.maxCount) {
                                            continue;
                                        }
                                        cacheLayer.addTo(limit, 1);
                                        cacheGlobal.addTo(limit, 1);
                                        infos = ArrayUtils.addAll(infos, limit.candidates == null ? null : limit.candidates.get());
                                    }
                                    for (SimplePredicate common : predicate.common) {
                                        infos = ArrayUtils.addAll(infos, common.candidates == null ? null : common.candidates.get());
                                    }
                                }
                                List<ItemStack> candidates = new ObjectArrayList<>();
                                if (infos != null) {
                                    for (Block info : infos) {
                                        if (info != Blocks.AIR) {
                                            candidates.add(SimplePredicate.toItem(info).getDefaultInstance());
                                        }
                                    }
                                }
                                // check inventory
                                ItemStack found = null;
                                int foundSlot = -1;
                                IItemHandler handler = null;
                                if (!player.isCreative()) {
                                    var foundHandler = getMatchStackWithHandler(candidates, player.getCapability(ForgeCapabilities.ITEM_HANDLER));
                                    if (foundHandler != null) {
                                        foundSlot = foundHandler.firstInt();
                                        handler = foundHandler.second();
                                        found = handler.getStackInSlot(foundSlot).copy();
                                    }
                                } else {
                                    for (ItemStack candidate : candidates) {
                                        found = candidate.copy();
                                        if (!found.isEmpty() && found.getItem() instanceof BlockItem) {
                                            break;
                                        }
                                        found = null;
                                    }
                                }
                                if (found == null) continue;
                                BlockItem itemBlock = (BlockItem) found.getItem();
                                BlockPlaceContext context = new BlockPlaceContext(world, player, InteractionHand.MAIN_HAND, found, BlockHitResult.miss(player.getEyePosition(0), Direction.UP, pos));
                                InteractionResult interactionResult = itemBlock.place(context);
                                if (interactionResult != InteractionResult.FAIL) {
                                    if (handler != null) {
                                        handler.extractItem(foundSlot, 1, false);
                                    }
                                    var direction = predicate.direction.apply(worldState);
                                    if (direction != null) {
                                        world.setBlock(pos, world.getBlockState(pos).setValue(DirectionalBlock.FACING, direction), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                                    } else {
                                        if (world.getBlockEntity(pos) instanceof MetaMachineBlockEntity machineBlockEntity) {
                                            machines.put(posLong, machineBlockEntity.metaMachine);
                                        }
                                    }
                                    blocks.add(posLong);
                                }
                            }
                        }
                    }
                }
                z++;
            }
        }
        Direction frontFacing = controller.self().getFrontFacing();
        machines.long2ObjectEntrySet().fastForEach(entry -> {
            long posLong = entry.getLongKey();
            var machine = entry.getValue();
            BlockPos pos = BlockPos.of(posLong);
            resetFacing(pos, machine.getBlockState(), frontFacing, (p, f) -> {
                if (!blocks.contains(p.relative(f).asLong())) {
                    return machine.isFacingValid(f);
                }
                return false;
            }, state -> world.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
        });
    }

    public BlockInfo[][][] getPreview(int[] repetition) {
        Reference2IntOpenHashMap<SimplePredicate> cacheGlobal = new Reference2IntOpenHashMap<>();
        Long2ObjectOpenHashMap<BlockInfo> blocks = new Long2ObjectOpenHashMap<>(1024, 0.5F);
        Long2ObjectOpenHashMap<BlockInfo> machines = new Long2ObjectOpenHashMap<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int l = 0, x = 0; l < this.fingerLength; l++) {
            for (int r = 0; r < repetition[l]; r++) {
                // Checking single slice
                Object2IntOpenHashMap<SimplePredicate> cacheLayer = new O2IOpenCacheHashMap<>();
                for (int y = 0; y < this.thumbLength; y++) {
                    for (int z = 0; z < this.palmLength; z++) {
                        TraceabilityPredicate predicate = this.blockMatches[l][y][z];
                        if (predicate != null) {
                            BlockInfo info = null;
                            boolean find = false;
                            for (SimplePredicate limit : predicate.limited) {
                                // check layer and previewCount
                                if (limit.minLayerCount > 0) {
                                    if (cacheLayer.getInt(limit) < limit.minLayerCount) {
                                        cacheLayer.addTo(limit, 1);
                                    } else {
                                        continue;
                                    }
                                    if (cacheGlobal.getInt(limit) < limit.previewCount) {
                                        cacheGlobal.addTo(limit, 1);
                                    } else {
                                        continue;
                                    }
                                } else {
                                    continue;
                                }
                                info = limit.blockInfo.get();
                                if (info != null) {
                                    find = true;
                                    break;
                                }
                            }
                            if (!find) {
                                // check global and previewCount
                                for (SimplePredicate limit : predicate.limited) {
                                    if (limit.minCount == -1 && limit.previewCount == -1) continue;
                                    if (cacheGlobal.getInt(limit) < limit.previewCount) {
                                        cacheGlobal.addTo(limit, 1);
                                    } else if (limit.minCount > 0) {
                                        if (cacheGlobal.getInt(limit) < limit.minCount) {
                                            cacheGlobal.addTo(limit, 1);
                                        } else {
                                            continue;
                                        }
                                    } else {
                                        continue;
                                    }
                                    info = limit.blockInfo.get();
                                    if (info != null) {
                                        find = true;
                                        break;
                                    }
                                }
                            }
                            if (!find) {
                                // check common with previewCount
                                for (SimplePredicate common : predicate.common) {
                                    if (common.previewCount > 0) {
                                        if (cacheGlobal.getInt(common) < common.previewCount) {
                                            cacheGlobal.addTo(common, 1);
                                        } else {
                                            continue;
                                        }
                                    } else {
                                        continue;
                                    }
                                    info = common.blockInfo.get();
                                    if (info != null) {
                                        find = true;
                                        break;
                                    }
                                }
                            }
                            if (!find) {
                                // check without previewCount
                                for (SimplePredicate common : predicate.common) {
                                    if (common.previewCount == -1) {
                                        info = common.blockInfo.get();
                                        if (info != null) {
                                            find = true;
                                            break;
                                        }
                                    }
                                }
                            }
                            if (!find) {
                                // check max
                                for (SimplePredicate limit : predicate.limited) {
                                    if (limit.previewCount != -1) continue;
                                    if (limit.maxCount != -1 || limit.maxLayerCount != -1) {
                                        if (cacheGlobal.getOrDefault(limit, 0) < limit.maxCount) {
                                            cacheGlobal.addTo(limit, 1);
                                        } else if (cacheLayer.getOrDefault(limit, 0) < limit.maxLayerCount) {
                                            cacheLayer.addTo(limit, 1);
                                        } else {
                                            continue;
                                        }
                                    }
                                    info = limit.blockInfo.get();
                                    if (info != null) {
                                        break;
                                    }
                                }
                            }
                            if (info != null && info.getBlockState().getBlock() != Blocks.AIR) {
                                BlockPos pos = setActualRelativeOffset(z, y, x, Direction.NORTH, Direction.UP, false);
                                if (info.getBlockState().getBlock() instanceof MetaMachineBlock) {
                                    machines.put(pos.asLong(), info);
                                } else {
                                    blocks.put(pos.asLong(), info);
                                }
                                minX = Math.min(pos.getX(), minX);
                                minY = Math.min(pos.getY(), minY);
                                minZ = Math.min(pos.getZ(), minZ);
                                maxX = Math.max(pos.getX(), maxX);
                                maxY = Math.max(pos.getY(), maxY);
                                maxZ = Math.max(pos.getZ(), maxZ);
                            }
                        }
                    }
                }
                x++;
            }
        }
        BlockInfo[][][] result = (BlockInfo[][][]) Array.newInstance(BlockInfo.class, maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
        int finalMinX = minX;
        int finalMinY = minY;
        int finalMinZ = minZ;
        machines.long2ObjectEntrySet().fastForEach(entry -> {
            var blockPos = entry.getLongKey();
            var pos = BlockPos.of(blockPos);
            var info = entry.getValue();
            var blockState = info.getBlockState();
            if (blockState.getBlock() instanceof MetaMachineBlock machineBlock) {
                resetFacing(pos, blockState, null, (p, f) -> {
                    var rp = p.relative(f).asLong();
                    if (blocks.get(rp) == null && machines.get(rp) == null) {
                        if (machineBlock.definition instanceof MultiblockMachineDefinition) {
                            return false;
                        } else {
                            return MetaMachine.isFacingValid(machineBlock, blockState, f);
                        }
                    }
                    return false;
                }, info::setBlockState);
            }
            result[pos.getX() - finalMinX][pos.getY() - finalMinY][pos.getZ() - finalMinZ] = info;
        });
        blocks.long2ObjectEntrySet().fastForEach(entry -> {
            var pos = BlockPos.of(entry.getLongKey());
            var info = entry.getValue();
            result[pos.getX() - finalMinX][pos.getY() - finalMinY][pos.getZ() - finalMinZ] = info;
        });
        return result;
    }

    protected void resetFacing(BlockPos pos, BlockState blockState, Direction facing, BiPredicate<BlockPos, Direction> checker, Consumer<BlockState> consumer) {
        if (blockState.hasProperty(BlockStateProperties.FACING)) {
            tryFacings(blockState, pos, checker, consumer, BlockStateProperties.FACING, facing == null ? FACINGS : ArrayUtils.addAll(new Direction[] { facing }, FACINGS));
        } else if (blockState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            tryFacings(blockState, pos, checker, consumer, BlockStateProperties.HORIZONTAL_FACING, facing == null || facing.getAxis() == Direction.Axis.Y ? FACINGS_H : ArrayUtils.addAll(new Direction[] { facing }, FACINGS_H));
        }
    }

    protected void tryFacings(BlockState blockState, BlockPos pos, BiPredicate<BlockPos, Direction> checker, Consumer<BlockState> consumer, Property<Direction> property, Direction[] facings) {
        Direction found = null;
        for (Direction facing : facings) {
            if (checker.test(pos, facing)) {
                found = facing;
                break;
            }
        }
        if (found == null) {
            found = Direction.NORTH;
        }
        consumer.accept(blockState.setValue(property, found));
    }

    protected BlockPos setActualRelativeOffset(int x, int y, int z, Direction facing, Direction upwardsFacing, boolean isFlipped) {
        int[] c0 = new int[] { x, y, z };
        int[] c1 = new int[3];
        boolean down = facing == Direction.DOWN;
        if (down || facing == Direction.UP) {
            Direction of = down ? upwardsFacing : upwardsFacing.getOpposite();
            for (int i = 0; i < 3; i++) {
                switch (structureDir[i].getActualDirection(of).ordinal()) {
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
                switch (structureDir[i].getActualDirection(facing).ordinal()) {
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
                    if (facing == Direction.NORTH || facing == Direction.SOUTH) {
                        c1[0] = -c1[0]; // flip X-axis
                    } else {
                        c1[2] = -c1[2]; // flip Z-axis
                    }
                } else {
                    c1[1] = -c1[1]; // flip Y-axis
                }
            }
        }
        return new BlockPos(c1[0], c1[1], c1[2]);
    }

    @Nullable
    protected static IntObjectPair<IItemHandler> getMatchStackWithHandler(List<ItemStack> candidates, LazyOptional<IItemHandler> cap) {
        IItemHandler handler = cap.orElse(null);
        if (handler == null) {
            return null;
        }
        for (int i = 0; i < handler.getSlots(); i++) {
            @NotNull
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            @NotNull
            LazyOptional<IItemHandler> stackCap = stack.getCapability(ForgeCapabilities.ITEM_HANDLER);
            if (stackCap.isPresent()) {
                var rt = getMatchStackWithHandler(candidates, stackCap);
                if (rt != null) {
                    return rt;
                }
            } else if (candidates.stream().anyMatch(candidate -> ItemStack.isSameItemSameTags(candidate, stack)) && !stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                return IntObjectPair.of(i, handler);
            }
        }
        return null;
    }
}
