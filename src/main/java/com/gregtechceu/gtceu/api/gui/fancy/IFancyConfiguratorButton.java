package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.data.SyncValueHost;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public interface IFancyConfiguratorButton extends IFancyConfigurator {

    void onClick(ClickData clickData);

    default boolean isLatched() {
        return false;
    }

    default boolean isPersistent() {
        return false;
    }

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
        private final SyncValue<Boolean> state;
        Function<Boolean, List<Component>> tooltipsSupplier = isPressed -> Collections.emptyList();

        public Toggle(IGuiTexture base, IGuiTexture pressed, BooleanSupplier booleanSupplier, BiConsumer<ClickData, Boolean> onClick) {
            this.base = base;
            this.pressed = pressed;
            this.booleanSupplier = booleanSupplier;
            this.onClick = onClick;
            this.state = SyncValue.of(booleanSupplier::getAsBoolean, SyncValue.BOOLEAN, false);
        }

        @Override
        public void bindSync(SyncValueHost host) {
            host.add(state);
        }

        public boolean isPressed() {
            return state.getValue();
        }

        @Override
        public List<Component> getTooltips() {
            return tooltipsSupplier.apply(isPressed());
        }

        @Override
        public IGuiTexture getIcon() {
            return isPressed() ? pressed : base;
        }

        @Override
        public void onClick(ClickData clickData) {
            if (clickData.isRemote) {
                if (!isPersistent()) onClick.accept(clickData, !isPressed());
                return;
            }
            onClick.accept(clickData, !booleanSupplier.getAsBoolean());
        }

        @Override
        public boolean isPersistent() {
            return base != pressed;
        }

        @Override
        public boolean isLatched() {
            return isPressed() && isPersistent();
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
