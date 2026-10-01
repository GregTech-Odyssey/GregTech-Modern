package com.gregtechceu.gtceu.integration.jade;

import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.uiwidgets.icon.IdleReasonInfo;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;

public final class IdleReasonJade {

    public static final String STATUS_KEY = "IdleStatus";
    public static final String REASON_KEY = "IdleReason";
    public static final String DESC_KEY = "IdleReasonDesc";
    public static final String SEVERITY_KEY = "IdleReasonSeverity";

    private IdleReasonJade() {}

    public static void write(CompoundTag tag, RecipeLogic logic) {
        int status = logic.getStatus();
        if (!IdleReasonInfo.visible(status) || !IdleReasonInfo.available(logic)) return;
        tag.putInt(STATUS_KEY, status);
        if (!(logic.machine.self().getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread()) return;
        var reason = IdleReasonInfo.reasonOf(logic);
        if (reason == null) return;
        tag.putString(REASON_KEY, Component.Serializer.toJson(reason));
        var entry = IdleReasonInfo.lookup(reason);
        if (entry == null) return;
        tag.putByte(SEVERITY_KEY, (byte) entry.severity().ordinal());
        var descKey = IdleReasonInfo.descKey(reason, entry);
        if (descKey != null) tag.putString(DESC_KEY, descKey);
    }

    @Nullable
    public static Component readReason(CompoundTag tag) {
        return tag.contains(REASON_KEY, Tag.TAG_STRING) ? Component.Serializer.fromJson(tag.getString(REASON_KEY)) : null;
    }

    @Nullable
    private static IdleReasonInfo.Entry readEntry(CompoundTag tag, @Nullable Component reason) {
        var key = IdleReasonInfo.keyOf(reason);
        if (key == null || !tag.contains(SEVERITY_KEY, Tag.TAG_BYTE)) return null;
        var values = IdleReasonInfo.Severity.values();
        int ordinal = tag.getByte(SEVERITY_KEY);
        var severity = ordinal >= 0 && ordinal < values.length ? values[ordinal] : IdleReasonInfo.Severity.BLOCKING;
        return new IdleReasonInfo.Entry(key, tag.contains(DESC_KEY, Tag.TAG_STRING) ? tag.getString(DESC_KEY) : null, false, severity);
    }

    public static void append(ITooltip tooltip, CompoundTag tag, boolean details) {
        if (!tag.contains(STATUS_KEY, Tag.TAG_INT)) return;
        int status = tag.getInt(STATUS_KEY);
        var reason = readReason(tag);
        var entry = readEntry(tag, reason);
        var headline = IdleReasonInfo.headline(status, reason, entry);
        tooltip.add(entry == null && reason != null && status == RecipeLogic.IDLE ? headline.copy().withStyle(ChatFormatting.GRAY) : headline);
        if (details || IdleReasonInfo.showsDetail(status, reason, entry)) {
            var detail = IdleReasonInfo.detail(status, reason, entry);
            if (detail != null) tooltip.add(detail);
        }
    }

    public static boolean reports(CompoundTag tag, String reasonKey) {
        if (!tag.contains(REASON_KEY, Tag.TAG_STRING)) return false;
        return reasonKey.equals(IdleReasonInfo.keyOf(readReason(tag)));
    }

    public static boolean reports(BlockAccessor accessor, ResourceLocation providerUid, String reasonKey) {
        var data = accessor.getServerData().getCompound(providerUid.toString()).getCompound("null");
        return !data.isEmpty() && reports(data, reasonKey);
    }
}
