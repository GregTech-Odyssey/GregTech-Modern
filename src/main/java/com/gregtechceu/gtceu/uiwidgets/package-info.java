/**
 * 用新式界面框架（{@link com.gregtechceu.gtceu.uipro}）拼出来的界面小组件，按组件分子包：
 * <ul>
 * <li>{@code display}：机器状态显示（多方块等 {@code addDisplayText} 的显示窗）；</li>
 * <li>{@code circuit}：编程电路选择器；</li>
 * <li>{@code inventory}：共享物品库 / 共享流体库这类小格子库存；</li>
 * <li>{@code mode}：机器模式（配方类型）选择；</li>
 * <li>{@code number}：单个数值的设置页（优先级等）。</li>
 * </ul>
 * 与框架的分工：{@code uipro} 只放通用的元素、布局、外壳和样式（不认识任何具体机器或功能）；
 * 这里的组件知道自己的业务（电路、配方类型、库存……），只用框架的标准元素拼装，不自己画界面元素。
 * GTM 原有的配置项类（{@code api.machine.fancyconfigurator} 等）保留接口不变，界面部分桥接到这里。
 */
package com.gregtechceu.gtceu.uiwidgets;
