package com.gregtechceu.gtceu.common.item.tool;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.item.IDataComponentItem;
import com.gregtechceu.gtceu.api.item.ItemDataComponentKey;
import com.gregtechceu.gtceu.api.tooltip.Tips;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.item.TurbineRotorBehaviour;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.gregtechceu.gtceu.common.item.GTTurbineItem.getRotorMaxDamage;

/**
 * 镀层转子的耐久处理；磨损由转子仓的工作回调驱动，本类不自行计时。
 * <p>
 * 正常运行时的调用链与时机：
 * <ol>
 * <li>服务端 RecipeLogic.handleRecipeWorking() 在配方本 tick 的 handleTickRecipe() 成功后，
 * 推进进度并调用 WorkableMultiblockMachine.onWorking()，再分发到 RotorHolderPartMachine.onWorking()。
 * 配方未运行、暂停或本 tick 处理失败时，不通过这条链扣耐久；停机后的惯性减速也不扣耐久。</li>
 * <li>转子仓先加速，再在 {@code getOffsetTimer() % 20 == 0} 时调用
 * {@code damageRotor(1 + numMaintenanceProblems)}。这是仓自身计时器的 20 tick 边界，
 * 并非从配方开始累计 20 个工作 tick；持续运行时每 20 tick 一次（20 TPS 下约一秒）。
 * 维护问题数来自已成型且实现 IMaintenanceMachine 的控制器，否则为 0。
 * 磨损量不按转速、功率、并行数或配方完成次数计算，未达到最大转速时也会磨损。</li>
 * <li>IRotorHolderMachine.damageRotor() 取得槽内转子的行为，存在时调用本类
 * {@link #applyRotorDamage(ItemStack, int)}，并将返回的物品栈写回转子仓、通知监听器。
 * 因此破损后的空栈、脱镀后的普通转子都会在这一次磨损中写回。</li>
 * </ol>
 * <p>
 * 耐久数据分为两部分：本体已损伤值是 ItemStack 的 Damage，本体上限是物品的 MaxDamage；
 * 镀层已损伤值和上限分别存于 {@code GT.PartStats.ProtectionDamage} 与
 * {@code GT.PartStats.ProtectionMaxDamage}，缺失时均按 0 读取。镀层不提高本体的 MaxDamage。
 * 实际处理顺序为：父类先判定本体破损 → 未破损才调用 {@link #setPartDamage(ItemStack, int)}
 * 分配本次磨损 → 本类检查镀层是否耗尽并脱镀。边界行为见这两个方法的注释。
 */
@SuppressWarnings("NoTranslation")
public class CoatedTurbineRotorBehaviour extends TurbineRotorBehaviour {

    public static final ItemDataComponentKey<Material, CompoundTag> COATING = ItemDataComponentKey.createCompound("GT.PartStats",
            (tag, material) -> tag.putString("Coating", material.getResourceLocation().toString()),
            tag -> tag.contains("Coating", Tag.TAG_STRING) ? GTMaterials.get(tag.getString("Coating")) : null,
            tag -> tag.remove("Coating"));
    public static final ItemDataComponentKey<Integer, CompoundTag> PROTECTION_DAMAGE = ItemDataComponentKey.createCompound("GT.PartStats",
            (tag, damage) -> tag.putInt("ProtectionDamage", damage),
            tag -> tag.contains("ProtectionDamage", Tag.TAG_ANY_NUMERIC) ? tag.getInt("ProtectionDamage") : null,
            tag -> tag.remove("ProtectionDamage"));
    public static final ItemDataComponentKey<Integer, CompoundTag> PROTECTION_MAX_DAMAGE = ItemDataComponentKey.createCompound("GT.PartStats",
            (tag, damage) -> tag.putInt("ProtectionMaxDamage", damage),
            tag -> tag.contains("ProtectionMaxDamage", Tag.TAG_ANY_NUMERIC) ? tag.getInt("ProtectionMaxDamage") : null,
            tag -> tag.remove("ProtectionMaxDamage"));

    private final RandomSource rd = RandomSource.create();

    @Override
    public void appendTooltips(ItemStack stack, @Nullable Level level,
                               List<Component> tooltipComponents, TooltipFlag isAdvanced) {
        super.appendTooltips(stack, level, tooltipComponents, isAdvanced);
        tooltipComponents
                .add(Tips.stat(Component.translatable("metaitem.tool.tooltip.rotor.coating", getCoatMaterial(stack).getLocalizedName())));
        int i = 0;
        boolean isMagic = isCoatingMagical(stack);
        // 机制说明标题
        tooltipComponents
                .add(Tips.heading(Component.translatable("metaitem.tool.tooltip.rotor.coating.tooltip." + i++ + (isMagic ? ".magic" : ""))));
        // 镀层耐久说明；实际耐久上限的初始化公式见 setCoatMaterial。
        tooltipComponents
                .add(Tips.feature(Component.translatable("metaitem.tool.tooltip.rotor.coating.tooltip." + i++ + (isMagic ? ".magic" : ""))));
        // 磨损分配说明；普通镀层的 95% 保护和魔法镀层的同步损伤见 setPartDamage。
        tooltipComponents
                .add(Tips.feature(Component.translatable("metaitem.tool.tooltip.rotor.coating.tooltip." + i++ + (isMagic ? ".magic" : ""))));
        if (isMagic) {
            tooltipComponents.add(Tips.formula(Component.translatable("metaitem.tool.tooltip.rotor.coating.tooltip.3.magic")));
            tooltipComponents.add(Tips.feature(Component.translatable("metaitem.tool.tooltip.rotor.coating.tooltip.4.magic")));
            tooltipComponents.add(Tips.feature(Component.translatable("metaitem.tool.tooltip.rotor.coating.tooltip.5.magic")));
            tooltipComponents.add(Tips.feature(Component.translatable("metaitem.tool.tooltip.rotor.coating.tooltip.6.magic")));
            tooltipComponents.add(Tips.feature(Component.translatable("metaitem.tool.tooltip.rotor.coating.tooltip.7.magic")));
            tooltipComponents.add(Tips.feature(Component.translatable("metaitem.tool.tooltip.rotor.coating.tooltip.8.magic")));
        }
    }

    public static boolean isCoatingMagical(ItemStack itemStack) {
        return getCoatMaterial(itemStack).hasFlag(MaterialFlags.MAGICAL);
    }

    public static Material getCoatMaterial(ItemStack itemStack) {
        var defaultMaterial = GTMaterials.Neutronium;
        var material = IDataComponentItem.getOrDefault(itemStack, COATING, defaultMaterial);
        if (material.isNull() || !material.hasProperty(PropertyKey.INGOT)) {
            return defaultMaterial;
        }
        return material;
    }

    @Override
    public int getRotorEfficiency(ItemStack stack) {
        return (int) ((super.getRotorEfficiency(stack) + getRotorBonusEfficiency(stack)) * (1.0d + getEnchBonusEfficiency(stack)));
    }

    @Override
    public int getRotorPower(ItemStack stack) {
        return (int) ((super.getRotorPower(stack) + getRotorBonusPower(stack)) * (1.0d + getEnchBonusPower(stack)));
    }

    public int getRotorBonusPower(ItemStack stack) {
        if (isCoatingMagical(stack)) {
            return (int) ((getCoatMaterial(stack).getProperty(PropertyKey.ROTOR).getPower() - 100) * 0.3f);
        }
        return 0;
    }

    public double getEnchBonusPower(ItemStack stack) {
        if (isCoatingMagical(stack)) {
            return Math.min(stack.getEnchantmentLevel(Enchantments.POWER_ARROWS), 5) * 0.05f;
        }
        return 0;
    }

    public int getRotorBonusEfficiency(ItemStack stack) {
        if (isCoatingMagical(stack)) {
            return (int) ((getCoatMaterial(stack).getProperty(PropertyKey.ROTOR).getEfficiency() - 100) * 0.3f);
        }
        return 0;
    }

    public double getEnchBonusEfficiency(ItemStack stack) {
        if (isCoatingMagical(stack)) {
            return Math.min(stack.getEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY), 5) * 0.1f;
        }
        return 0;
    }

    /**
     * 设置镀层材质及耐久上限。设 A 为本体最大耐久，B 为镀层材料制成普通转子的最大耐久，
     * 则镀层上限为 {@code 4 * max(min(B, A + B / 2) - A, B / 10)}，除法按整数截断。
     * A、B 均为最大耐久而非剩余耐久；本方法不重置已有的 ProtectionDamage。
     */
    public static void setCoatMaterial(ItemStack itemStack, Material coating) {
        var behaviour = getBehaviour(itemStack);
        if (!(behaviour instanceof CoatedTurbineRotorBehaviour)) {
            return;
        }
        IDataComponentItem.set(itemStack, COATING, coating);

        var bonusDurability = getRotorMaxDamage(coating);
        var originalDurability = behaviour.getPartMaxDurability(itemStack);
        var result = Math.min(bonusDurability, originalDurability + bonusDurability / 2) - originalDurability;
        IDataComponentItem.set(itemStack, PROTECTION_MAX_DAMAGE, 4 * Math.max(result, bonusDurability / 10));
    }

    public int getCoatDamage(ItemStack itemStack) {
        return IDataComponentItem.getInt(itemStack, PROTECTION_DAMAGE);
    }

    public int getCoatMaxDamage(ItemStack itemStack) {
        return IDataComponentItem.getInt(itemStack, PROTECTION_MAX_DAMAGE);
    }

    public void setCoatDamage(ItemStack itemStack, int resultDamage) {
        IDataComponentItem.set(itemStack, PROTECTION_DAMAGE, resultDamage);
    }

    /**
     * 分配已经通过父类破损判定的磨损。resultDamage 是本体的目标累计损伤值，
     * 本次增量为 {@code resultDamage - getPartDamage(itemStack)}，不是 resultDamage 本身。
     * <p>
     * 普通镀层：仅在增量为正、镀层尚未耗尽且本次 0～99 的随机数小于 95 时吸收磨损。
     * 命中后只增加镀层损伤（封顶到镀层上限）并立即返回，本体不受损；即使本次增量超过
     * 镀层剩余耐久，超出的部分也不会转移到本体。未命中或镀层已耗尽时，本次增量全部计入本体。
     * <p>
     * 魔法镀层：不享受上述保护，镀层和本体同时增加相同的损伤增量，镀层损伤不封顶。
     * 最后由父类 setPartDamage() 写入本体 Damage。直接调用本方法不会判定本体破损或自动脱镀，
     * 脱镀检查仅在 applyRotorDamage() 中执行；非正增量不触发普通镀层保护，魔法镀层仍同步该增量。
     */
    @Override
    public void setPartDamage(ItemStack itemStack, int resultDamage) {
        var isMagic = isCoatingMagical(itemStack);
        if (resultDamage > getPartDamage(itemStack) &&
                getCoatDamage(itemStack) < getCoatMaxDamage(itemStack) &&
                rd.nextInt(100) < 95 && isMagic) {
            setCoatDamage(itemStack, Math.min(getCoatDamage(itemStack) + resultDamage - getPartDamage(itemStack), getCoatMaxDamage(itemStack)));
            return;
        } else if (isMagic) {
            var unbreakingEnchantment = Math.min(itemStack.getEnchantmentLevel(Enchantments.UNBREAKING), 3);
            resultDamage = (int) Math.ceil((resultDamage - getPartDamage(itemStack)) * (1.0f / (unbreakingEnchantment + 1))) + getPartDamage(itemStack);
            // 魔法镀层与本体耐久同时损失
            setCoatDamage(itemStack, getCoatDamage(itemStack) + resultDamage - getPartDamage(itemStack));
        }
        super.setPartDamage(itemStack, resultDamage);
    }

    /**
     * 一次磨损的完整入口，先处理本体/镀层损伤，再检查是否脱镀。
     * <p>
     * 父类先计算 {@code 本体当前损伤 + damageApplied}，若达到或超过本体最大耐久，
     * 直接 shrink(1) 销毁转子，不调用 setPartDamage()。因此即使普通镀层还有耐久，
     * 这次也没有 95% 保护机会；只有未达到本体破损阈值时才动态调用本类 setPartDamage()。
     * <p>
     * 父类返回后，仅在物品栈非空且镀层损伤达到或超过上限时脱镀，将转子换成同本体材料的
     * 普通转子并保留本体 Damage。检查发生在本次磨损分配之后，不会为脱镀额外扣一次本体耐久；
     * 普通镀层吸收最后一次磨损、魔法镀层同步耗尽，或镀层上限缺失为 0 时都可能触发此检查。
     */
    @Override
    public ItemStack applyRotorDamage(ItemStack itemStack, int damageApplied) {
        var stack = super.applyRotorDamage(itemStack, damageApplied);
        if (!stack.isEmpty() && getCoatDamage(itemStack) >= getCoatMaxDamage(itemStack)) {
            return removePlating(stack);
        }
        return stack;
    }

    /**
     * 创建同本体材料的普通转子，仅复制本体损伤值；镀层数据及原物品栈的其他 NBT 不会复制。
     * 后续磨损由普通 TurbineRotorBehaviour 处理，魔法镀层提供的功率/效率加成也随之消失。
     */
    private ItemStack removePlating(ItemStack itemStack) {
        var damage = getPartDamage(itemStack);
        var newItem = ChemicalHelper.get(TagPrefix.turbineRotor, getPartMaterial(itemStack));
        newItem.setDamageValue(damage);
        return newItem;
    }
}
