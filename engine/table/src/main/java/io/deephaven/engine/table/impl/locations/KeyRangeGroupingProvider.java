/**
 * Copyright (c) 2016-2022 Deephaven Data Labs and Patent Pending
 */
package io.deephaven.engine.table.impl.locations;

import io.deephaven.engine.rowset.RowSet;
import io.deephaven.engine.rowset.TrackingWritableRowSet;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for {@link GroupingProvider}s that operate on metadata derived from a {@link ColumnLocation} for a given
 * {@link RowSet} key range.
 */
public interface KeyRangeGroupingProvider<DATA_TYPE> extends GroupingProvider<DATA_TYPE> {

    /**
     * Add a column location for consideration when constructing groupings.
     * 
     * @param columnLocation The column location to add
     * @param locationRowSetInTable The location's RowSet in the table
     */
    void addSource(@NotNull ColumnLocation columnLocation, @NotNull RowSet locationRowSetInTable);
}
