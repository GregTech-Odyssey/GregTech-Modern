package com.gto.datasynclib.listener;

import org.jetbrains.annotations.NotNull;

public interface ISyncNotifiable<L> {

    void setReceiverListener(@NotNull L receiverListener);

    void setSenderListener(@NotNull L senderListener);
}
