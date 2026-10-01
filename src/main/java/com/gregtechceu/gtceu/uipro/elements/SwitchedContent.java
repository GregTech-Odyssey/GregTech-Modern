package com.gregtechceu.gtceu.uipro.elements;

import com.gregtechceu.gtceu.uipro.UIElement;
import com.gregtechceu.gtceu.uipro.data.UIStructure;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import com.gto.datasynclib.datastream.codec.ByteStreamCodec;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntSupplier;

public class SwitchedContent extends UIElement {

    private static final int UNBUILT = Integer.MIN_VALUE;

    @FunctionalInterface
    public interface Factory {

        @Nullable
        Widget create(int key, boolean remote);
    }

    private final IntSupplier serverKey;
    private final Factory factory;
    private final UIStructure<Integer> content;
    private int builtKey = UNBUILT;

    public SwitchedContent(IntSupplier serverKey, Factory factory) {
        this.serverKey = serverKey;
        this.factory = factory;
        layout(l -> l.column());
        content = addStructure(ByteStreamCodec.INT_CODEC, () -> builtKey)
                .serverOnly()
                .apply(this::build);
    }

    public int getKey() {
        return builtKey;
    }

    private void build(int key) {
        builtKey = key;
        clearAllWidgets();
        var widget = factory.create(key, isRemote());
        if (widget != null) addWidget(widget);
    }

    @Override
    public void initWidget() {
        if (!isRemote() && builtKey == UNBUILT) build(serverKey.getAsInt());
        super.initWidget();
    }

    @Override
    public void detectAndSendChanges() {
        if (!isRemote() && builtKey != UNBUILT && serverKey.getAsInt() != builtKey) content.request(serverKey.getAsInt());
        super.detectAndSendChanges();
    }
}
