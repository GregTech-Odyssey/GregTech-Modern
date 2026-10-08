package com.gregtechceu.gtceu.api.gui.widget;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.transfer.key.KeyCodecs;
import com.gregtechceu.gtceu.api.transfer.key.Keys;
import com.gregtechceu.gtceu.uipro.data.RPC;
import com.gregtechceu.gtceu.uipro.data.SyncValue;
import com.gregtechceu.gtceu.uipro.data.UIChannel;

import com.lowdragmc.lowdraglib.gui.editor.annotation.ConfigSetter;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.ingredient.IGhostIngredientTarget;
import com.lowdragmc.lowdraglib.gui.ingredient.Target;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import com.google.common.collect.Lists;
import com.gto.datasynclib.datastream.codec.StreamCodec;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.emi.emi.api.stack.EmiStack;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@LDLRegister(name = "gtm_phantom_fluid_slot", group = "widget.gtm_container", priority = 50)
public class PhantomFluidWidget extends TankWidget implements IGhostIngredientTarget, UIChannel.Host {

    private static final StreamCodec<FriendlyByteBuf, GenericStack> OPTIONAL_STACK = StreamCodec.of((buf, stack) -> {
        buf.writeBoolean(stack != null);
        if (stack != null) KeyCodecs.GENERIC_STACK_STREAM_CODEC.encode(buf, stack);
    }, buf -> buf.readBoolean() ? KeyCodecs.GENERIC_STACK_STREAM_CODEC.decode(buf) : null);

    @Setter
    private Supplier<FluidStack> phantomFluidGetter;
    @Setter
    private Consumer<FluidStack> phantomFluidSetter;
    @Nullable
    protected FluidStack lastPhantomStack;
    @Nullable
    private GenericStack phantomSnapshot;
    @Nullable
    private FluidStack syncedPhantom;
    private final UIChannel channel = new UIChannel(this);
    private final RPC<Unit> clickRequest = channel.addRPC(this::serverClick);
    private final RPC<GenericStack> dropRequest = channel.addRPC(KeyCodecs.GENERIC_STACK_STREAM_CODEC, this::serverDrop)
            .validate(stack -> stack != null && stack.what() instanceof AEFluidKey && stack.amount() > 0);
    private final SyncValue<GenericStack> phantomValue = channel.addSyncValue(SyncValue.of(this::readPhantom, OPTIONAL_STACK, null)
            .onChanged(this::applyPhantom));

    public PhantomFluidWidget() {
        super();
    }

    public PhantomFluidWidget(@Nullable IFluidHandler fluidTank, int tank, int x, int y, int width, int height, Supplier<FluidStack> phantomFluidGetter, Consumer<FluidStack> phantomFluidSetter) {
        super(fluidTank, tank, x, y, width, height, false, false);
        this.phantomFluidGetter = phantomFluidGetter;
        this.phantomFluidSetter = phantomFluidSetter;
    }

    @Override
    public UIChannel getChannel() {
        return channel;
    }

    @ConfigSetter(field = "allowClickFilled")
    public PhantomFluidWidget setAllowClickFilled(boolean v) {
        // you cant modify it
        return this;
    }

    @ConfigSetter(field = "allowClickDrained")
    public PhantomFluidWidget setAllowClickDrained(boolean v) {
        // you cant modify it
        return this;
    }

    protected void setLastPhantomStack(FluidStack fluid) {
        if (fluid != null) {
            this.lastPhantomStack = fluid.copy();
            this.lastPhantomStack.setAmount(1);
        } else {
            this.lastPhantomStack = null;
        }
    }

    public static FluidStack drainFrom(Object ingredient) {
        if (ingredient instanceof Ingredient ing) {
            var items = ing.getItems();
            if (items.length > 0) {
                ingredient = items[0];
            }
        }
        if (ingredient instanceof ItemStack itemStack) {
            return FluidUtil.getFluidHandler(itemStack).map(h -> h.drain(Integer.MAX_VALUE, FluidAction.SIMULATE)).orElse(FluidStack.EMPTY);
        }
        return FluidStack.EMPTY;
    }

    @Nullable
    @OnlyIn(Dist.CLIENT)
    private static GenericStack phantomOf(@Nullable Object ingredient) {
        if (GTCEu.Mods.isEMILoaded() && ingredient instanceof EmiStack emiStack) {
            var key = emiStack.getKey();
            if (key instanceof Fluid f) {
                var fluidKey = AEFluidKey.of(Keys.source(f), emiStack.getNbt());
                return new GenericStack(fluidKey, emiStack.getAmount() <= 0 ? FluidType.BUCKET_VOLUME : emiStack.getAmount());
            } else if (key instanceof Item i) {
                var stack = new ItemStack(i, 1);
                stack.setTag(emiStack.getNbt());
                ingredient = stack;
            } else {
                return null;
            }
        }
        FluidStack fluid = ingredient instanceof FluidStack fluidStack ? fluidStack : drainFrom(ingredient);
        var fluidKey = Keys.fluid(fluid);
        return fluidKey == null ? null : new GenericStack(fluidKey, fluid.getAmount());
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public List<Target> getPhantomTargets(Object ingredient) {
        if (phantomOf(ingredient) == null) {
            return Collections.emptyList();
        }
        Rect2i rectangle = toRectangleBox();
        return Lists.newArrayList(new Target() {

            @Nonnull
            @Override
            public Rect2i getArea() {
                return rectangle;
            }

            @Override
            public void accept(@NotNull Object ingredient) {
                var stack = phantomOf(ingredient);
                if (isClientSideWidget) {
                    if (phantomFluidSetter != null) {
                        phantomFluidSetter.accept(stack == null ? FluidStack.EMPTY : Keys.toFluidStack((AEFluidKey) stack.what(), stack.amount()));
                    }
                } else if (stack != null) {
                    dropRequest.send(stack);
                }
            }
        });
    }

    private static boolean canInteract(@Nullable Player player) {
        return player == null || !player.isSpectator();
    }

    protected boolean acceptsServerInput() {
        return true;
    }

    private void serverClick(@Nullable Player player) {
        if (gui == null || !canInteract(player) || !acceptsServerInput()) return;
        handlePhantomClick();
    }

    private void serverDrop(@Nullable Player player, GenericStack stack) {
        if (phantomFluidSetter == null || !canInteract(player) || !acceptsServerInput()) return;
        phantomFluidSetter.accept(Keys.toFluidStack((AEFluidKey) stack.what(), stack.amount()));
    }

    @Nullable
    private GenericStack readPhantom() {
        FluidStack stack = phantomFluidGetter == null ? null : phantomFluidGetter.get();
        if (stack == null || stack.isEmpty()) {
            phantomSnapshot = null;
            return null;
        }
        var last = phantomSnapshot;
        if (last != null && last.amount() == stack.getAmount() && last.what() instanceof AEFluidKey key && key.matches(stack)) {
            return last;
        }
        var key = Keys.fluid(stack);
        phantomSnapshot = key == null ? null : new GenericStack(key, stack.getAmount());
        return phantomSnapshot;
    }

    private void applyPhantom(@Nullable GenericStack value) {
        if (value != null && value.what() instanceof AEFluidKey key) {
            syncedPhantom = Keys.toFluidStack(key, value.amount());
            setLastPhantomStack(syncedPhantom);
        } else {
            syncedPhantom = null;
            setLastPhantomStack(null);
        }
    }

    @Override
    public void initWidget() {
        super.initWidget();
        channel.prime();
    }

    @Override
    public void handleClientAction(int id, FriendlyByteBuf buffer) {
        channel.handleClientAction(id, buffer);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (!channel.readUpdateInfo(id, buffer)) super.readUpdateInfo(id, buffer);
    }

    @Override
    public void writeInitialData(FriendlyByteBuf buffer) {
        super.writeInitialData(buffer);
        channel.writeInitialData(buffer);
    }

    @Override
    public void readInitialData(FriendlyByteBuf buffer) {
        super.readInitialData(buffer);
        channel.readInitialData(buffer);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        channel.detectAndSendChanges();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void updateScreen() {
        super.updateScreen();
        channel.pollClient();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOverElement(mouseX, mouseY)) {
            if (isClientSideWidget) {
                handlePhantomClick();
            } else {
                clickRequest.send(Unit.INSTANCE);
            }
            return true;
        }
        return false;
    }

    private void handlePhantomClick() {
        ItemStack itemStack = gui.getModularUIContainer().getCarried();
        FluidStack fluid = FluidUtil.getFluidContained(itemStack).map(f -> new FluidStack(f, FluidType.BUCKET_VOLUME)).orElse(FluidStack.EMPTY);
        if (phantomFluidSetter != null) phantomFluidSetter.accept(fluid);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawInBackground(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (this.lastFluidInTank != null) {
            super.drawInBackground(graphics, mouseX, mouseY, partialTicks);
            return;
        }
        FluidStack stack = isClientSideWidget ? (phantomFluidGetter == null ? null : phantomFluidGetter.get()) : syncedPhantom;
        if (stack != null && !stack.isEmpty()) {
            RenderSystem.disableBlend();
            drawFluidContent(graphics, stack, showAmount);
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    @Nullable
    public FluidStack getLastPhantomStack() {
        return this.lastPhantomStack;
    }
}
