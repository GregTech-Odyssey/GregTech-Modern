package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.item.LampBlockItem;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;
import net.minecraftforge.items.wrapper.PlayerOffhandInvWrapper;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEItemKey;
import de.mari_023.ae2wtlib.wct.CraftingTerminalHandler;
import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import me.kall.duplicationless.ext.RegistryEntry;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

public final class PlayerSupply {

    private static final String BACKPACK_MOD = "sophisticatedbackpacks";

    private final List<IItemHandler> handlers;
    private final Reference2ObjectOpenHashMap<Item, int[]> cursors = new Reference2ObjectOpenHashMap<>();
    @Nullable
    private final IStorageService network;
    @Nullable
    private final IActionSource source;

    private PlayerSupply(List<IItemHandler> handlers, @Nullable IStorageService network, @Nullable IActionSource source) {
        this.handlers = handlers;
        this.network = network;
        this.source = source;
    }

    public static PlayerSupply of(Player player, boolean backpacks) {
        return new PlayerSupply(handlers(player, backpacks), null, null);
    }

    public static PlayerSupply of(ServerPlayer player, boolean backpacks, boolean network) {
        var storage = network ? Network.storage(player) : null;
        return new PlayerSupply(handlers(player, backpacks), storage, storage == null ? null : IActionSource.ofPlayer(player));
    }

    public static boolean hasNetwork(ServerPlayer player) {
        return Network.storage(player) != null;
    }

    public boolean usesNetwork() {
        return network != null;
    }

    private static List<IItemHandler> handlers(Player player, boolean backpacks) {
        var handlers = new ArrayList<IItemHandler>();
        var main = new PlayerMainInvWrapper(player.getInventory());
        var offhand = new PlayerOffhandInvWrapper(player.getInventory());
        handlers.add(main);
        handlers.add(offhand);
        if (backpacks) {
            var containers = new ArrayList<IItemHandler>();
            containers.add(main);
            containers.add(offhand);
            if (GTCEu.Mods.isCuriosLoaded()) Curios.equipped(player, containers);
            for (var container : containers) {
                for (int slot = 0; slot < container.getSlots(); slot++) {
                    var stack = container.getStackInSlot(slot);
                    if (isBackpack(stack)) stack.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handlers::add);
                }
            }
        }
        return handlers;
    }

    private static boolean isBackpack(ItemStack stack) {
        return !stack.isEmpty() && BACKPACK_MOD.equals(RegistryEntry.get(stack.getItem()).getNamespace());
    }

    private static boolean usable(ItemStack stack) {
        return !stack.isEmpty() && (!stack.hasTag() || stack.getItem() instanceof LampBlockItem);
    }

    public Reference2LongOpenHashMap<Item> count() {
        var counts = new Reference2LongOpenHashMap<Item>();
        for (var handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                var stack = handler.getStackInSlot(slot);
                if (usable(stack)) counts.addTo(stack.getItem(), stack.getCount());
            }
        }
        return counts;
    }

    public void countNetwork(Reference2LongOpenHashMap<Item> counts, Iterable<Item> wanted) {
        if (network == null) return;
        var available = network.getCachedInventory();
        for (var item : wanted) {
            long amount = available.get(AEItemKey.of(item));
            if (amount > 0) counts.addTo(item, amount);
        }
    }

    public void giveBack(Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (network != null && !stack.hasTag()) {
            long inserted = network.getInventory().insert(AEItemKey.of(stack), stack.getCount(), Actionable.MODULATE, source);
            stack.shrink((int) inserted);
            if (stack.isEmpty()) return;
        }
        if (!player.addItem(stack)) player.drop(stack, false);
    }

    public ItemStack take(Item item) {
        var taken = takeCarried(item);
        if (!taken.isEmpty() || network == null) return taken;
        return network.getInventory().extract(AEItemKey.of(item), 1, Actionable.MODULATE, source) > 0 ? new ItemStack(item) : ItemStack.EMPTY;
    }

    private ItemStack takeCarried(Item item) {
        var cursor = cursors.computeIfAbsent(item, k -> new int[2]);
        for (int h = cursor[0]; h < handlers.size(); h++) {
            var handler = handlers.get(h);
            for (int slot = h == cursor[0] ? cursor[1] : 0; slot < handler.getSlots(); slot++) {
                var stack = handler.getStackInSlot(slot);
                if (!usable(stack) || !stack.is(item)) continue;
                var taken = handler.extractItem(slot, 1, false);
                if (taken.isEmpty()) continue;
                cursor[0] = h;
                cursor[1] = slot;
                return taken;
            }
        }
        cursor[0] = handlers.size();
        return ItemStack.EMPTY;
    }

    private static final class Network {

        @Nullable
        private static IStorageService storage(ServerPlayer player) {
            if (!GTCEu.isModLoaded(GTValues.MODID_AE2WTLIB)) return null;
            return Wireless.storage(player);
        }
    }

    private static final class Wireless {

        @Nullable
        private static IStorageService storage(ServerPlayer player) {
            var handler = CraftingTerminalHandler.getCraftingTerminalHandler(player);
            if (handler.getLocator() == null) return null;
            var grid = handler.getTargetGrid();
            return grid == null ? null : grid.getStorageService();
        }
    }

    private static final class Curios {

        private static void equipped(Player player, List<IItemHandler> containers) {
            CuriosApi.getCuriosInventory(player).ifPresent(curios -> containers.add(curios.getEquippedCurios()));
        }
    }
}
