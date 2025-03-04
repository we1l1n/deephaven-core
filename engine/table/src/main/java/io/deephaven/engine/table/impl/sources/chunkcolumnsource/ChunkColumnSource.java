/**
 * Copyright (c) 2016-2022 Deephaven Data Labs and Patent Pending
 */
package io.deephaven.engine.table.impl.sources.chunkcolumnsource;

import gnu.trove.list.array.TLongArrayList;
import io.deephaven.chunk.attributes.Values;
import io.deephaven.engine.table.ColumnSource;
import io.deephaven.chunk.ChunkType;
import io.deephaven.chunk.WritableChunk;
import org.jetbrains.annotations.NotNull;

/**
 * An immutable ColumnSource that is backed by chunks.
 * <p>
 * The owner of the column source may append chunks to with the addChunk call.
 *
 * @param <T> the data type of the column source
 */
public interface ChunkColumnSource<T> extends ColumnSource<T> {
    /**
     * Create a new ChunkColumnSource for the given chunk type and data type.
     *
     * @param chunkType the type of chunk
     * @param dataType the datatype for the newly created column source
     * @return an empty ChunkColumnSource
     */
    static ChunkColumnSource<?> make(ChunkType chunkType, Class<?> dataType) {
        return make(chunkType, dataType, (Class<?>) null);
    }

    /**
     * Create a new ChunkColumnSource for the given chunk type and data type.
     *
     * @param chunkType the type of chunk
     * @param dataType the datatype for the newly created column source
     * @param componentType the component type for the newly created column source (only applies to Objects)
     * @return an empty ChunkColumnSource
     */
    @SuppressWarnings("unchecked")
    static <T> ChunkColumnSource<T> make(ChunkType chunkType, Class<T> dataType, Class<?> componentType) {
        switch (chunkType) {
            case Char:
                return (ChunkColumnSource<T>) new CharChunkColumnSource();
            case Byte:
                return (ChunkColumnSource<T>) new ByteChunkColumnSource();
            case Short:
                return (ChunkColumnSource<T>) new ShortChunkColumnSource();
            case Int:
                return (ChunkColumnSource<T>) new IntChunkColumnSource();
            case Long:
                return (ChunkColumnSource<T>) new LongChunkColumnSource();
            case Float:
                return (ChunkColumnSource<T>) new FloatChunkColumnSource();
            case Double:
                return (ChunkColumnSource<T>) new DoubleChunkColumnSource();
            case Object:
                return new ObjectChunkColumnSource<>(dataType, componentType);
            default:
                throw new IllegalArgumentException("Can not make ChunkColumnSource of type " + chunkType);
        }
    }

    /**
     * Create a new ChunkColumnSource for the given chunk type and data type.
     *
     * @param chunkType the type of chunk
     * @param dataType the datatype for the newly created column source
     * @param sharedOffsetForData an array list representing the shared offsets for data across several
     *        ChunkColumnSources
     * @return an empty ChunkColumnSource
     */
    static ChunkColumnSource<?> make(ChunkType chunkType, Class<?> dataType, TLongArrayList sharedOffsetForData) {
        return make(chunkType, dataType, null, sharedOffsetForData);
    }

    /**
     * Create a new ChunkColumnSource for the given chunk type and data type.
     *
     * @param chunkType the type of chunk
     * @param dataType the datatype for the newly created column source
     * @param componentType the component type for the newly created column source (only applies to Objects)
     * @param sharedOffsetForData an array list representing the shared offsets for data across several
     *        ChunkColumnSources
     * @return an empty ChunkColumnSource
     */
    static ChunkColumnSource<?> make(ChunkType chunkType, Class<?> dataType, Class<?> componentType,
            TLongArrayList sharedOffsetForData) {
        switch (chunkType) {
            case Char:
                return new CharChunkColumnSource(sharedOffsetForData);
            case Byte:
                return new ByteChunkColumnSource(sharedOffsetForData);
            case Short:
                return new ShortChunkColumnSource(sharedOffsetForData);
            case Int:
                return new IntChunkColumnSource(sharedOffsetForData);
            case Long:
                return new LongChunkColumnSource(sharedOffsetForData);
            case Float:
                return new FloatChunkColumnSource(sharedOffsetForData);
            case Double:
                return new DoubleChunkColumnSource(sharedOffsetForData);
            case Object:
                return new ObjectChunkColumnSource<>(dataType, componentType, sharedOffsetForData);
            default:
                throw new IllegalArgumentException("Can not make ChunkColumnSource of type " + chunkType);
        }
    }

    /**
     * Append a chunk of data to this column source.
     *
     * The chunk must not be empty (i.e., the size must be greater than zero).
     *
     * @param chunk the chunk of data to add
     */
    void addChunk(@NotNull WritableChunk<? extends Values> chunk);

    /**
     * Reset the column source to be ready for reuse.
     * <p>
     * Clear will discard the currently held chunks. This should not be called if a table will continue to reference the
     * column source; as it violates the immutability contract.
     */
    void clear();

    /**
     * Get the size of this column source (one more than the last valid row key).
     *
     * @return the size of this column source
     */
    long getSize();
}
