package com.gregtechceu.gtceu.integration.jade.provider;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.GTBlockEntity;
import com.gregtechceu.gtceu.api.misc.TickTimeMonitor;
import com.gregtechceu.gtceu.common.data.GTTickTimeMonitors;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * tick 耗时监控的 Jade 显示：把方块实体上注册的命名监控逐个显示成一行。
 *
 * <p>
 * 取数据这一下同时就是「有人在看」的信号（监控会续期 2 个采样窗口），所以没被查看的机器不会计时。
 * 显示名走语言键 {@code gtceu.top.tick_time.<key>}，扩展模组注册自己的 key 时自己补语言文件即可。
 */
public class TickTimeProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    private static final String TAG = "tick_times";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor blockAccessor) {
        if (!(blockAccessor.getBlockEntity() instanceof GTBlockEntity blockEntity)) return;
        var monitors = blockEntity.getTickTimeMonitors();
        if (monitors.isEmpty()) return;
        var tag = new CompoundTag();
        for (var entry : GTTickTimeMonitors.REGISTRY) {
            var monitor = monitors.getData(entry);
            if (monitor == null) continue;
            var micros = ((TickTimeMonitor) monitor).getAverageTickTimeMicros();
            if (micros > 0.0D) {
                tag.putDouble(String.valueOf(GTTickTimeMonitors.REGISTRY.getId(entry)), micros);
            }
        }
        if (!tag.isEmpty()) {
            data.put(TAG, tag);
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor blockAccessor, IPluginConfig config) {
        var tag = blockAccessor.getServerData().getCompound(TAG);
        if (tag.isEmpty()) return;
        var keys = tag.getAllKeys();

        // 只有一条：直接显示，不用折叠
        if (keys.size() == 1) {
            String key = keys.iterator().next();
            addLine(tooltip, name(Integer.parseInt(key)), tag.getDouble(key));
            return;
        }

        // 多条：平时只给总耗时（各条平均值的和，客户端直接算），明细按 Shift 展开
        boolean showDetails = blockAccessor.getPlayer().isShiftKeyDown();
        if (showDetails) {
            for (String key : keys) {
                addLine(tooltip, name(Integer.parseInt(key)), tag.getDouble(key));
            }
        }
        double total = 0.0D;
        for (String key : keys) {
            total += tag.getDouble(key);
        }
        addLine(tooltip, Component.translatable("gtceu.top.tick_time.total"), total);
        if (!showDetails) {
            tooltip.add(Component.translatable("gtceu.tooltip.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static void addLine(ITooltip tooltip, Component label, double micros) {
        MutableComponent value = Component.literal(FormattingUtil.formatNumber2Places(micros))
                .withStyle(ChatFormatting.GOLD);
        tooltip.add(Component.translatable("gtceu.top.tick_time", label, value).withStyle(ChatFormatting.GRAY));
    }

    private static Component name(int keyId) {
        return Component.translatable("gtceu.top.tick_time." + GTTickTimeMonitors.REGISTRY.get(keyId).name);
    }

    @Override
    public ResourceLocation getUid() {
        return GTCEu.id("tick_time_info");
    }
}
