package com.gregtechceu.gtceu.uiwidgets.multiblock;

/**
 * 标准多方块主页给机器留的两处扩展：屏幕里的读数行、屏下的操作面板。两端建页时都会以同样顺序调用，
 * 只注册控件，不能有副作用；取值函数在服务端每次同步时调用，要便宜（数值用 {@link MultiblockPage#addNumber} 等带缓存的写法）。
 */
public interface IMultiblockPageMachine {

    default void addScreenReadouts(MultiblockPage page) {}

    default void addControls(ControlPanel controls) {}
}
