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
    /// 服务端每刻取文字用的暂存表：内容没变时下一刻清空复用，变了才交给 lastText 并换一张新表
    private List<Component> textBuffer = new ArrayList<>();

    public CustomComponentPanelWidget(int x, int y) {
        super(x, y, GTUtil.NOOP_CONSUMER);
    }

    /**
     * 文字变了才下发（连同附加数据）。
     * <p>
     * 不调 super：LDLib {@link ComponentPanelWidget#detectAndSendChanges()} 会用同一个 {@code textSupplier} 再取一遍、
     * 再深比较一遍，变化时还会先发一条不带附加数据（{@code dataWriter}）的更新，客户端的 {@code dataReader} 就读到包尾之外。
     * 它的父类 {@code Widget} 的同名方法是空的，所以这里完整接管。
     */
    @Override
    public void detectAndSendChanges() {
        if (this.textSupplier == null) return;
        textBuffer.clear();
        this.textSupplier.accept(textBuffer);
        if (this.lastText.equals(textBuffer)) return;
        this.lastText = textBuffer;
        textBuffer = new ArrayList<>();
        this.writeUpdateInfo(1, this::writeText);
    }

    private void writeText(FriendlyByteBuf buffer) {
        buffer.writeVarInt(this.lastText.size());
        for (Component textComponent : this.lastText) {
            buffer.writeComponent(textComponent);
        }
        if (dataWriter != null) {
            dataWriter.accept(buffer);
        }
    }

    /** 与读取对称：LDLib 读初始数据走 {@code readUpdateInfo(1, ...)}（本类会读附加数据），所以初始数据也要写上附加数据。 */
    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        if (dataWriter != null) {
            dataWriter.accept(buffer);
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

    /**
     * 文字之外的附加数据：服务端 {@code writer} 写、客户端 {@code reader} 读，必须成对设置——初始数据与兄弟控件共用一个包，
     * 只写不读或只读不写都会让后面的控件读错位。
     */
    public CustomComponentPanelWidget setTextData(Consumer<FriendlyByteBuf> writer, BiConsumer<FriendlyByteBuf, List<Component>> reader) {
        this.dataWriter = writer;
        this.dataReader = reader;
        return this;
    }
}
