package com.gregtechceu.gtceu.api.gui.widget;

import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class CustomComponentPanelWidget extends ComponentPanelWidget {

    private Consumer<FriendlyByteBuf> dataWriter;
    private BiConsumer<FriendlyByteBuf, List<Component>> dataReader;

    public CustomComponentPanelWidget(int x, int y) {
        super(x, y, GTUtil.NOOP_CONSUMER);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        if (this.textSupplier != null) {
            List<Component> textBuffer = new ArrayList<>();
            this.textSupplier.accept(textBuffer);
            if (!this.lastText.equals(textBuffer)) {
                this.lastText = textBuffer;
                this.writeUpdateInfo(1, (buffer) -> {
                    buffer.writeVarInt(this.lastText.size());
                    for (Component textComponent : this.lastText) {
                        buffer.writeComponent(textComponent);
                    }
                    if (dataWriter != null) {
                        dataWriter.accept(buffer);
                    }
                });
            }
        }
    }

    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id == 1) {
            this.lastText.clear();
            int count = buffer.readVarInt();

            for (int i = 0; i < count; ++i) {
                this.lastText.add(buffer.readComponent());
            }
            if (dataReader != null) {
                dataReader.accept(buffer, lastText);
            }
            this.formatDisplayText();
            this.updateComponentTextSize();
        }
    }

    public CustomComponentPanelWidget setTextDataWriter(Consumer<FriendlyByteBuf> dataWriter) {
        this.dataWriter = dataWriter;
        return this;
    }

    public CustomComponentPanelWidget setTextDataReader(BiConsumer<FriendlyByteBuf, List<Component>> dataReader) {
        this.dataReader = dataReader;
        return this;
    }
}
