package com.gregtechceu.gtceu.api.gui.fancy;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public interface IFancyConfiguratorButton extends IFancyConfigurator {

    void onClick(ClickData clickData);

    /**
     * 带点击玩家的版本（服务端是发起点击的玩家，客户端是本地玩家；取不到时为 null）。
     * 需要玩家的按钮（如打开独立窗口）覆写它；默认转给 {@link #onClick(ClickData)}。
     */
    default void onClick(ClickData clickData, @Nullable Player player) {
        onClick(clickData);
    }

    @Override
    default Component getTitle() {
        throw new NotImplementedException();
    }

    @Override
    default Widget createConfigurator() {
        throw new NotImplementedException();
    }

    class Toggle implements IFancyConfiguratorButton {

        IGuiTexture base;
        IGuiTexture pressed;
        BiConsumer<ClickData, Boolean> onClick;
        BooleanSupplier booleanSupplier;
        boolean isPressed;
        Function<Boolean, List<Component>> tooltipsSupplier = isPressed -> Collections.emptyList();

        public Toggle(IGuiTexture base, IGuiTexture pressed, BooleanSupplier booleanSupplier, BiConsumer<ClickData, Boolean> onClick) {
            this.base = base;
            this.pressed = pressed;
            this.booleanSupplier = booleanSupplier;
            this.onClick = onClick;
        }

        @Override
        public List<Component> getTooltips() {
            return tooltipsSupplier.apply(isPressed);
        }

        @Override
        public void detectAndSendChange(BiConsumer<Integer, Consumer<FriendlyByteBuf>> sender) {
            var newIsPressed = booleanSupplier.getAsBoolean();
            if (newIsPressed != isPressed) {
                isPressed = newIsPressed;
                sender.accept(0, buf -> buf.writeBoolean(isPressed));
            }
        }

        @Override
        public void readUpdateInfo(int id, FriendlyByteBuf buf) {
            if (id == 0) {
                isPressed = buf.readBoolean();
            }
        }

        @Override
        public void writeInitialData(FriendlyByteBuf buffer) {
            this.isPressed = booleanSupplier.getAsBoolean();
            buffer.writeBoolean(this.isPressed);
        }

        @Override
        public void readInitialData(FriendlyByteBuf buffer) {
            this.isPressed = buffer.readBoolean();
        }

        @Override
        public IGuiTexture getIcon() {
            return isPressed ? pressed : base;
        }

        @Override
        public void onClick(ClickData clickData) {
            onClick.accept(clickData, !isPressed);
        }

        /**
         * @return {@code this}.
         */
        public IFancyConfiguratorButton.Toggle setTooltipsSupplier(final Function<Boolean, List<Component>> tooltipsSupplier) {
            this.tooltipsSupplier = tooltipsSupplier;
            return this;
        }
    }
}
