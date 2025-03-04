/*
 * ---------------------------------------------------------------------------------------------------------------------
 * AUTO-GENERATED CLASS - DO NOT EDIT MANUALLY - for any changes edit BaseCharUpdateByOperator and regenerate
 * ---------------------------------------------------------------------------------------------------------------------
 */
package io.deephaven.engine.table.impl.updateby.internal;

import io.deephaven.util.QueryConstants;
import io.deephaven.engine.table.impl.sources.ByteArraySource;
import io.deephaven.engine.table.impl.sources.ByteSparseArraySource;
import io.deephaven.engine.table.WritableColumnSource;

import io.deephaven.chunk.Chunk;
import io.deephaven.chunk.IntChunk;
import io.deephaven.chunk.LongChunk;
import io.deephaven.chunk.WritableByteChunk;
import io.deephaven.chunk.attributes.Values;
import io.deephaven.engine.rowset.RowSequence;
import io.deephaven.engine.rowset.RowSet;
import io.deephaven.engine.table.*;
import io.deephaven.engine.table.impl.sources.*;
import io.deephaven.engine.table.impl.updateby.UpdateByOperator;
import io.deephaven.engine.table.impl.util.RowRedirection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

import static io.deephaven.engine.rowset.RowSequence.NULL_ROW_KEY;
import static io.deephaven.util.QueryConstants.*;

public abstract class BaseByteUpdateByOperator extends UpdateByOperator {
    protected final WritableColumnSource<Byte> outputSource;
    protected final WritableColumnSource<Byte> maybeInnerSource;

    // region extra-fields
    final byte nullValue;
    // endregion extra-fields

    protected abstract class Context extends UpdateByOperator.Context {
        public final ChunkSink.FillFromContext outputFillContext;
        public final WritableByteChunk<Values> outputValues;

        public byte curVal = NULL_BYTE;

        protected Context(final int chunkSize, final int chunkCount) {
            super(chunkCount);
            this.outputFillContext = outputSource.makeFillFromContext(chunkSize);
            this.outputValues = WritableByteChunk.makeWritableChunk(chunkSize);
        }

        @Override
        public void accumulateCumulative(RowSequence inputKeys,
                                         Chunk<? extends Values>[] valueChunkArr,
                                         LongChunk<? extends Values> tsChunk,
                                         int len) {

            setValuesChunk(valueChunkArr[0]);

            // chunk processing
            for (int ii = 0; ii < len; ii++) {
                push(NULL_ROW_KEY, ii, 1);
                writeToOutputChunk(ii);
            }

            // chunk output to column
            writeToOutputColumn(inputKeys);
        }

        @Override
        public void accumulateRolling(RowSequence inputKeys,
                                      Chunk<? extends Values>[] influencerValueChunkArr,
                                      IntChunk<? extends Values> pushChunk,
                                      IntChunk<? extends Values> popChunk,
                                      int len) {

            setValuesChunk(influencerValueChunkArr[0]);
            int pushIndex = 0;

            // chunk processing
            for (int ii = 0; ii < len; ii++) {
                final int pushCount = pushChunk.get(ii);
                final int popCount = popChunk.get(ii);

                if (pushCount == NULL_INT) {
                    writeNullToOutputChunk(ii);
                    continue;
                }

                // pop for this row
                if (popCount > 0) {
                    pop(popCount);
                }

                // push for this row
                if (pushCount > 0) {
                    push(NULL_ROW_KEY, pushIndex, pushCount);
                    pushIndex += pushCount;
                }

                // write the results to the output chunk
                writeToOutputChunk(ii);
            }

            // chunk output to column
            writeToOutputColumn(inputKeys);
        }

        @Override
        public void setValuesChunk(@NotNull final Chunk<? extends Values> valuesChunk) {}

        @Override
        public void writeToOutputChunk(int outIdx) {
            outputValues.set(outIdx, curVal);
        }

        void writeNullToOutputChunk(int outIdx) {
            outputValues.set(outIdx, NULL_BYTE);
        }

        @Override
        public void writeToOutputColumn(@NotNull final RowSequence inputKeys) {
            outputSource.fillFromChunk(outputFillContext, outputValues, inputKeys);
        }

        @Override
        public void reset() {
            curVal = NULL_BYTE;
        }

        @Override
        public void close() {
            super.close();
            outputValues.close();
            outputFillContext.close();
        }
    }

    /**
     * Construct a base operator for operations that produce byte outputs.
     *
     * @param pair             the {@link MatchPair} that defines the input/output for this operation
     * @param affectingColumns a list of all columns (including the input column from the pair) that affects the result
     *                         of this operator.
     * @param rowRedirection the {@link RowRedirection} for the output column
     */
    public BaseByteUpdateByOperator(@NotNull final MatchPair pair,
                                    @NotNull final String[] affectingColumns,
                                    @Nullable final RowRedirection rowRedirection
                                    // region extra-constructor-args
                                    // endregion extra-constructor-args
    ) {
        this(pair, affectingColumns, rowRedirection, null, 0, 0, false);
    }

    /**
     * Construct a base operator for operations that produce byte outputs.
     *
     * @param pair             the {@link MatchPair} that defines the input/output for this operation
     * @param affectingColumns a list of all columns (including the input column from the pair) that affects the result
     *                         of this operator.
     * @param rowRedirection the {@link RowRedirection} for the output column
     * @param timestampColumnName an optional timestamp column. If this is null, it will be assumed time is measured in
     *        integer ticks.
     * @param reverseWindowScaleUnits the reverse window for the operator. If no {@code timestampColumnName} is provided, this
     *                       is measured in ticks, otherwise it is measured in nanoseconds.
     * @param forwardWindowScaleUnits the forward window for the operator. If no {@code timestampColumnName} is provided, this
     *                       is measured in ticks, otherwise it is measured in nanoseconds.
     */
    public BaseByteUpdateByOperator(@NotNull final MatchPair pair,
                                    @NotNull final String[] affectingColumns,
                                    @Nullable final RowRedirection rowRedirection,
                                    @Nullable final String timestampColumnName,
                                    final long reverseWindowScaleUnits,
                                    final long forwardWindowScaleUnits,
                                    final boolean isWindowed
                                    // region extra-constructor-args
                                    // endregion extra-constructor-args
                                    ) {
        super(pair, affectingColumns, rowRedirection, timestampColumnName, reverseWindowScaleUnits, forwardWindowScaleUnits, isWindowed);
        if(rowRedirection != null) {
            // region create-dense
            this.maybeInnerSource = makeDenseSource();
            // endregion create-dense
            this.outputSource = WritableRedirectedColumnSource.maybeRedirect(rowRedirection, maybeInnerSource, 0);
        } else {
            this.maybeInnerSource = null;
            // region create-sparse
            this.outputSource = makeSparseSource();
            // endregion create-sparse
        }

        // region constructor
        this.nullValue = getNullValue();
        // endregion constructor
    }


    // region extra-methods
    protected byte getNullValue() {
        return QueryConstants.NULL_BYTE;
    }

    // region extra-methods
    protected WritableColumnSource<Byte> makeSparseSource() {
        return new ByteSparseArraySource();
    }

    protected WritableColumnSource<Byte> makeDenseSource() {
        return new ByteArraySource();
    }
    // endregion extra-methods

    @Override
    public void initializeCumulative(@NotNull UpdateByOperator.Context context, long firstUnmodifiedKey, long firstUnmodifiedTimestamp) {
        Context ctx = (Context) context;
        if (firstUnmodifiedKey != NULL_ROW_KEY) {
            ctx.curVal = outputSource.getByte(firstUnmodifiedKey);
        } else {
            ctx.reset();
        }
    }

    @Override
    public void startTrackingPrev() {
        outputSource.startTrackingPrevValues();
        if (rowRedirection != null) {
            assert maybeInnerSource != null;
            maybeInnerSource.startTrackingPrevValues();
        }
    }

    // region Shifts
    @Override
    public void applyOutputShift(@NotNull final RowSet subIndexToShift, final long delta) {
        if (outputSource instanceof BooleanSparseArraySource.ReinterpretedAsByte) {
            ((BooleanSparseArraySource.ReinterpretedAsByte)outputSource).shift(subIndexToShift, delta);
        } else {
            ((ByteSparseArraySource)outputSource).shift(subIndexToShift, delta);
        }
    }
    // endregion Shifts

    @Override
    public void prepareForParallelPopulation(final RowSet changedRows) {
        if (rowRedirection != null) {
            assert maybeInnerSource != null;
            ((WritableSourceWithPrepareForParallelPopulation) maybeInnerSource).prepareForParallelPopulation(changedRows);
        } else {
            ((WritableSourceWithPrepareForParallelPopulation) outputSource).prepareForParallelPopulation(changedRows);
        }
    }

    @NotNull
    @Override
    public Map<String, ColumnSource<?>> getOutputColumns() {
        return Collections.singletonMap(pair.leftColumn, outputSource);
    }

    // region clear-output
    @Override
    public void clearOutputRows(final RowSet toClear) {
        // NOP for primitive types
    }
    // endregion clear-output
}
