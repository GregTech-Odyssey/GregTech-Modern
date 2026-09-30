package com.gregtechceu.gtceu.uipro.view;

public interface ViewController {

    default boolean canZoom() {
        return true;
    }

    boolean canZoomIn();

    boolean canZoomOut();

    void zoomStep(int direction);

    String percentText();

    void fitView();

    default void percentClicked() {
        fitView();
    }

    default String percentTooltip() {
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
