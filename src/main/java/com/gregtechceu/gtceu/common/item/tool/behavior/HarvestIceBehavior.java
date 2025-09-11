package com.gregtechceu.gtceu.common.item.tool.behavior;

import com.gregtechceu.gtceu.api.item.tool.behavior.IToolBehavior;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * ice harvesting is handled in an event elsewhere
 * 
 * @see com.gregtechceu.gtceu.common.item.tool.ToolEventHandlers#onHarvestDrops
 */
public class HarvestIceBehavior implements IToolBehavior {

    public static final HarvestIceBehavior INSTANCE = new HarvestIceBehavior();

    protected HarvestIceBehavior() {/**/}

    @Override
    public void addInformation(@NotNull ItemStack stack, @Nullable Level world, @NotNull List<Component> tooltip,
                               @NotNull TooltipFlag flag) {
        tooltip.add(Component.translatable("item.gtceu.tool.behavior.silk_ice"));
    }
}
