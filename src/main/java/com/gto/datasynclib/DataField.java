package com.gto.datasynclib;

import com.gto.datasynclib.datasream.data.Data;
import com.gto.datasynclib.datasream.stream.ByteDataStream;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

public interface DataField<T> {

    DataFieldDefinition<T> getDefinition();

    void markAsDirty();

    boolean isDirty();

    void clearDirty();

    boolean hasChanges(@NotNull LogicalSide side, @NotNull Object source, boolean auto);

    void writeToBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull ByteDataStream data, boolean force) throws IOException;

    void readFromBuffer(@NotNull LogicalSide side, @NotNull Object source, @NotNull ByteDataStream data) throws IOException;

    @NotNull
    Data writeToData(@NotNull Object source);

    void readFromData(@NotNull Object source, @NotNull Data data);

    interface Factory<T> {

        @SuppressWarnings("rawtypes")
        DataField<T> create(DataFieldDefinition definition);
    }

    record CustomFactory<T>(Predicate<Class<?>> predicate, Function<Class<?>, Factory<T>> factory, int priority) {}

    record CustomGenericFactory<T>(BiPredicate<Class<?>, Class<?>[]> predicate, BiFunction<Class<?>, Class<?>[], Factory<T>> factory, int priority) {}
}
