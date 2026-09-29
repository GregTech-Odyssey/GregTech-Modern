package com.gregtechceu.gtceu.api.machine.multiblockpro;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;
import net.minecraftforge.items.wrapper.PlayerOffhandInvWrapper;

import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.ArrayList;
import java.util.List;

public final class PlayerSupply {

    private static final String BACKPACK_MOD = "sophisticatedbackpacks";

    private final List<IItemHandler> handlers;
    private final Reference2ObjectOpenHashMap<Item, int[]> cursors = new Reference2ObjectOpenHashMap<>();

    private PlayerSupply(List<IItemHandler> handlers) {
        this.handlers = handlers;
    }

    public static PlayerSupply of(Player player, boolean backpacks) {
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
        return new PlayerSupply(handlers);
    }

    private static boolean isBackpack(ItemStack stack) {
        return !stack.isEmpty() && BACKPACK_MOD.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace());
    }

    private static boolean usable(ItemStack stack) {
        return !stack.isEmpty() && !stack.hasTag();
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

    public boolean take(Item item) {
        var cursor = cursors.computeIfAbsent(item, k -> new int[2]);
        for (int h = cursor[0]; h < handlers.size(); h++) {
            var handler = handlers.get(h);
            for (int slot = h == cursor[0] ? cursor[1] : 0; slot < handler.getSlots(); slot++) {
                var stack = handler.getStackInSlot(slot);
                if (!usable(stack) || !stack.is(item)) continue;
                if (handler.extractItem(slot, 1, false).isEmpty()) continue;
                cursor[0] = h;
                cursor[1] = slot;
                return true;
            }
        }
        cursor[0] = handlers.size();
        return false;
    }

    private static final class Curios {

        private static void equipped(Player player, List<IItemHandler> containers) {
            CuriosApi.getCuriosInventory(player).ifPresent(curios -> containers.add(curios.getEquippedCurios()));
        }
    }
}
