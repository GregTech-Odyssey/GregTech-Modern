package com.gregtechceu.gtceu.api.capability;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.block.IAppearance;
import com.gregtechceu.gtceu.api.blockentity.GTBlockEntity;
import com.gregtechceu.gtceu.api.blockentity.ITickSubscription;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.transfer.fluid.ICustomFluidStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.ICustomItemStackHandler;
import com.gregtechceu.gtceu.datasynclib.GTDataFixer;
import com.gregtechceu.gtceu.utils.GTUtil;
import com.gregtechceu.gtceu.utils.cache.BlockEntityDirectionCache;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.google.common.collect.ImmutableList;
import com.gto.datasynclib.IFieldDataHolder;
import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.data.ListData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public interface ICoverable extends ITickSubscription, IAppearance, IFieldDataHolder {

    BlockEntityDirectionCache getBlockEntityDirectionCache();

    BlockEntity getNeighbor(Direction facing);

    GTBlockEntity holder();

    Level getLevel();

    BlockPos getPos();

    void onChanged();

    boolean isInValid();

    void notifyNeighborsUpdate();

    void scheduleNeighborShapeUpdate();

    boolean canPlaceCoverOnSide(CoverDefinition definition, Direction side);

    double getCoverPlateThickness();

    Direction getFrontFacing();

    boolean shouldRenderBackSide();

    ICustomItemStackHandler getItemHandlerCap(@Nullable Direction side, boolean useCoverCapability);

    ICustomFluidStackHandler getFluidHandlerCap(@Nullable Direction side, boolean useCoverCapability);

    /**
     * Its an internal method, you should never call it yourself.
     * <br>
     * Use {@link ICoverable#removeCover(boolean, Direction, Player)} and
     * {@link ICoverable#placeCoverOnSide(Direction, ItemStack, CoverDefinition, ServerPlayer)} instead
     * 
     * @param coverBehavior
     * @param side
     */
    void setCoverAtSide(@Nullable CoverBehavior coverBehavior, Direction side);

    void setCoverAtSideinternal(@Nullable CoverBehavior coverBehavior, Direction side);

    @Nullable
    CoverBehavior getCoverAtSide(Direction side);

    @Nullable
    default <T> Object getGTCapability(Class<T> cap, @Nullable Direction side) {
        if (side != null) {
            var cover = getCoverAtSide(side);
            if (cover != null) return cover.getGTCapability(cap);
        } else {
            for (var s : GTUtil.DIRECTIONS) {
                var cover = getCoverAtSide(s);
                if (cover != null) {
                    var result = cover.getGTCapability(cap);
                    if (result != null) return result;
                }
            }
        }
        return null;
    }

    default boolean placeCoverOnSide(Direction side, ItemStack itemStack, CoverDefinition coverDefinition,
                                     ServerPlayer player) {
        CoverBehavior coverBehavior = coverDefinition.createCoverBehavior(this, side);
        if (!canPlaceCoverOnSide(coverDefinition, side) || !coverBehavior.canAttach()) {
            return false;
        }
        if (getCoverAtSide(side) != null) {
            removeCover(side, player);
        }
        coverBehavior.onAttached(itemStack, player);
        coverBehavior.onLoad();
        setCoverAtSide(coverBehavior, side);
        notifyNeighborsUpdate();
        onChanged();
        scheduleNeighborShapeUpdate();
        // TODO achievement
        // AdvancementTriggers.FIRST_COVER_PLACE.trigger((PlayerMP) player);
        return true;
    }

    default boolean removeCover(boolean dropItself, Direction side, @Nullable Player player) {
        CoverBehavior coverBehavior = getCoverAtSide(side);
        if (coverBehavior == null) {
            return false;
        }
        List<ItemStack> drops = coverBehavior.getAdditionalDrops();
        if (dropItself) {
            drops.add(coverBehavior.getPickItem());
        }
        coverBehavior.onRemoved();
        setCoverAtSide(null, side);
        for (ItemStack dropStack : drops) {
            if (player != null && player.getInventory().add(dropStack))
                continue;

            Block.popResource(getLevel(), getPos(), dropStack);

        }
        notifyNeighborsUpdate();
        onChanged();
        scheduleNeighborShapeUpdate();
        return true;
    }

    /**
     * Drop all attached covers on the ground
     */
    default void dropAllCovers() {
        for (Direction side : GTUtil.DIRECTIONS) {
            removeCover(side, null);
        }
    }

    default boolean removeCover(Direction side, @Nullable Player player) {
        return removeCover(true, side, player);
    }

    default List<CoverBehavior> getCovers() {
        ImmutableList.Builder<CoverBehavior> result = ImmutableList.builderWithExpectedSize(6);
        for (Direction direction : GTUtil.DIRECTIONS) {
            var cover = getCoverAtSide(direction);
            if (cover != null) {
                result.add(cover);
            }
        }
        return result.build();
    }

    default void onLoad() {
        for (CoverBehavior cover : getCovers()) {
            cover.onLoad();
        }
    }

    default void onUnload() {
        for (CoverBehavior cover : getCovers()) {
            cover.onUnload();
        }
    }

    default void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        for (CoverBehavior cover : getCovers()) {
            cover.onNeighborChanged(block, fromPos, isMoving);
        }
    }

    default boolean hasAnyCover() {
        for (Direction facing : GTUtil.DIRECTIONS)
            if (getCoverAtSide(facing) != null)
                return true;
        return false;
    }

    default boolean hasCover(Direction facing) {
        return getCoverAtSide(facing) != null;
    }

    default boolean isRemote() {
        return getLevel() == null ? GTCEu.isClientThread() : getLevel().isClientSide;
    }

    default VoxelShape[] addCoverCollisionBoundingBox() {
        double plateThickness = getCoverPlateThickness();
        List<VoxelShape> shapes = new ArrayList<>();
        if (plateThickness > 0.0) {
            for (Direction side : GTUtil.DIRECTIONS) {
                if (getCoverAtSide(side) != null) {
                    var coverBox = getCoverPlateBox(side, plateThickness);
                    shapes.add(coverBox);
                }
            }
        }
        return shapes.toArray(VoxelShape[]::new);
    }

    static boolean doesCoverCollide(Direction side, List<VoxelShape> collisionBox, double plateThickness) {
        if (side == null) {
            return false;
        }

        if (plateThickness > 0.0) {
            var coverPlateBox = getCoverPlateBox(side, plateThickness);
            var aabbs = coverPlateBox.toAabbs();
            for (AABB aabb : aabbs) {
                if (Shapes.collide(side.getAxis(), aabb, collisionBox, plateThickness) < plateThickness) {
                    return true;
                }

            }
        }
        return false;
    }

    @Nullable
    static Direction rayTraceCoverableSide(ICoverable coverable, Player player) {
        BlockHitResult rayTrace = (BlockHitResult) player.pick(player.getBlockReach(), 0, false);
        if (rayTrace.getType() == HitResult.Type.MISS) {
            return null;
        }
        return traceCoverSide(rayTrace);
    }

    class PrimaryBoxData {

        public final boolean usePlacementGrid;

        public PrimaryBoxData(boolean usePlacementGrid) {
            this.usePlacementGrid = usePlacementGrid;
        }
    }

    @Nullable
    static Direction traceCoverSide(BlockHitResult result) {
        return determineGridSideHit(result);
    }

    @Nullable
    static Direction determineGridSideHit(BlockHitResult result) {
        if (result == null) return null;
        return GTUtil.determineWrenchingSide(result.getDirection(),
                (float) (result.getLocation().x - result.getBlockPos().getX()),
                (float) (result.getLocation().y - result.getBlockPos().getY()),
                (float) (result.getLocation().z - result.getBlockPos().getZ()));
    }

    static VoxelShape getCoverPlateBox(Direction side, double plateThickness) {
        return switch (side) {
            case UP -> Shapes.box(0.0, 1.0 - plateThickness, 0.0, 1.0, 1.0, 1.0);
            case DOWN -> Shapes.box(0.0, 0.0, 0.0, 1.0, plateThickness, 1.0);
            case NORTH -> Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, plateThickness);
            case SOUTH -> Shapes.box(0.0, 0.0, 1.0 - plateThickness, 1.0, 1.0, 1.0);
            case WEST -> Shapes.box(0.0, 0.0, 0.0, plateThickness, 1.0, 1.0);
            case EAST -> Shapes.box(1.0 - plateThickness, 0.0, 0.0, 1.0, 1.0, 1.0);
        };
    }

    static boolean canPlaceCover(CoverDefinition coverDef, ICoverable coverable) {
        for (Direction facing : GTUtil.DIRECTIONS) {
            if (coverable.canPlaceCoverOnSide(coverDef, facing)) {
                var cover = coverDef.createCoverBehavior(coverable, facing);
                if (cover.canAttach()) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nullable
    @Override
    default BlockState getBlockAppearance(BlockState state, BlockAndTintGetter level, BlockPos pos, Direction side,
                                          BlockState sourceState, BlockPos sourcePos) {
        if (hasCover(side)) {
            return getCoverAtSide(side).getAppearance(sourceState, sourcePos);
        }
        return null;
    }

    @SuppressWarnings("unused")
    default Data serializeCoverData(CoverBehavior coverBehavior) {
        var uid = new ListData();
        uid.add(GTRegistries.COVERS.dataCodec(), coverBehavior.coverDefinition);
        uid.addByte((byte) coverBehavior.attachedSide.ordinal());
        return uid;
    }

    @SuppressWarnings("unused")
    default CoverBehavior deserializeCoverData(Data uid, int dataVersion) {
        return GTDataFixer.decodeCover(this, uid, dataVersion);
    }

    @SuppressWarnings("unused")
    default void serializeCoverBuffer(FriendlyByteBuf buf, CoverBehavior coverBehavior) {
        GTRegistries.COVERS.streamCodec().encode(buf, coverBehavior.coverDefinition);
        buf.writeEnum(coverBehavior.attachedSide);
    }

    @SuppressWarnings("unused")
    default CoverBehavior deserializeCoverBuffer(FriendlyByteBuf buf) {
        var definition = GTRegistries.COVERS.streamCodec().decode(buf);
        return definition.createCoverBehavior(this, buf.readEnum(Direction.class));
    }
}
