package com.gregtechceu.gtceu.utils;

import com.gregtechceu.gtceu.api.transfer.fluid.FluidHandlerList;
import com.gregtechceu.gtceu.api.transfer.fluid.ICustomFluidStackHandler;
import com.gregtechceu.gtceu.api.transfer.item.ICustomItemStackHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;

import com.gto.fastcollection.O2IOpenCustomCacheHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

public class GTTransferUtils {

    /**
     * Gets the FluidHandler from the adjacent block on the side connected to the caller
     *
     * @param level  Level of caller
     * @param pos    BlockPos of caller
     * @param facing Direction to get the FluidHandler from
     * @return LazyOpt of the IFluidHandler described above
     */
    public static LazyOptional<IFluidHandler> getAdjacentFluidHandler(Level level, BlockPos pos, Direction facing) {
        return FluidUtil.getFluidHandler(level, pos.relative(facing), facing.getOpposite());
    }

    // Same as above, but returns the presence
    public static boolean hasAdjacentFluidHandler(Level level, BlockPos pos, Direction facing) {
        return getAdjacentFluidHandler(level, pos, facing).isPresent();
    }

    /**
     * Get the ItemHandler Capability from the given block
     * 
     * @param level Level of block
     * @param pos   BlockPos of block
     * @param side  Side of block
     * @return LazyOpt of ItemHandler of given block
     */
    public static LazyOptional<IItemHandler> getItemHandler(Level level, BlockPos pos, @Nullable Direction side) {
        BlockState state = level.getBlockState(pos);
        if (state.hasBlockEntity()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                return blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side);
            }
        }
        return LazyOptional.empty();
    }

    // Same as getAdjacentFluidHandler, but for ItemHandler
    public static LazyOptional<IItemHandler> getAdjacentItemHandler(Level level, BlockPos pos, Direction facing) {
        return getItemHandler(level, pos.relative(facing), facing.getOpposite());
    }

    // Same as above, but returns the presence
    public static boolean hasAdjacentItemHandler(Level level, BlockPos pos, Direction facing) {
        return getAdjacentItemHandler(level, pos, facing).isPresent();
    }

    /**
     * Transfer fluids with the given filter
     * 
     * @param source        FluidHandler to drain from
     * @param dest          FluidHandler to fill
     * @param filter        Filter to test FluidStacks
     * @param transferLimit Amount to transfer
     * @return Remaining amount that was not transferred
     */
    public static int transferFluidsFiltered(@NotNull IFluidHandler source, @NotNull IFluidHandler dest,
                                             @NotNull Predicate<FluidStack> filter, int transferLimit) {
        int toTransfer = transferLimit;
        for (int i = 0; i < source.getTanks(); i++) {
            FluidStack fluid = source.getFluidInTank(i);
            if (fluid.isEmpty() || !filter.test(fluid)) continue;

            fluid = new FluidStack(fluid, toTransfer);
            var transferred = FluidUtil.tryFluidTransfer(dest, source, fluid, true);
            toTransfer -= transferred.getAmount();
            if (toTransfer <= 0) break;
        }
        return transferLimit - toTransfer;
    }

    // Override to transfer as much as possible
    public static void transferFluidsFiltered(@NotNull IFluidHandler source, @NotNull IFluidHandler dest,
                                              @NotNull Predicate<FluidStack> filter) {
        transferFluidsFiltered(source, dest, filter, Integer.MAX_VALUE);
    }

    /**
     * Transfer items with the given filter
     * 
     * @param source        ItemHandler to extract from
     * @param dest          ItemHandler to insert into
     * @param filter        Filter to test ItemStacks
     * @param transferLimit Maximum amount to transfer
     * @return the amount that was transferred
     */
    public static int transferItemsFiltered(@NotNull IItemHandler source, @NotNull IItemHandler dest,
                                            @NotNull Predicate<ItemStack> filter, int transferLimit) {
        if (transferLimit <= 0) return 0;
        int toTransfer = transferLimit;
        for (int i = 0; i < source.getSlots(); i++) {
            toTransfer -= transferItemsFromSlot(source, i, dest, filter, toTransfer);
            if (toTransfer <= 0) break;
        }
        return transferLimit - toTransfer;
    }

    /**
     * Transfers items from one source slot. The destination simulation is performed through
     * {@link ICustomItemStackHandler#insertItemStacked(ItemStack, boolean)} when available so handler-wide slot
     * restrictions are represented correctly. Any remainder produced by the real insertion is returned to the source.
     */
    public static int transferItemsFromSlot(@NotNull IItemHandler source, int sourceSlot,
                                            @NotNull IItemHandler dest, @NotNull Predicate<ItemStack> filter,
                                            int transferLimit) {
        if (transferLimit <= 0) return 0;
        var simulatedExtract = source.extractItem(sourceSlot, transferLimit, true);
        if (simulatedExtract.isEmpty() || !filter.test(simulatedExtract)) return 0;

        var simulatedRemainder = insertItemStacked(dest, simulatedExtract, true);
        int canInsert = simulatedExtract.getCount() - simulatedRemainder.getCount();
        if (canInsert <= 0) return 0;

        var extracted = source.extractItem(sourceSlot, canInsert, false);
        if (extracted.isEmpty()) return 0;
        if (!filter.test(extracted) || !ItemStack.isSameItemSameTags(simulatedExtract, extracted)) {
            returnItemToSource(source, sourceSlot, extracted);
            return 0;
        }

        int extractedCount = extracted.getCount();
        var remainder = insertItemStacked(dest, extracted, false);
        int remainderCount = Math.min(extractedCount, remainder.getCount());
        if (!remainder.isEmpty()) {
            returnItemToSource(source, sourceSlot, remainder);
        }
        return extractedCount - remainderCount;
    }

    /**
     * Uses a custom handler's transaction-aware stacked insertion when possible.
     */
    @NotNull
    public static ItemStack insertItemStacked(@NotNull IItemHandler handler, @NotNull ItemStack stack,
                                              boolean simulate) {
        if (handler instanceof ICustomItemStackHandler customHandler) {
            return customHandler.insertItemStacked(stack, simulate);
        }
        return ItemHandlerHelper.insertItemStacked(handler, stack, simulate);
    }

    /**
     * Returns an uninserted stack to its source, bypassing external side restrictions for custom handlers.
     */
    @NotNull
    public static ItemStack insertItemBack(@NotNull IItemHandler source, int preferredSlot,
                                           @NotNull ItemStack stack) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        var remaining = stack;
        int slots = source.getSlots();
        if (source instanceof ICustomItemStackHandler customHandler) {
            remaining = customHandler.insertItemInternal(preferredSlot, remaining, false);
            for (int slot = 0; slot < slots && !remaining.isEmpty(); slot++) {
                if (slot != preferredSlot) {
                    remaining = customHandler.insertItemInternal(slot, remaining, false);
                }
            }
        } else {
            remaining = source.insertItem(preferredSlot, remaining, false);
            if (!remaining.isEmpty()) {
                remaining = ItemHandlerHelper.insertItemStacked(source, remaining, false);
            }
        }
        if (!remaining.isEmpty() && source instanceof IItemHandlerModifiable modifiable) {
            remaining = forceRestoreItem(modifiable, preferredSlot, remaining);
            for (int slot = 0; slot < slots && !remaining.isEmpty(); slot++) {
                if (slot != preferredSlot) {
                    remaining = forceRestoreItem(modifiable, slot, remaining);
                }
            }
        }
        return remaining;
    }

    private static ItemStack forceRestoreItem(IItemHandlerModifiable source, int slot, ItemStack stack) {
        var existing = source.getStackInSlot(slot);
        int existingCount = existing.getCount();
        if (!existing.isEmpty() && !ItemStack.isSameItemSameTags(existing, stack)) return stack;

        long restoredCount = (long) existingCount + stack.getCount();
        int count = (int) Math.min(Integer.MAX_VALUE, restoredCount);
        source.setStackInSlot(slot, (existing.isEmpty() ? stack : existing).copyWithCount(count));

        var restored = source.getStackInSlot(slot);
        int inserted = ItemStack.isSameItemSameTags(restored, stack) ? restored.getCount() - existingCount : 0;
        if (inserted <= 0) return stack;
        if (inserted >= stack.getCount()) return ItemStack.EMPTY;
        return stack.copyWithCount(stack.getCount() - inserted);
    }

    public static void returnItemToSource(@NotNull IItemHandler source, int sourceSlot, @NotNull ItemStack stack) {
        var unreturned = insertItemBack(source, sourceSlot, stack);
        if (!unreturned.isEmpty()) {
            throw new IllegalStateException("Source item handler rejected " + unreturned.getCount() +
                    " items while rolling back a transfer");
        }
    }

    // Override to transfer as much as possible
    public static void transferItemsFiltered(@NotNull IItemHandler source, @NotNull IItemHandler dest,
                                             @NotNull Predicate<ItemStack> filter) {
        transferItemsFiltered(source, dest, filter, Integer.MAX_VALUE);
    }

    public static void moveInventoryItems(IItemHandlerModifiable sourceInventory,
                                          IItemHandlerModifiable targetInventory) {
        transferItemsFiltered(sourceInventory, targetInventory, GTUtil.FAVORABLE, Integer.MAX_VALUE);
    }

    /**
     * Simulates the insertion of items into a target inventory, then optionally performs the insertion.
     * <br />
     * <br />
     * Simulating will not modify any of the input parameters. Insertion will either succeed completely, or fail
     * without modifying anything.
     * This method should be called with {@code simulate} {@code true} first, then {@code simulate} {@code false},
     * only if it returned {@code true}.
     *
     * @param handler  the target inventory
     * @param simulate whether to simulate ({@code true}) or actually perform the insertion ({@code false})
     * @param items    the items to insert into {@code handler}.
     * @return {@code true} if the insertion succeeded, {@code false} otherwise.
     */
    public static boolean addItemsToItemHandler(final IItemHandlerModifiable handler,
                                                final boolean simulate,
                                                final List<ItemStack> items) {
        // determine if there is sufficient room to insert all items into the target inventory
        if (simulate) {
            OverlayedItemHandler overlayedItemHandler = new OverlayedItemHandler(handler);
            Object2IntMap<ItemStack> stackKeyMap = new O2IOpenCustomCacheHashMap<>(ItemStackHashStrategy.ITEM_AND_TAG);

            for (Object2IntMap.Entry<ItemStack> entry : stackKeyMap.object2IntEntrySet()) {
                int amountToInsert = entry.getIntValue();
                int amount = overlayedItemHandler.insertStackedItemStack(entry.getKey(), amountToInsert);
                if (amount > 0) {
                    return false;
                }
            }
            return true;
        }

        // perform the merge.
        items.forEach(stack -> ItemHandlerHelper.insertItemStacked(handler, stack, false));
        return true;
    }

    public static int fillFluidAccountNotifiableList(IFluidHandler fluidHandler, FluidStack stack, FluidAction action) {
        if (stack.isEmpty()) return 0;
        if (fluidHandler instanceof FluidHandlerList handlerList) {
            var copied = stack.copy();
            for (var handler : handlerList.handlers) {
                var candidate = copied.copy();
                if (handler instanceof ICustomFluidStackHandler notifiable) {
                    copied.shrink(notifiable.fillInternal(candidate, action));
                } else {
                    copied.shrink(handler.fill(candidate, action));
                }
                if (copied.isEmpty()) break;
            }
            return stack.getAmount() - copied.getAmount();
        }
        return fluidHandler.fill(stack, action);
    }

    public static FluidStack drainFluidAccountNotifiableList(IFluidHandler fluidHandler, FluidStack stack,
                                                             FluidAction action) {
        if (stack.isEmpty()) return FluidStack.EMPTY;
        if (fluidHandler instanceof FluidHandlerList handlerList) {
            var copied = stack.copy();
            for (var handler : handlerList.handlers) {
                var candidate = copied.copy();
                if (handler instanceof ICustomFluidStackHandler notifiable) {
                    copied.shrink(notifiable.drainInternal(candidate, action).getAmount());
                } else {
                    copied.shrink(handler.drain(candidate, action).getAmount());
                }
                if (copied.isEmpty()) break;
            }
            copied.setAmount(stack.getAmount() - copied.getAmount());
            return copied;
        }
        return fluidHandler.drain(stack, action);
    }

    public static boolean transferExactFluidStack(@NotNull IFluidHandler sourceHandler,
                                                  @NotNull IFluidHandler destHandler, FluidStack fluidStack) {
        int amount = fluidStack.getAmount();
        FluidStack sourceFluid = sourceHandler.drain(fluidStack, FluidAction.SIMULATE);
        if (sourceFluid == FluidStack.EMPTY || sourceFluid.getAmount() != amount) {
            return false;
        }
        int canInsertAmount = destHandler.fill(sourceFluid, FluidAction.SIMULATE);
        if (canInsertAmount == amount) {
            sourceFluid = sourceHandler.drain(sourceFluid, FluidAction.EXECUTE);
            if (sourceFluid != FluidStack.EMPTY && sourceFluid.getAmount() > 0) {
                destHandler.fill(sourceFluid, FluidAction.EXECUTE);
                return true;
            }
        }
        return false;
    }

    /**
     * Inserts items by trying to fill slots with the same item first, and then fill empty slots. <br>
     * Seems like close to duplicate behavior of {@link ItemHandlerHelper#insertItemStacked}
     */
    public static ItemStack insertItem(IItemHandler handler, ItemStack stack, boolean simulate) {
        if (handler == null || stack.isEmpty()) {
            return stack;
        }
        if (!stack.isStackable()) {
            return insertToEmpty(handler, stack, simulate);
        }

        IntList emptySlots = new IntArrayList();
        int slots = handler.getSlots();

        for (int i = 0; i < slots; i++) {
            ItemStack slotStack = handler.getStackInSlot(i);
            if (slotStack.isEmpty()) {
                emptySlots.add(i);
            } else if (ItemHandlerHelper.canItemStacksStack(stack, slotStack)) {
                stack = handler.insertItem(i, stack, simulate);
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }

        for (int slot : emptySlots) {
            stack = handler.insertItem(slot, stack, simulate);
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    /**
     * Only inerts to empty slots. Perfect for not stackable items
     */
    public static ItemStack insertToEmpty(IItemHandler handler, ItemStack stack, boolean simulate) {
        if (handler == null || stack.isEmpty()) {
            return stack;
        }
        int slots = handler.getSlots();
        for (int i = 0; i < slots; i++) {
            ItemStack slotStack = handler.getStackInSlot(i);
            if (slotStack.isEmpty()) {
                stack = handler.insertItem(i, stack, simulate);
                if (stack.isEmpty()) {
                    return ItemStack.EMPTY;
                }
            }
        }
        return stack;
    }
}
