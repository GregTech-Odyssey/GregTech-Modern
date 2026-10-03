package com.gregtechceu.gtceu.uipro;

/**
 * 悬停提示的归属：光标所在的最深组件到某个带提示的祖先之间，若有组件自带提示（返回 true），祖先的提示让位给它。
 */
public interface ITooltipOwner {

    boolean hasOwnTooltip(int mouseX, int mouseY);
}
