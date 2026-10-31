package com.gregtechceu.gtceu.api.gui.widget;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.data.lang.LangHandler;

import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.lowdragmc.lowdraglib.gui.widget.SwitchWidget;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLEnvironment;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * @deprecated Use {@link com.gregtechceu.gtceu.uipro.elements.IconToggle} or {@link com.gregtechceu.gtceu.uipro.elements.Switch} instead.
 */
@Deprecated
public class ToggleButtonWidget extends SwitchWidget {

    private final IGuiTexture texture;
    private String tooltipText;
    private boolean isMultiLang;
    private Function<Boolean, ? extends List<? extends Component>> dynamicTooltips;

    public ToggleButtonWidget(int xPosition, int yPosition, int width, int height, BooleanSupplier isPressedCondition,
                              BooleanConsumer setPressedExecutor) {
        this(xPosition, yPosition, width, height, GuiTextures.VANILLA_BUTTON, isPressedCondition, setPressedExecutor);
    }

    public ToggleButtonWidget(int xPosition, int yPosition, int width, int height, IGuiTexture buttonTexture,
                              BooleanSupplier isPressedCondition, BooleanConsumer setPressedExecutor) {
        super(xPosition, yPosition, width, height, null);
        texture = buttonTexture;
        if (buttonTexture instanceof ResourceTexture resourceTexture) {
            setTexture(resourceTexture.getSubTexture(0, 0, 1, 0.5), resourceTexture.getSubTexture(0, 0.5, 1, 0.5));
        } else {
            setTexture(buttonTexture, buttonTexture);
        }

        setSupplier(isPressedCondition::getAsBoolean);
        setOnPressCallback((cd, bool) -> {
            setPressedExecutor.accept(bool.booleanValue());
            this.updateHoverTooltips();
        });
    }

    public ToggleButtonWidget setShouldUseBaseBackground() {
        if (texture != null) {
            setTexture(
                    new GuiTextureGroup(GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0, 1, 0.5), texture),
                    new GuiTextureGroup(GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0.5, 1, 0.5), texture));
        }
        return this;
    }

    public ToggleButtonWidget setTooltipText(String tooltipText) {
        this.tooltipText = tooltipText;
        updateHoverTooltips();
        return this;
    }

    public ToggleButtonWidget isMultiLang() {
        isMultiLang = true;
        updateHoverTooltips();
        return this;
    }

    public ToggleButtonWidget setDynamicTooltips(Function<Boolean, ? extends List<? extends Component>> dynamicTooltips) {
        this.dynamicTooltips = dynamicTooltips;
        updateHoverTooltips();
        return this;
    }

    public ToggleButtonWidget onOffTooltips(Component onTooltip, Component offTooltip) {
        return setDynamicTooltips(pressed -> List.of(pressed ? onTooltip : offTooltip));
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        updateHoverTooltips();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        super.readUpdateInfo(id, buffer);
        updateHoverTooltips();
    }

    protected void updateHoverTooltips() {
        if (dynamicTooltips != null) {
            var list = dynamicTooltips.apply(isPressed);
            setHoverTooltips(list == null ? List.of() : List.copyOf(list));
            return;
        }
        if (tooltipText != null) {
            String stateKey = tooltipText + (isPressed ? ".enabled" : ".disabled");
            if (isMultiLang) {
                var lines = LangHandler.getMultiLang(stateKey);
                if (lines.isEmpty()) {
                    lines = LangHandler.getMultiLang(tooltipText);
                }
                if (!lines.isEmpty()) {
                    setHoverTooltips(List.copyOf(lines));
                    return;
                }
            }
            if (FMLEnvironment.dist == Dist.CLIENT) {
                if (net.minecraft.client.resources.language.I18n.exists(stateKey)) {
                    setHoverTooltips(stateKey);
                } else {
                    setHoverTooltips(tooltipText);
                }
            } else {
                setHoverTooltips(Component.translatable(stateKey));
            }
        }
    }
}
