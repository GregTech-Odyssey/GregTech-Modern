package com.gregtechceu.gtceu.uipro.view;

import net.minecraft.network.chat.Component;

public interface ViewController {

    default boolean canZoom() {
        return true;
    }

    default boolean isZoomLocked() {
        return false;
    }

    default void setZoomLocked(boolean locked) {}

    boolean canZoomIn();

    boolean canZoomOut();

    void zoomStep(int direction);

    String percentText();

    void fitView();

    default void percentClicked() {
        fitView();
    }

    default Component percentTooltip() {
        return ZoomBar.PERCENT;
    }

    default boolean hasMinimap() {
        return false;
    }

    default boolean isMinimapAvailable() {
        return hasMinimap();
    }

    default boolean isMinimapShown() {
        return false;
    }

    default void setMinimapShown(boolean shown) {}
}
