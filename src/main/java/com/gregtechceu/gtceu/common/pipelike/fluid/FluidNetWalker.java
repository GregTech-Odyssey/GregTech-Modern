package com.gregtechceu.gtceu.common.pipelike.fluid;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.FluidPipeProperties;
import com.gregtechceu.gtceu.api.pipenet.PipeNetWalker;
import com.gregtechceu.gtceu.common.blockentity.FluidPipeBlockEntity;
import com.gregtechceu.gtceu.common.cover.FluidFilterCover;
import com.gregtechceu.gtceu.common.cover.ShutterCover;
import com.gregtechceu.gtceu.common.cover.data.FilterMode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Predicate;

public class FluidNetWalker extends PipeNetWalker<FluidPipeBlockEntity, FluidPipeProperties, FluidPipeNet> {

    public static List<FluidRoutePath> createNetData(FluidPipeNet pipeNet, BlockPos sourcePipe, Direction sourceFacing) {
        if (!(pipeNet.getLevel().getBlockEntity(sourcePipe) instanceof FluidPipeBlockEntity)) {
            return null;
        }
        try {
            FluidNetWalker walker = new FluidNetWalker(pipeNet, sourcePipe, 1, new ArrayList<>(), null);
            walker.sourcePipe = sourcePipe;
            walker.facingToHandler = sourceFacing;
            walker.traversePipeNet();
            return walker.inventories;
        } catch (Exception e) {
            GTCEu.LOGGER.error("error while create net data for FluidPipeNet", e);
        }
        return null;
    }

    private FluidPipeProperties minProperties;
    private final List<FluidRoutePath> inventories;
    private final List<Predicate<FluidStack>> filters = new ArrayList<>();
    private final EnumMap<Direction, List<Predicate<FluidStack>>> nextFilters = new EnumMap<>(Direction.class);
    private BlockPos sourcePipe;
    private Direction facingToHandler;

    protected FluidNetWalker(FluidPipeNet world, BlockPos sourcePipe, int distance, List<FluidRoutePath> inventories,
                             FluidPipeProperties properties) {
        super(world, sourcePipe, distance);
        this.inventories = inventories;
        this.minProperties = properties;
    }

    @NotNull
    @Override
    protected PipeNetWalker<FluidPipeBlockEntity, FluidPipeProperties, FluidPipeNet> createSubWalker(FluidPipeNet pipeNet,
                                                                                                     Direction facingToNextPos,
                                                                                                     BlockPos nextPos,
                                                                                                     int walkedBlocks) {
        FluidNetWalker walker = new FluidNetWalker(pipeNet, nextPos, walkedBlocks, inventories, minProperties);
        walker.facingToHandler = facingToHandler;
        walker.sourcePipe = sourcePipe;
        walker.filters.addAll(filters);
        List<Predicate<FluidStack>> moreFilters = nextFilters.get(facingToNextPos);
        if (moreFilters != null && !moreFilters.isEmpty()) {
            walker.filters.addAll(moreFilters);
        }
        return walker;
    }

    @Override
    protected Class<FluidPipeBlockEntity> getBasePipeClass() {
        return FluidPipeBlockEntity.class;
    }

    @Override
    protected void checkPipe(FluidPipeBlockEntity pipeTile, BlockPos pos) {
        for (List<Predicate<FluidStack>> filters : nextFilters.values()) {
            if (!filters.isEmpty()) {
                this.filters.addAll(filters);
            }
        }
        nextFilters.clear();
        FluidPipeProperties pipeProperties = pipeTile.getNodeData();
        if (minProperties == null) {
            minProperties = pipeProperties;
        } else {
            minProperties = new FluidPipeProperties(Math.min(minProperties.getThroughput(), pipeProperties.getThroughput()));
        }
    }

    @Override
    protected void checkNeighbour(FluidPipeBlockEntity pipeTile, BlockPos pipePos, Direction faceToNeighbour,
                                  @Nullable BlockEntity neighbourTile) {
        if (neighbourTile == null || (pipePos.equals(sourcePipe) && faceToNeighbour == facingToHandler)) {
            return;
        }
        LazyOptional<IFluidHandler> handler = neighbourTile.getCapability(ForgeCapabilities.FLUID_HANDLER,
                faceToNeighbour.getOpposite());
        if (handler.isPresent()) {
            List<Predicate<FluidStack>> filters = new ArrayList<>(this.filters);
            List<Predicate<FluidStack>> moreFilters = nextFilters.get(faceToNeighbour);
            if (moreFilters != null && !moreFilters.isEmpty()) {
                filters.addAll(moreFilters);
            }
            inventories.add(new FluidRoutePath(pipeTile, faceToNeighbour, getWalkedBlocks(), minProperties, filters));
        }
    }

    @Override
    protected boolean isValidPipe(FluidPipeBlockEntity currentPipe, FluidPipeBlockEntity neighbourPipe, BlockPos pipePos,
                                  Direction faceToNeighbour) {
        CoverBehavior thisCover = currentPipe.getCoverContainer().getCoverAtSide(faceToNeighbour);
        CoverBehavior neighbourCover = neighbourPipe.getCoverContainer().getCoverAtSide(faceToNeighbour.getOpposite());
        List<Predicate<FluidStack>> filters = new ArrayList<>();
        if (thisCover instanceof ShutterCover shutter) {
            filters.add(stack -> !shutter.isWorkingEnabled());
        } else if (thisCover instanceof FluidFilterCover fluidFilterCover &&
                fluidFilterCover.getFilterMode() != FilterMode.FILTER_INSERT) {
                    filters.add(fluidFilterCover.getFluidFilter());
                }
        if (neighbourCover instanceof ShutterCover shutter) {
            filters.add(stack -> !shutter.isWorkingEnabled());
        } else if (neighbourCover instanceof FluidFilterCover fluidFilterCover &&
                fluidFilterCover.getFilterMode() != FilterMode.FILTER_EXTRACT) {
                    filters.add(fluidFilterCover.getFluidFilter());
                }
        if (!filters.isEmpty()) {
            nextFilters.put(faceToNeighbour, filters);
        }
        return true;
    }
}
