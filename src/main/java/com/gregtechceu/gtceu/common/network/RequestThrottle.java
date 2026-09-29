package com.gregtechceu.gtceu.common.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RequestThrottle {

    private static final List<RequestThrottle> ALL = new ArrayList<>();

    public static final RequestThrottle CARRIED_STOCK = new RequestThrottle(10);

    static {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, PlayerEvent.PlayerLoggedOutEvent.class, RequestThrottle::onLoggedOut);
    }

    private final int interval;
    private final Object2IntOpenHashMap<UUID> next = new Object2IntOpenHashMap<>();

    public RequestThrottle(int interval) {
        this.interval = interval;
        synchronized (ALL) {
            ALL.add(this);
        }
    }

    public boolean tryAcquire(ServerPlayer player) {
        int now = player.server.getTickCount();
        var id = player.getUUID();
        if (next.containsKey(id) && now < next.getInt(id)) return false;
        next.put(id, now + interval);
        return true;
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        var id = event.getEntity().getUUID();
        synchronized (ALL) {
            for (var throttle : ALL) throttle.next.removeInt(id);
        }
    }
}
