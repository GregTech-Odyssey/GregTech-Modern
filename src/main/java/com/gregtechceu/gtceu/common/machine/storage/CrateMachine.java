package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.ButtonConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.IDropSaveMachine;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.IMachineLife;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableInventory;
import com.gregtechceu.gtceu.api.recipe.handler.IO;
import com.gregtechceu.gtceu.api.transfer.forge.MenuItemAdapter;
import com.gregtechceu.gtceu.api.transfer.key.KeyInventory;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.datasynclib.GTDataFixer;
import com.gregtechceu.gtceu.uipro.elements.ItemSlot;
import com.gregtechceu.gtceu.uipro.elements.ScrollerView;
import com.gregtechceu.gtceu.uipro.elements.SlotGrid;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;
import com.gregtechceu.gtceu.uiwidgets.icon.WidgetIcons;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import appeng.api.stacks.AEItemKey;
import com.gto.datasynclib.annotations.SaveToDisk;
import com.gto.datasynclib.annotations.SyncToClient;
import com.gto.datasynclib.datastream.data.Data;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CrateMachine extends MetaMachine implements IFancyUIMachine, IMachineLife, IDropSaveMachine, IInteractedMachine {

    private static final int WIDE_INVENTORY = 90;

    @Getter
    private final Material material;
    @Getter
    private final int inventorySize;
    @Getter
    @SaveToDisk(defaultValue = "false")
    @SyncToClient(scheduleUpdate = true)
    private boolean isTaped;
    @SaveToDisk
    public final NotifiableInventory<AEItemKey> inventory;

    public CrateMachine(MetaMachineBlockEntity holder, Material material, int inventorySize) {
        super(holder);
        this.material = material;
        this.inventorySize = inventorySize;
        this.inventory = NotifiableInventory.items(this, inventorySize, IO.BOTH);
    }

    @Override
    public int getInventoryGutter() {
        return ScrollerView.SCROLL_BAR_SPACE;
    }

    @Override
    public Widget createUIWidget() {
        int columns = inventorySize >= WIDE_INVENTORY ? 2 * UISizes.SLOTS_PER_ROW : UISizes.SLOTS_PER_ROW;
        var adapter = new MenuItemAdapter(inventory.storage);
        return ScrollerView.page("crate.slots", columns * UISizes.SLOT_SIZE).adaptiveWidth()
                .addScrollViewChild(SlotGrid.of(columns, inventorySize, i -> ItemSlot.of(adapter, i)));
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        IFancyUIMachine.super.attachConfigurators(configuratorPanel);
        configuratorPanel.attachConfigurators(new ButtonConfigurator(WidgetIcons.SORT, clickData -> {
            if (!clickData.isRemote) sortInventory(inventory.storage);
        }).setTooltips(List.of(Component.translatable("gtceu.gui.inventory.sort"))));
    }

    private static void sortInventory(KeyInventory<AEItemKey> storage) {
        var totals = new Object2LongLinkedOpenHashMap<AEItemKey>();
        int size = storage.size();
        for (int i = 0; i < size; i++) {
            var key = storage.keyAt(i);
            if (key != null) totals.addTo(key, storage.amountAt(i));
        }
        var keys = new ArrayList<>(totals.keySet());
        keys.sort(Comparator.comparing((AEItemKey key) -> BuiltInRegistries.ITEM.getKey(key.getItem()))
                .thenComparing(key -> Objects.toString(key.getTag(), "")));
        int slot = 0;
        for (var key : keys) {
            long left = totals.getLong(key);
            long limit = Math.max(1, storage.limitFor(key));
            while (left > 0 && slot < size) {
                long n = Math.min(left, limit);
                storage.set(slot++, key, n);
                left -= n;
            }
        }
        while (slot < size) {
            storage.set(slot++, null, 0);
        }
    }

    @Override
    public InteractionResult onUse(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isCrouching() && !isTaped) {
            if (stack.is(GTItems.DUCT_TAPE.asItem()) || stack.is(GTItems.BASIC_TAPE.asItem())) {
                if (!player.isCreative()) {
                    stack.shrink(1);
                }
                isTaped = true;
                return InteractionResult.SUCCESS;
            }
        }
        return IInteractedMachine.super.onUse(state, world, pos, player, hand, hit);
    }

    @Override
    public void onMachinePlaced(@Nullable LivingEntity player, ItemStack stack) {
        IMachineLife.super.onMachinePlaced(player, stack);
        CompoundTag tag = stack.getTag();
        if (tag != null) {
            this.isTaped = tag.getBoolean("taped");
            if (isTaped) {
                readInventory(tag.get("inventory"));
            }
            tag.remove("taped");
            this.isTaped = false;
        }
        stack.setTag(null);
    }

    @Override
    public void saveToItem(CompoundTag tag) {
        if (isTaped) {
            tag.putBoolean("taped", isTaped);
            tag.put("inventory", new ByteArrayTag(inventory.storage.writeData().writeToBytes()));
        }
    }

    @Override
    public void loadFromItem(CompoundTag tag) {
        if (tag.getBoolean("taped")) isTaped = true;
        readInventory(tag.get("inventory"));
    }

    private void readInventory(@Nullable Tag tag) {
        var storage = inventory.storage;
        if (tag instanceof ByteArrayTag bytes) {
            storage.readData(Data.readData(bytes.getAsByteArray()), GTDataFixer.VERSION);
            storage.notifyChanged();
        } else if (tag instanceof CompoundTag nbt) {
            ListTag list = nbt.getList("Items", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag itemTag = list.getCompound(i);
                int slot = itemTag.getInt("Slot");
                if (slot >= 0 && slot < storage.size()) {
                    var item = ItemStack.of(itemTag);
                    storage.set(slot, Keys.item(item), item.getCount());
                }
            }
        }
    }

    @Override
    public boolean saveBreak() {
        return isTaped;
    }

    @Override
    public void onMachineRemoved() {
        if (!isTaped) clearInventory(inventory.storage);
    }
}
