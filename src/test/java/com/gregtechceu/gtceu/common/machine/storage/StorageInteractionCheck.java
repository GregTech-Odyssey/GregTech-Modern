package com.gregtechceu.gtceu.common.machine.storage;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.targets.ForgeDataUserdevLaunchHandler;
import net.minecraftforge.items.ItemStackHandler;

import java.util.UUID;

/** Run with {@code gradlew storageInteractionCheck}; checks display hits, held interactions and fluid conservation. */
public final class StorageInteractionCheck {

    public static void main(String[] args) throws ReflectiveOperationException {
        checkDisplay();
        checkRepeats();
        bootstrapRegistries();
        checkContainers();
        System.out.println("StorageInteractionCheck PASS");
    }

    private static void bootstrapRegistries() throws ReflectiveOperationException {
        // This standalone check needs real buckets and capabilities, without starting Forge networking or a world.
        var bootstrap = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrap.setAccessible(true);
        bootstrap.setBoolean(null, true);
        SharedConstants.tryDetectVersion();
        var dist = FMLLoader.class.getDeclaredField("dist");
        dist.setAccessible(true);
        dist.set(null, Dist.DEDICATED_SERVER);
        var handler = FMLLoader.class.getDeclaredField("commonLaunchHandler");
        handler.setAccessible(true);
        handler.set(null, new ForgeDataUserdevLaunchHandler());
        BuiltInRegistries.bootStrap();
    }

    private static void checkDisplay() {
        for (var pos : new BlockPos[] { BlockPos.ZERO, new BlockPos(-13, 72, -29) }) {
            for (var front : Direction.values()) {
                var center = Vec3.atCenterOf(pos).relative(front, 0.5);
                check(StorageInteraction.isDisplayHit(pos, front, new BlockHitResult(center, front, pos, false)), "display center");
                check(!StorageInteraction.isDisplayHit(pos, front, new BlockHitResult(center, front.getOpposite(), pos, false)), "other face");
                check(!StorageInteraction.isDisplayHit(pos, front, new BlockHitResult(center, front, pos.relative(front), false)), "other block");
                check(!StorageInteraction.isDisplayHit(pos, front, BlockHitResult.miss(center, front, pos)), "miss");
                for (var axis : Direction.Axis.values()) {
                    if (axis == front.getAxis()) continue;
                    var along = Direction.get(Direction.AxisDirection.POSITIVE, axis);
                    for (double edge : new double[] { -0.375, 0.375 }) {
                        var boundary = center.relative(along, edge);
                        check(StorageInteraction.isDisplayHit(pos, front, new BlockHitResult(boundary, front, pos, false)), "glass boundary");
                        var outside = center.relative(along, edge * 1.001);
                        check(!StorageInteraction.isDisplayHit(pos, front, new BlockHitResult(outside, front, pos, false)), "casing outside glass");
                    }
                }
            }
        }
    }

    private static void checkRepeats() {
        var interaction = new StorageInteraction();
        var player = new UUID(0, 1);
        var other = new UUID(0, 2);
        var main = InteractionHand.MAIN_HAND;
        var off = InteractionHand.OFF_HAND;
        check(!interaction.recordUse(player, main, 100), "first use must not empty inventory");
        check(interaction.recordUse(player, main, 104), "held use at vanilla four tick interval");
        check(!interaction.recordUse(player, main, 104), "duplicate use in same tick");
        check(!interaction.recordUse(player, off, 108), "other hand");
        check(!interaction.recordUse(other, off, 112), "other player");
        check(!interaction.recordUse(other, off, 119), "released use");
        check(interaction.recordUse(other, off, 123), "resumed hold");
        check(!new StorageInteraction().recordUse(other, off, 124), "other block");
        check(interaction.claimExtraction(0), "first extraction");
        check(!interaction.claimExtraction(0), "duplicate extraction");
        check(!interaction.claimExtraction(1) && !interaction.claimExtraction(2), "held extraction cooldown");
        check(interaction.claimExtraction(3) && interaction.claimExtraction(6), "continuous extraction");

        check(!interaction.recordGtceuUse(player, 1000), "first GTCEu empty-hand click");
        check(interaction.recordGtceuUse(player, 1000), "GTCEu allows a double click in the same tick");
        check(interaction.recordGtceuUse(player, 1299), "GTCEu double click inside 300 milliseconds");
        check(!interaction.recordGtceuUse(player, 1599), "GTCEu double click expires at 300 milliseconds");
        check(!interaction.recordGtceuUse(other, 1600), "GTCEu does not share double clicks between players");
        check(!new StorageInteraction().recordGtceuUse(other, 1601), "GTCEu double clicks stay on one block");
    }

    private static void checkContainers() {
        var tank = new FluidTank(10_000);
        var inventory = new ItemStackHandler(36);
        var water = new ItemStack(Items.WATER_BUCKET);
        var emptied = StorageInteraction.transferContainers(water, tank, inventory, null, 1, false);
        check(emptied.is(Items.BUCKET) && tank.getFluidAmount() == 1000, "right click deposits one bucket");
        check(water.is(Items.WATER_BUCKET), "input container is not modified");
        check(StorageInteraction.transferContainers(emptied, tank, inventory, null, 1, false) == emptied && tank.getFluidAmount() == 1000,
                "right click with an empty container cannot extract");
        check(StorageInteraction.transferContainers(water, tank, inventory, null, 1, true) == water && tank.getFluidAmount() == 1000,
                "left click with a full container cannot deposit");
        var lava = new ItemStack(Items.LAVA_BUCKET);
        check(StorageInteraction.transferContainers(lava, tank, inventory, null, 1, false) == lava && tank.getFluidAmount() == 1000,
                "different fluid cannot enter");
        var noContainer = ItemStack.EMPTY;
        check(StorageInteraction.transferContainers(noContainer, tank, inventory, null, 1, true).isEmpty() && tank.getFluidAmount() == 1000,
                "no extraction without a container");
        var stone = new ItemStack(Items.STONE);
        check(StorageInteraction.transferContainers(stone, tank, inventory, null, 1, true) == stone && tank.getFluidAmount() == 1000,
                "ordinary items cannot extract fluid");
        var filled = StorageInteraction.transferContainers(emptied, tank, inventory, null, 1, true);
        check(filled.is(Items.WATER_BUCKET) && tank.isEmpty(), "left click fills one bucket");

        tank.fill(new FluidStack(Fluids.WATER, 3500), IFluidHandler.FluidAction.EXECUTE);
        var buckets = new ItemStack(Items.BUCKET, 4);
        var remainder = StorageInteraction.transferContainers(buckets, tank, inventory, null, 4, true);
        check(remainder.is(Items.BUCKET) && remainder.getCount() == 1 && tank.getFluidAmount() == 500, "shift fills only available whole buckets");
        check(count(inventory, Items.WATER_BUCKET) == 3, "filled containers are stowed");
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            inventory.setStackInSlot(slot, StorageInteraction.transferContainers(stack, tank, inventory, null, stack.getCount(), false));
        }
        check(tank.getFluidAmount() == 3500 && count(inventory, Items.WATER_BUCKET) == 0 && count(inventory, Items.BUCKET) == 3,
                "bulk deposit preserves fluid and containers");

        var fullInventory = new ItemStackHandler(1);
        fullInventory.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        var pair = new ItemStack(Items.BUCKET, 2);
        var blocked = StorageInteraction.transferContainers(pair, tank, fullInventory, null, 2, true);
        check(blocked == pair && tank.getFluidAmount() == 3500, "no fluid consumed when filled container cannot be stowed");
        var fullTank = new FluidTank(1000);
        fullTank.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
        check(StorageInteraction.transferContainers(water, fullTank, inventory, null, 1, false) == water && fullTank.getFluidAmount() == 1000,
                "full tank preserves filled container");
    }

    private static int count(ItemStackHandler inventory, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (stack.is(item)) count += stack.getCount();
        }
        return count;
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
