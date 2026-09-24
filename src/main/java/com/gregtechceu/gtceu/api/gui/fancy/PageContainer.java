package com.gregtechceu.gtceu.api.gui.fancy;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 机器界面放当前页面的容器。
 * <p>
 * 切换页面时两端不同步：客户端点下标签就换页、同时发包，服务端收到包才换页，换上的页面由服务端发来初始数据（id 2）。
 * 在这之前服务端<b>旧页面</b>发出的更新（如运行中的输出仓，储量每 tick 都在变）按"第几个子控件"路由，会被客户端送进已经换上的新页面，
 * 读出错位的数据；服务端 min/max 这类不再变化的值因此永远不会被纠正（输出仓优先级页的上下限变成乱数、加减按钮被错误禁用）。
 * <p>
 * 所以客户端每换一次页记一笔"待服务端换页"：待换期间丢弃服务端的子控件更新（id 1）；每收到一次初始数据（id 2）销一笔，
 * 只有最后一笔对应的初始数据才读进页面（前面的属于服务端中途换上又换掉的页面，与客户端当前页面对不上）。
 * 界面打开阶段两端各自建首页、服务端不发初始数据，不记账——以客户端第一次绘制为界。
 */
public class PageContainer extends WidgetGroup {

    /// WidgetGroup 自用的更新 ID：1 子控件更新，2 新加入子控件的初始数据
    private static final int CHILD_UPDATE = 1;
    private static final int CHILD_INIT = 2;

    /// 客户端：界面已经画出来过（打开阶段结束）
    private boolean shown;
    /// 客户端：还没等到服务端初始数据的换页次数
    private int pendingPages;

    public PageContainer(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    @Override
    public void clearAllWidgets() {
        super.clearAllWidgets();
        if (shown) pendingPages++;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        shown = true;
        super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (pendingPages > 0) {
            if (id == CHILD_UPDATE) return;
            if (id == CHILD_INIT && --pendingPages > 0) return;
        }
        super.readUpdateInfo(id, buffer);
    }
}
