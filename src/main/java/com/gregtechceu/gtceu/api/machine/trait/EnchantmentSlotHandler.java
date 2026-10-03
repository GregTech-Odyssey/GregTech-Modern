package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.transfer.key.StackInventory;

import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import com.gto.datasynclib.annotations.SaveToDisk;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMaps;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

/**
 * 机器的附魔槽：一格，只接受附魔书，向外提供时运 / 效率 / 耐久 / 精准的等级。
 *
 * <p>
 * 只读取书上的附魔等级，不消耗书、也不改动书本身。一本书可以同时带多个附魔，
 * 因此时运与效率可以来自同一本书（原版本就不允许把时运与精准放在同一件物品上）。
 *
 * <h2>缓存</h2>
 * <p>
 * 整本书的附魔只在第一次读取时解析一次，结果放进 {@link #enchantmentCache}；
 * 槽位内容一变（放进 / 取出 / 换书）就整份丢弃，下次读取重新解析。
 * 因此外部每次 tick 读等级都不会反复解析 NBT。
 */
public class EnchantmentSlotHandler extends MachineTrait {

    @Getter
    @SaveToDisk
    protected final StackInventory storage;

    /** 附魔等级缓存；{@code null} 表示需要重新解析。不持久化。 */
    @Nullable
    private Reference2IntMap<Enchantment> enchantmentCache;

    public EnchantmentSlotHandler(MetaMachine machine) {
        this(machine, 1);
    }

    public EnchantmentSlotHandler(MetaMachine machine, int slots) {
        super(machine);
        this.storage = new StackInventory(slots);
        this.storage.setFilter(stack -> stack.is(Items.ENCHANTED_BOOK));
        this.storage.setOnContentsChanged(this::onContentsChanged);
    }

    /** @return 时运等级，没有时返回 {@code 0} */
    public final int getFortuneLevel() {
        return getLevel(Enchantments.BLOCK_FORTUNE);
    }

    /** @return 效率等级，没有时返回 {@code 0} */
    public final int getEfficiencyLevel() {
        return getLevel(Enchantments.BLOCK_EFFICIENCY);
    }

    /** @return 耐久等级，没有时返回 {@code 0} */
    public final int getUnbreakingLevel() {
        return getLevel(Enchantments.UNBREAKING);
    }

    /** @return 精准采集等级，没有时返回 {@code 0} */
    public final int getSilkTouchLevel() {
        return getLevel(Enchantments.SILK_TOUCH);
    }

    /**
     * 读某个附魔的等级。
     *
     * <p>
     * 命中缓存时不会碰 NBT；未命中才去解析整本书并缓存下来。
     *
     * @return 等级；书上没有该附魔时为 {@code 0}
     */
    public final int getLevel(Enchantment enchantment) {
        return getEnchantments().getInt(enchantment);
    }

    /**
     * 取整本书的附魔等级表，必要时解析并缓存。
     *
     * <p>
     * 返回的是内部缓存，<b>不要修改</b>。
     */
    public final Reference2IntMap<Enchantment> getEnchantments() {
        if (enchantmentCache == null) {
            enchantmentCache = parseEnchantments();
        }
        return enchantmentCache;
    }

    private Reference2IntMap<Enchantment> parseEnchantments() {
        var stack = storage.getStackInSlot(0);
        if (stack.isEmpty()) return Reference2IntMaps.emptyMap();
        var levels = new Reference2IntOpenHashMap<Enchantment>();
        // 附魔书把附魔存在 StoredEnchantments 里，显式取出来，不依赖 ItemStack 对书的特殊处理
        var stored = EnchantedBookItem.getEnchantments(stack);
        if (!stored.isEmpty()) {
            levels.putAll(EnchantmentHelper.deserializeEnchantments(stored));
        } else if (stack.isEnchanted()) {
            levels.putAll(EnchantmentHelper.getEnchantments(stack));
        }
        return levels.isEmpty() ? Reference2IntMaps.emptyMap() : levels;
    }

    /** 槽位变化：丢弃缓存并通知机器。 */
    private void onContentsChanged() {
        enchantmentCache = null;
        onChanged();
    }
}
