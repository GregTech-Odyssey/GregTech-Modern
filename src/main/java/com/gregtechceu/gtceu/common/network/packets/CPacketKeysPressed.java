package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.utils.input.KeyBind;

import com.lowdragmc.lowdraglib.networking.IHandlerContext;
import com.lowdragmc.lowdraglib.networking.IPacket;

import net.minecraft.network.FriendlyByteBuf;

import io.netty.handler.codec.DecoderException;
import it.unimi.dsi.fastutil.booleans.BooleanBooleanPair;

import java.util.List;

@SuppressWarnings("unchecked")
public class CPacketKeysPressed implements IPacket {

    private Object updateKeys;

    public CPacketKeysPressed(List<KeyBind> updateKeys) {
        this.updateKeys = updateKeys;
    }

    @Override
    public void encode(FriendlyByteBuf buf) {
        List<KeyBind> updateKeys = (List<KeyBind>) this.updateKeys;
        buf.writeVarInt(updateKeys.size());
        for (KeyBind keyBind : updateKeys) {
            buf.writeVarInt(keyBind.ordinal());
            buf.writeBoolean(keyBind.isPressed());
            buf.writeBoolean(keyBind.isKeyDown());
        }
    }

    @Override
    public void decode(FriendlyByteBuf buf) {
        this.updateKeys = new BooleanBooleanPair[KeyBind.VALUES.length];
        BooleanBooleanPair[] updateKeys = (BooleanBooleanPair[]) this.updateKeys;
        int size = buf.readVarInt();
        if (size < 0 || size > KeyBind.VALUES.length) {
            throw new DecoderException("Invalid key count " + size);
        }
        for (int i = 0; i < size; i++) {
            int index = buf.readVarInt();
            boolean pressed = buf.readBoolean();
            boolean down = buf.readBoolean();
            if (index < 0 || index >= updateKeys.length) {
                throw new DecoderException("Invalid key index " + index);
            }
            updateKeys[index] = BooleanBooleanPair.of(pressed, down);
        }
    }

    @Override
    public void execute(IHandlerContext handler) {
        if (handler.getPlayer() != null) {
            KeyBind[] keybinds = KeyBind.VALUES;
            BooleanBooleanPair[] updateKeys = (BooleanBooleanPair[]) this.updateKeys;
            for (int i = 0; i < updateKeys.length; i++) {
                BooleanBooleanPair pair = updateKeys[i];
                if (pair != null) {
                    keybinds[i].update(pair.firstBoolean(), pair.secondBoolean(), handler.getPlayer());
                }
            }
        }
    }

    public CPacketKeysPressed() {}
}
