package com.gregtechceu.gtceu.uipro.flow;

import com.gregtechceu.gtceu.uipro.IHoverOwner;
import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.render.UIDraw;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import com.gto.datasynclib.datastream.codec.ByteBufCodecs;
import com.gto.datasynclib.datastream.codec.StreamCodec;
import com.gto.datasynclib.util.ByteBufCodecExtends;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

public class FlowNode extends UIElement {

    private static final StreamCodec<FriendlyByteBuf, List<Component>> LINES = ByteBufCodecs.collection(ArrayList::new, ByteBufCodecExtends.COMPONENT_CODEC);

    final int row;
    final int column;
    final int span;
    private final SyncValue<Integer> state;
    private Supplier<FlowState> stateGetter = () -> FlowState.IDLE;
    @Nullable
    private SyncValue<List<Component>> detail;

    FlowNode(int row, int column, int span, int width) {
        this.row = row;
        this.column = column;
        this.span = span;
        layout(l -> l.column().width(width).gapAll(UISizes.GAP)
                .paddingTop(UISizes.FLOW_NODE_PADDING_TOP).paddingBottom(UISizes.FLOW_NODE_PADDING)
                .paddingLeft(UISizes.FLOW_NODE_PADDING).paddingRight(UISizes.FLOW_NODE_PADDING));
        this.state = addSyncValue(SyncValue.ofInt(() -> stateGetter.get().ordinal(), FlowState.IDLE.ordinal()));
    }

    public FlowNode bindState(Supplier<FlowState> state) {
        this.stateGetter = state;
        return this;
    }

    public FlowNode bindDetail(Supplier<List<Component>> lines) {
        this.detail = addSyncValue(SyncValue.of(lines, LINES, Collections.emptyList()));
        return this;
    }

    public FlowState getFlowState() {
        return FlowState.of(state.getValue());
    }

    public int getRow() {
        return row;
    }

    public int getColumn() {
        return column;
    }

    public int getSpan() {
        return span;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        var current = getFlowState();
        UIDraw.flowPlate(graphics, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight(),
                current.getOutline(), current.getStripLight(), current.getStripMid());
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean hasOwnTooltip(int mouseX, int mouseY) {
        return super.hasOwnTooltip(mouseX, mouseY) || detail != null && !detail.getValue().isEmpty();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (detail != null && !detail.getValue().isEmpty() && gui != null && gui.getModularUIGui() != null && isMouseOverElement(mouseX, mouseY) && !IHoverOwner.anyOwns(this, mouseX, mouseY)) {
            gui.getModularUIGui().setHoverTooltip(detail.getValue(), ItemStack.EMPTY, null, null);
        }
        super.drawInForeground(graphics, mouseX, mouseY, partialTicks);
    }
}
