package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfigurator;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.uipro.LayoutStyle;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.elements.Form;
import com.gregtechceu.gtceu.uipro.elements.PhantomItemSlot;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import com.gto.datasynclib.annotations.SyncToClient;
import lombok.Getter;

import java.util.List;

/** A non-consuming block sample, synchronized to world rendering independently of the UI. */
public final class StorageAppearance extends MachineTrait implements IFancyConfigurator {

    private static final String KEY = "storageAppearance";
    private static final Component TITLE = Component.translatable("gtceu.gui.storage_appearance");
    private static final Component SAMPLE = Component.translatable("gtceu.gui.storage_appearance.sample");
    private static final Component HELP = Component.translatable("gtceu.gui.storage_appearance.help");
    private static final IGuiTexture ICON = new ItemStackTexture(Blocks.BRICKS.asItem());

    @Getter
    @SyncToClient(listener = "onBlockSynced")
    private Block block = Blocks.AIR;

    private Block sampleBlock = Blocks.AIR;
    private ItemStack sample = ItemStack.EMPTY;

    public StorageAppearance(MetaMachine machine) {
        super(machine);
    }

    public ItemStack getSample() {
        if (sampleBlock != block) {
            sampleBlock = block;
            sample = block == Blocks.AIR ? ItemStack.EMPTY : new ItemStack(block);
        }
        return sample;
    }

    public void setSample(ItemStack stack) {
        if (!stack.isEmpty() && !isValidSample(stack)) return;
        Block selected = stack.isEmpty() ? Blocks.AIR : ((BlockItem) stack.getItem()).getBlock();
        if (block == selected) return;
        block = selected;
        if (!isRemote()) {
            onChanged();
            machine.requestSync();
        }
    }

    public static boolean isValidSample(ItemStack stack) {
        return stack.getItem() instanceof BlockItem && !(stack.getItem() instanceof MetaMachineItem);
    }

    private void onBlockSynced(Block value, Block previous) {
        machine.scheduleRenderUpdate();
    }

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        if (block != Blocks.AIR) tag.putString(KEY, BuiltInRegistries.BLOCK.getKey(block).toString());
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        var id = ResourceLocation.tryParse(tag.getString(KEY));
        block = id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(id);
    }

    @Override
    public Component getTitle() {
        return TITLE;
    }

    @Override
    public IGuiTexture getIcon() {
        return ICON;
    }

    @Override
    public List<Component> getTooltips() {
        return List.of(getTitle(), HELP);
    }

    @Override
    public Widget createConfigurator() {
        var slot = PhantomItemSlot.of(this::getSample, this::setSample, StorageAppearance::isValidSample).xeiPhantom();
        slot.tooltips(HELP);
        return UIElement.column(LayoutStyle.AUTO).layout(l -> l.minWidth(6 * UISizes.SLOT_SIZE).gapAll(UISizes.SECTION_GAP))
                .addChild(Form.controlRow(SAMPLE, slot, HELP));
    }
}
