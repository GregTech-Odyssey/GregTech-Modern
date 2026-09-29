package com.gregtechceu.gtceu.uipro.window;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.uipro.styletemplate.UISizes;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import dev.emi.emi.config.EmiConfig;
import dev.emi.emi.config.Margins;
import dev.emi.emi.config.SidebarPages;
import dev.emi.emi.config.SidebarTheme;
import dev.emi.emi.runtime.EmiReloadManager;
import dev.emi.emi.screen.EmiScreenManager;
import it.unimi.dsi.fastutil.ints.IntList;

@OnlyIn(Dist.CLIENT)
public record ScreenArea(int left, int top, int right, int bottom) {

    public static ScreenArea current(int minWidth) {
        var window = Minecraft.getInstance().getWindow();
        int width = window.getGuiScaledWidth(), height = window.getGuiScaledHeight();
        int margin = UISizes.SCREEN_MARGIN;
        int left = margin, right = margin, top = margin, bottom = margin;
        if (GTCEu.Mods.isEMILoaded()) {
            var emi = EmiCompat.reserved();
            if (emi != null) {
                int sideLeft = Math.max(margin, emi[0]), sideRight = Math.max(margin, emi[1]);
                if (width - sideLeft - sideRight < minWidth) {
                    sideLeft = margin;
                    sideRight = margin;
                }
                left = sideLeft;
                right = sideRight;
                top = Math.max(top, emi[2]);
                bottom = Math.max(bottom, Math.max(emi[3], left < emi[4] ? EmiCompat.BOTTOM_BAR : 0));
            }
        }
        return new ScreenArea(left, top, Math.max(left, width - right), Math.max(top, height - bottom));
    }

    public int width() {
        return right - left;
    }

    public int height() {
        return bottom - top;
    }

    private static final class EmiCompat {

        private static final int ENTRY = 18;
        private static final int MIN_SIDE = ENTRY * 2;
        private static final int BOTTOM_BAR = 22 + UISizes.GAP;
        private static final int CORNER_BUTTONS = 46;
        private static final int HEADER = 18;

        private static int[] reserved() {
            if (!EmiReloadManager.isLoaded() || !EmiConfig.enabled) return null;
            int left = side(EmiConfig.leftSidebarPages);
            int right = side(EmiConfig.rightSidebarPages);
            int top = band(EmiConfig.topSidebarPages, EmiConfig.topSidebarSize.values, EmiConfig.topSidebarMargins, EmiConfig.topSidebarTheme);
            int bottom = band(EmiConfig.bottomSidebarPages, EmiConfig.bottomSidebarSize.values, EmiConfig.bottomSidebarMargins, EmiConfig.bottomSidebarTheme);
            if (EmiConfig.centerSearchBar && EmiScreenManager.search.isVisible()) bottom = Math.max(bottom, BOTTOM_BAR);
            return new int[] { left, right, top, bottom, CORNER_BUTTONS };
        }

        private static int side(SidebarPages pages) {
            return pages.pages.isEmpty() ? 0 : MIN_SIDE;
        }

        private static int band(SidebarPages pages, IntList size, Margins margins, SidebarTheme theme) {
            if (pages.pages.isEmpty()) return 0;
            return size.getInt(1) * ENTRY + margins.top() + margins.bottom() + 2 * theme.verticalPadding + HEADER;
        }
    }
}
