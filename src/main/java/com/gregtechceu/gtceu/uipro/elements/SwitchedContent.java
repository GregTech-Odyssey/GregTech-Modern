package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.SyncValueHost;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

public class SwitchedContent extends UIElement {

    private static final int SWITCH_ID = SyncValueHost.ID_BASE - 11;
    private static final int UNBUILT = Integer.MIN_VALUE;

    @FunctionalInterface
    public interface Factory {

        @Nullable
        Widget create(int key, boolean remote);
    }

    private final IntSupplier serverKey;
    private final Factory factory;
    private int builtKey = UNBUILT;

    public SwitchedContent(IntSupplier serverKey, Factory factory) {
        this.serverKey = serverKey;
        this.factory = factory;
        layout(l -> l.column());
    }

    public int getKey() {
        return builtKey;
    }

    private void build(int key) {
        builtKey = key;
        clearAllWidgets();
        var content = factory.create(key, isRemote());
        if (content != null) addWidget(content);
    }

    @Override
    public void initWidget() {
        if (!isRemote() && builtKey == UNBUILT) build(serverKey.getAsInt());
        super.initWidget();
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        buffer.writeVarInt(builtKey);
        super.writeInitialData(buffer);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        build(buffer.readVarInt());
        super.readInitialData(buffer);
    }

    @Override
    public void detectAndSendChanges() {
        int key = serverKey.getAsInt();
        if (key != builtKey) {
            writeUpdateInfo(SWITCH_ID, buf -> buf.writeVarInt(key));
            build(key);
        }
        super.detectAndSendChanges();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == SWITCH_ID) build(buffer.readVarInt());
        else super.readUpdateInfo(id, buffer);
    }
}
