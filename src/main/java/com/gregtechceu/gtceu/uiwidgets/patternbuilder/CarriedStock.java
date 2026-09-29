package com.gregtechceu.gtceu.uiwidgets.patternbuilder;

import com.gregtechceu.gtceu.api.machine.multiblockpro.PlayerSupply;
import com.gregtechceu.gtceu.common.network.GTNetwork;
import com.gregtechceu.gtceu.common.network.packets.CPacketCarriedStock;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import it.unimi.dsi.fastutil.objects.Reference2LongOpenHashMap;

@OnlyIn(Dist.CLIENT)
public final class CarriedStock {

    private static Reference2LongOpenHashMap<Item> counts = new Reference2LongOpenHashMap<>();
    private static int version;

    private CarriedStock() {}

    public static boolean request() {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        counts = PlayerSupply.of(player, false).count();
        version++;
        GTNetwork.NETWORK.sendToServer(new CPacketCarriedStock());
        return true;
    }

    public static long get(Item item) {
        return counts.getLong(item);
    }

    public static int version() {
        return version;
    }

    public static void accept(Reference2LongOpenHashMap<Item> received) {
        counts = received;
        version++;
    }

    public static void clear() {
        counts = new Reference2LongOpenHashMap<>();
        version++;
    }
}
