package com.gregtechceu.gtceu.common.machine.owner;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.Event;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.UsernameCache;

import com.gto.fastcollection.O2OOpenCacheHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public abstract sealed class MachineOwner permits PlayerOwner, FTBOwner {

    public static final Event<AtomicReference<Function<UUID, MachineOwner>>> REGISTER_EVENT = Event.createRegister();

    private static Function<UUID, MachineOwner> machineOwnerGenerator;
    public static final UUID EMPTY = new UUID(0, 0);
    protected static final Map<UUID, MachineOwner> MACHINE_OWNERS = new O2OOpenCacheHashMap<>();
    protected static final Map<UUID, PlayerOwner> PLAYER_OWNERS = new O2OOpenCacheHashMap<>();
    @NotNull
    protected final UUID playerUUID;

    protected MachineOwner(UUID playerUUID) {
        this.playerUUID = playerUUID == null ? EMPTY : playerUUID;
    }

    public abstract UUID getUUID();

    public abstract String getName();

    public abstract Component getTypeDisplayName();

    public static void init() {
        AtomicReference<Function<UUID, MachineOwner>> reference = new AtomicReference<>();
        if (GTCEu.Mods.isFTBTeamsLoaded()) {
            reference.set(FTBOwner::new);
        } else {
            reference.set(PlayerOwner::new);
        }
        REGISTER_EVENT.call(reference);
        machineOwnerGenerator = reference.get();
    }

    public void displayInfo(List<Component> compList) {
        compList.add(Component.translatable("behavior.portable_scanner.machine_ownership", getTypeDisplayName()));
    }

    @UnmodifiableView
    @NotNull
    public abstract Set<UUID> getMembers();

    public boolean isPlayerInTeam(Player player) {
        return isPlayerInTeam(player.getUUID());
    }

    public abstract boolean isPlayerInTeam(UUID playerUUID);

    public boolean isPlayerFriendly(Player player) {
        return isPlayerFriendly(player.getUUID());
    }

    public abstract boolean isPlayerFriendly(UUID playerUUID);

    @Nullable
    public static MachineOwner getOwner(UUID playerUUID) {
        if (playerUUID == null) {
            return null;
        }
        return MACHINE_OWNERS.computeIfAbsent(playerUUID, MachineOwner.machineOwnerGenerator);
    }

    @Nullable
    public static PlayerOwner getPlayerOwner(UUID playerUUID) {
        if (playerUUID == null) {
            return null;
        }
        return PLAYER_OWNERS.computeIfAbsent(playerUUID, PlayerOwner::new);
    }

    public static boolean canOpenOwnerMachine(Player player, MetaMachine machine) {
        if (!ConfigHolder.INSTANCE.machines.onlyOwnerGUI) return true;
        if (player.hasPermissions(ConfigHolder.INSTANCE.machines.ownerOPBypass)) return true;
        var owner = machine.getOwner();
        if (owner == null) return true;
        return owner.isPlayerInTeam(player) || owner.isPlayerFriendly(player);
    }

    public static boolean canBreakOwnerMachine(Player player, MetaMachine machine) {
        if (!ConfigHolder.INSTANCE.machines.onlyOwnerBreak) return true;
        if (player.hasPermissions(ConfigHolder.INSTANCE.machines.ownerOPBypass)) return true;
        var owner = machine.getOwner();
        if (owner == null) return true;
        return owner.isPlayerInTeam(player);
    }

    public static void displayPlayerInfo(List<Component> compList, UUID playerUUID) {
        final var playerName = UsernameCache.getLastKnownUsername(playerUUID);
        var online = "gtceu.tooltip.status.trinary.";
        if (GTCEu.isClientThread()) {
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                online += connection.getOnlinePlayerIds().contains(playerUUID);
            } else {
                online += "unknown";
            }
        } else {
            online += GTCEu.getMinecraftServer().getPlayerList().getPlayer(playerUUID) != null;
        }
        compList.add(Component.translatable("behavior.portable_scanner.player_name", playerName, Component.translatable(online)));
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof MachineOwner that)) return false;
        return playerUUID.equals(that.playerUUID);
    }

    @Override
    public int hashCode() {
        return playerUUID.hashCode();
    }

    @NotNull
    public UUID getPlayerUUID() {
        return this.playerUUID;
    }
}
