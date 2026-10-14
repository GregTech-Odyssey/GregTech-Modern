package com.gregtechceu.gtceu.common.item.tool.behavior;

import com.gregtechceu.gtceu.api.item.tool.behavior.IToolBehavior;
import com.gregtechceu.gtceu.api.tooltip.Tips;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class EntityDamageBehavior implements IToolBehavior {

    public static final EntityDamageBehavior WRENCH = new EntityDamageBehavior(3.0F, IronGolem.class, EntityType.IRON_GOLEM);
    public static final EntityDamageBehavior HAMMER = new EntityDamageBehavior(2.0F, IronGolem.class, EntityType.IRON_GOLEM);
    public static final EntityDamageBehavior SCREWDRIVER = new EntityDamageBehavior(3.0F, Spider.class, EntityType.SPIDER);

    private final float bonus;
    private final Class<? extends LivingEntity> target;
    private final EntityType<?> displayType;

    private EntityDamageBehavior(float bonus, Class<? extends LivingEntity> target, EntityType<?> displayType) {
        this.bonus = bonus;
        this.target = target;
        this.displayType = displayType;
    }

    @Override
    public float getAttackBonus(@NotNull ItemStack stack, @NotNull LivingEntity target) {
        return this.target.isInstance(target) ? bonus : 0;
    }

    @Override
    public void addInformation(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip,
                               @NotNull TooltipFlag flag) {
        tooltip.add(Tips.bonus(Component.translatable("item.gtceu.tool.behavior.entity_damage",
                Component.translatable(displayType.getDescriptionId()), FormattingUtil.formatNumbers(bonus))));
    }
}
