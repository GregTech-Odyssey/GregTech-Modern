package com.gregtechceu.gtceu.common.item.armor;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.item.armor.ArmorComponentItem;
import com.gregtechceu.gtceu.api.item.armor.IArmorLogic;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

public class HazmatSuit implements IArmorLogic {

    /**
     * 与 HazardProperty 的 HANDS 防护使用同一个饰品槽名
     */
    private static final String HANDS_SLOT = "hands";

    private final ArmorItem.Type type;
    private final String armorTexture;
    private final boolean insulatedHands;

    public HazmatSuit(ArmorItem.Type type, String armorTexture) {
        this(type, armorTexture, false);
    }

    /**
     * insulatedHands 的手部装备（橡胶手套）护住手，手持高温物品时不被烫伤。
     */
    public HazmatSuit(ArmorItem.Type type, String armorTexture, boolean insulatedHands) {
        this.type = type;
        this.armorTexture = armorTexture;
        this.insulatedHands = insulatedHands;
    }

    @Override
    public ArmorItem.Type getArmorType() {
        return this.type;
    }

    @Override
    public int getArmorDisplay(Player player, @NotNull ItemStack armor, EquipmentSlot slot) {
        return 0;
    }

    @Override
    public @Nullable ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return slot != EquipmentSlot.LEGS ?
                GTCEu.id(String.format("textures/armor/%s_1.png", armorTexture)) :
                GTCEu.id(String.format("textures/armor/%s_2.png", armorTexture));
    }

    @Override
    public boolean isPPE() {
        return true;
    }

    /**
     * 手部是否已戴上隔热的橡胶手套。检测的槽位与 HazardProperty 的 HANDS 防护相同
     * （胸甲槽 + 饰品 hands 槽），但这里只认手套本身，其他 PPE 装备不算。
     */
    public static boolean hasInsulatedHands(LivingEntity entity) {
        if (isInsulatedHands(entity.getItemBySlot(EquipmentSlot.CHEST))) {
            return true;
        }
        if (!GTCEu.Mods.isCuriosLoaded()) {
            return false;
        }
        ICuriosItemHandler curios = CuriosApi.getCuriosInventory(entity).resolve().orElse(null);
        if (curios == null) {
            return false;
        }
        ICurioStacksHandler hands = curios.getStacksHandler(HANDS_SLOT).orElse(null);
        if (hands == null) {
            return false;
        }
        IDynamicStackHandler stacks = hands.getStacks();
        for (int i = 0; i < stacks.getSlots(); i++) {
            if (isInsulatedHands(stacks.getStackInSlot(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInsulatedHands(ItemStack stack) {
        return stack.getItem() instanceof ArmorComponentItem armor &&
                armor.getArmorLogic() instanceof HazmatSuit suit && suit.insulatedHands;
    }
}
