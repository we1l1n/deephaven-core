package io.deephaven.engine.table.impl.updateby;

import io.deephaven.api.agg.Pair;
import io.deephaven.api.updateby.ColumnUpdateOperation;
import io.deephaven.api.updateby.OperationControl;
import io.deephaven.api.updateby.UpdateByControl;
import io.deephaven.api.updateby.UpdateByOperation;
import io.deephaven.api.updateby.spec.*;
import io.deephaven.engine.table.ColumnSource;
import io.deephaven.engine.table.MatchPair;
import io.deephaven.engine.table.Table;
import io.deephaven.engine.table.impl.TableDefaults;
import io.deephaven.engine.table.impl.updateby.ema.*;
import io.deephaven.engine.table.impl.updateby.fill.*;
import io.deephaven.engine.table.impl.updateby.minmax.*;
import io.deephaven.engine.table.impl.updateby.prod.*;
import io.deephaven.engine.table.impl.updateby.rollingsum.*;
import io.deephaven.engine.table.impl.updateby.sum.*;
import io.deephaven.engine.table.impl.util.WritableRowRedirection;
import io.deephaven.time.DateTime;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static io.deephaven.util.BooleanUtils.NULL_BOOLEAN_AS_BYTE;
import static io.deephaven.util.QueryConstants.NULL_BYTE;

/**
 * A factory to visit all of the {@link UpdateByOperation}s to produce a set of {@link UpdateByOperator}s that
 * {@link UpdateBy} can use to produce a result.
 */
public class UpdateByOperatorFactory {
    private final TableDefaults source;
    private final MatchPair[] groupByColumns;
    @Nullable
    private final WritableRowRedirection rowRedirection;
    @NotNull
    private final UpdateByControl control;

    public UpdateByOperatorFactory(@NotNull final TableDefaults source,
            @NotNull final MatchPair[] groupByColumns,
            @Nullable final WritableRowRedirection rowRedirection,
            @NotNull final UpdateByControl control) {
        this.source = source;
        this.groupByColumns = groupByColumns;
        this.rowRedirection = rowRedirection;
        this.control = control;
    }

    final Collection<UpdateByOperator> getOperators(@NotNull final Collection<? extends UpdateByOperation> specs) {
        final OperationVisitor v = new OperationVisitor();
        specs.forEach(s -> s.walk(v));
        return v.ops;
    }

    static MatchPair[] parseMatchPairs(final List<Pair> columns) {
        return columns.stream()
                .map(MatchPair::of)
                .toArray(MatchPair[]::new);
    }

    /**
     * If the input columns to add is an empty array, create a new one that maps each column to itself in the result
     *
     * @param table the source table
     * @param columnsToAdd the list of {@link MatchPair}s for the result columns
     * @return the input columns to add if it was non-empty, or a new one that maps each source column 1:1 to the
     *         output.
     */
    @NotNull
    static MatchPair[] createColumnsToAddIfMissing(final @NotNull Table table,
            final @NotNull MatchPair[] columnsToAdd,
            final @NotNull UpdateBySpec spec,
            final MatchPair[] groupByColumns) {
        if (columnsToAdd.length == 0) {
            return createOneToOneMatchPairs(table, groupByColumns, spec);
        }
        return columnsToAdd;
    }

    /**
     * Create a new {@link MatchPair} array that maps each input column to itself on the output side.
     *
     * @param table the source table.
     * @param groupByColumns the columns to group the table by
     * @return A new {@link MatchPair}[] that maps each source column 1:1 to the output.
     */
    @NotNull
    static MatchPair[] createOneToOneMatchPairs(final @NotNull Table table,
            final MatchPair[] groupByColumns,
            @NotNull final UpdateBySpec spec) {
        final Set<String> usedGroupColumns = groupByColumns.length == 0 ? Collections.emptySet()
                : Arrays.stream(groupByColumns)
                        .map(MatchPair::rightColumn).collect(Collectors.toSet());
        return table.getDefinition().getColumnStream()
                .filter(c -> !usedGroupColumns.contains(c.getName()) && spec.applicableTo(c.getDataType()))
                .map(c -> new MatchPair(c.getName(), c.getName()))
                .toArray(MatchPair[]::new);
    }

    public String describe(Collection<? extends UpdateByOperation> clauses) {
        final Describer d = new Describer();
        clauses.forEach(c -> c.walk(d));
        return d.descriptionBuilder.toString();
    }

    private static class Describer implements UpdateByOperation.Visitor<Void> {
        final StringBuilder descriptionBuilder = new StringBuilder();

        @Override
        public Void visit(ColumnUpdateOperation clause) {
            final MatchPair[] pairs = parseMatchPairs(clause.columns());
            final String columnStr;
            if (pairs.length == 0) {
                columnStr = "[All]";
            } else {
                columnStr = MatchPair.matchString(pairs);
            }

            descriptionBuilder.append(clause.spec().toString()).append("(").append(columnStr).append("), ");
            return null;
        }
    }

    private class OperationVisitor implements UpdateBySpec.Visitor<Void>, UpdateByOperation.Visitor<Void> {
        private final List<UpdateByOperator> ops = new ArrayList<>();
        private MatchPair[] pairs;

        /**
         * Check if the supplied type is one of the supported time types.
         *
         * @param type the type
         * @return true if the type is one of the useable time types
         */
        public boolean isTimeType(final @NotNull Class<?> type) {
            // TODO: extend time handling similar to enterprise (Instant, ZonedDateTime, LocalDate, LocalTime)
            return type == DateTime.class;
        }

        @Override
        public Void visit(@NotNull final ColumnUpdateOperation clause) {
            final UpdateBySpec spec = clause.spec();
            pairs = createColumnsToAddIfMissing(source, parseMatchPairs(clause.columns()), spec, groupByColumns);
            spec.walk(this);
            pairs = null;
            return null;
        }

        @Override
        public Void visit(@NotNull final EmaSpec ema) {
            final boolean isTimeBased = ema.timeScale().isTimeBased();
            final String timestampCol = ema.timeScale().timestampCol();

            Arrays.stream(pairs)
                    .filter(p -> !isTimeBased || !p.rightColumn().equals(timestampCol))
                    .map(fc -> makeEmaOperator(fc,
                            source,
                            ema))
                    .forEach(ops::add);
            return null;
        }

        @Override
        public Void visit(@NotNull final FillBySpec f) {
            Arrays.stream(pairs)
                    .map(fc -> makeForwardFillOperator(fc, source))
                    .forEach(ops::add);
            return null;
        }

        @Override
        public Void visit(@NotNull final CumSumSpec c) {
            Arrays.stream(pairs)
                    .map(fc -> makeCumSumOperator(fc, source))
                    .forEach(ops::add);
            return null;
        }

        @Override
        public Void visit(CumMinMaxSpec m) {
            Arrays.stream(pairs)
                    .map(fc -> makeCumMinMaxOperator(fc, source, m.isMax()))
                    .forEach(ops::add);
            return null;
        }

        @Override
        public Void visit(CumProdSpec p) {
            Arrays.stream(pairs)
                    .map(fc -> makeCumProdOperator(fc, source))
                    .forEach(ops::add);
            return null;
        }

        @Override
        public Void visit(@NotNull final RollingSumSpec rs) {
            final boolean isTimeBased = rs.revTimeScale().isTimeBased();
            final String timestampCol = rs.revTimeScale().timestampCol();

            Arrays.stream(pairs)
                    .filter(p -> !isTimeBased || !p.rightColumn().equals(timestampCol))
                    .map(fc -> makeRollingSumOperator(fc,
                            source,
                            rs))
                    .forEach(ops::add);
            return null;
        }

        private UpdateByOperator makeEmaOperator(@NotNull final MatchPair pair,
                @NotNull final TableDefaults source,
                @NotNull final EmaSpec ema) {
            // noinspection rawtypes
            final ColumnSource columnSource = source.getColumnSource(pair.rightColumn);
            final Class<?> csType = columnSource.getType();

            final String[] affectingColumns;
            if (ema.timeScale().timestampCol() == null) {
                affectingColumns = new String[] {pair.rightColumn};
            } else {
                affectingColumns = new String[] {ema.timeScale().timestampCol(), pair.rightColumn};
            }

            // use the correct units from the EmaSpec (depending on if Time or Tick based)
            final long timeScaleUnits = ema.timeScale().timescaleUnits();
            final OperationControl control = ema.controlOrDefault();

            if (csType == byte.class || csType == Byte.class) {
                return new ByteEMAOperator(pair, affectingColumns, rowRedirection, control,
                        ema.timeScale().timestampCol(), timeScaleUnits, columnSource);
            } else if (csType == short.class || csType == Short.class) {
                return new ShortEMAOperator(pair, affectingColumns, rowRedirection, control,
                        ema.timeScale().timestampCol(), timeScaleUnits, columnSource);
            } else if (csType == int.class || csType == Integer.class) {
                return new IntEMAOperator(pair, affectingColumns, rowRedirection, control,
                        ema.timeScale().timestampCol(), timeScaleUnits, columnSource);
            } else if (csType == long.class || csType == Long.class) {
                return new LongEMAOperator(pair, affectingColumns, rowRedirection, control,
                        ema.timeScale().timestampCol(), timeScaleUnits, columnSource);
            } else if (csType == float.class || csType == Float.class) {
                return new FloatEMAOperator(pair, affectingColumns, rowRedirection, control,
                        ema.timeScale().timestampCol(), timeScaleUnits, columnSource);
            } else if (csType == double.class || csType == Double.class) {
                return new DoubleEMAOperator(pair, affectingColumns, rowRedirection, control,
                        ema.timeScale().timestampCol(), timeScaleUnits, columnSource);
            } else if (csType == BigDecimal.class) {
                return new BigDecimalEMAOperator(pair, affectingColumns, rowRedirection, control,
                        ema.timeScale().timestampCol(), timeScaleUnits, columnSource);
            } else if (csType == BigInteger.class) {
                return new BigIntegerEMAOperator(pair, affectingColumns, rowRedirection, control,
                        ema.timeScale().timestampCol(), timeScaleUnits, columnSource);
            }

            throw new IllegalArgumentException("Can not perform EMA on type " + csType);
        }

        private UpdateByOperator makeCumProdOperator(MatchPair fc, TableDefaults source) {
            final Class<?> csType = source.getColumnSource(fc.rightColumn).getType();
            if (csType == byte.class || csType == Byte.class) {
                return new ByteCumProdOperator(fc, rowRedirection);
            } else if (csType == short.class || csType == Short.class) {
                return new ShortCumProdOperator(fc, rowRedirection);
            } else if (csType == int.class || csType == Integer.class) {
                return new IntCumProdOperator(fc, rowRedirection);
            } else if (csType == long.class || csType == Long.class) {
                return new LongCumProdOperator(fc, rowRedirection);
            } else if (csType == float.class || csType == Float.class) {
                return new FloatCumProdOperator(fc, rowRedirection);
            } else if (csType == double.class || csType == Double.class) {
                return new DoubleCumProdOperator(fc, rowRedirection);
            } else if (csType == BigDecimal.class) {
                return new BigDecimalCumProdOperator(fc, rowRedirection, control.mathContextOrDefault());
            } else if (csType == BigInteger.class) {
                return new BigIntegerCumProdOperator(fc, rowRedirection);
            }

            throw new IllegalArgumentException("Can not perform Cumulative Min/Max on type " + csType);
        }

        private UpdateByOperator makeCumMinMaxOperator(MatchPair fc, TableDefaults source, boolean isMax) {
            final ColumnSource<?> columnSource = source.getColumnSource(fc.rightColumn);
            final Class<?> csType = columnSource.getType();
            if (csType == byte.class || csType == Byte.class) {
                return new ByteCumMinMaxOperator(fc, isMax, rowRedirection);
            } else if (csType == short.class || csType == Short.class) {
                return new ShortCumMinMaxOperator(fc, isMax, rowRedirection);
            } else if (csType == int.class || csType == Integer.class) {
                return new IntCumMinMaxOperator(fc, isMax, rowRedirection);
            } else if (csType == long.class || csType == Long.class || isTimeType(csType)) {
                return new LongCumMinMaxOperator(fc, isMax, rowRedirection, csType);
            } else if (csType == float.class || csType == Float.class) {
                return new FloatCumMinMaxOperator(fc, isMax, rowRedirection);
            } else if (csType == double.class || csType == Double.class) {
                return new DoubleCumMinMaxOperator(fc, isMax, rowRedirection);
            } else if (Comparable.class.isAssignableFrom(csType)) {
                // noinspection rawtypes
                return new ComparableCumMinMaxOperator(fc, isMax, rowRedirection, csType);
            }

            throw new IllegalArgumentException("Can not perform Cumulative Min/Max on type " + csType);
        }

        private UpdateByOperator makeCumSumOperator(MatchPair fc, TableDefaults source) {
            final Class<?> csType = source.getColumnSource(fc.rightColumn).getType();
            if (csType == Boolean.class || csType == boolean.class) {
                return new ByteCumSumOperator(fc, rowRedirection, NULL_BOOLEAN_AS_BYTE);
            } else if (csType == byte.class || csType == Byte.class) {
                return new ByteCumSumOperator(fc, rowRedirection, NULL_BYTE);
            } else if (csType == short.class || csType == Short.class) {
                return new ShortCumSumOperator(fc, rowRedirection);
            } else if (csType == int.class || csType == Integer.class) {
                return new IntCumSumOperator(fc, rowRedirection);
            } else if (csType == long.class || csType == Long.class) {
                return new LongCumSumOperator(fc, rowRedirection);
            } else if (csType == float.class || csType == Float.class) {
                return new FloatCumSumOperator(fc, rowRedirection);
            } else if (csType == double.class || csType == Double.class) {
                return new DoubleCumSumOperator(fc, rowRedirection);
            } else if (csType == BigDecimal.class) {
                return new BigDecimalCumSumOperator(fc, rowRedirection, control.mathContextOrDefault());
            } else if (csType == BigInteger.class) {
                return new BigIntegerCumSumOperator(fc, rowRedirection);
            }

            throw new IllegalArgumentException("Can not perform Cumulative Sum on type " + csType);
        }

        private UpdateByOperator makeForwardFillOperator(MatchPair fc, TableDefaults source) {
            final ColumnSource<?> columnSource = source.getColumnSource(fc.rightColumn);
            final Class<?> csType = columnSource.getType();
            if (csType == char.class || csType == Character.class) {
                return new CharFillByOperator(fc, rowRedirection);
            } else if (csType == byte.class || csType == Byte.class) {
                return new ByteFillByOperator(fc, rowRedirection);
            } else if (csType == short.class || csType == Short.class) {
                return new ShortFillByOperator(fc, rowRedirection);
            } else if (csType == int.class || csType == Integer.class) {
                return new IntFillByOperator(fc, rowRedirection);
            } else if (csType == long.class || csType == Long.class || isTimeType(csType)) {
                return new LongFillByOperator(fc, rowRedirection, csType);
            } else if (csType == float.class || csType == Float.class) {
                return new FloatFillByOperator(fc, rowRedirection);
            } else if (csType == double.class || csType == Double.class) {
                return new DoubleFillByOperator(fc, rowRedirection);
            } else if (csType == boolean.class || csType == Boolean.class) {
                return new BooleanFillByOperator(fc, rowRedirection);
            } else {
                return new ObjectFillByOperator<>(fc, rowRedirection, csType);
            }
        }

        private UpdateByOperator makeRollingSumOperator(@NotNull final MatchPair pair,
                @NotNull final TableDefaults source,
                @NotNull final RollingSumSpec rs) {
            // noinspection rawtypes
            final ColumnSource columnSource = source.getColumnSource(pair.rightColumn);
            final Class<?> csType = columnSource.getType();

            final String[] affectingColumns;
            if (rs.revTimeScale().timestampCol() == null) {
                affectingColumns = new String[] {pair.rightColumn};
            } else {
                affectingColumns = new String[] {rs.revTimeScale().timestampCol(), pair.rightColumn};
            }

            final long prevTimeScaleUnits = rs.revTimeScale().timescaleUnits();
            final long fwdTimeScaleUnits = rs.fwdTimeScale().timescaleUnits();

            if (csType == Boolean.class || csType == boolean.class) {
                return new ByteRollingSumOperator(pair, affectingColumns, rowRedirection,
                        rs.revTimeScale().timestampCol(),
                        prevTimeScaleUnits, fwdTimeScaleUnits, NULL_BOOLEAN_AS_BYTE);
            } else if (csType == byte.class || csType == Byte.class) {
                return new ByteRollingSumOperator(pair, affectingColumns, rowRedirection,
                        rs.revTimeScale().timestampCol(),
                        prevTimeScaleUnits, fwdTimeScaleUnits, NULL_BYTE);
            } else if (csType == short.class || csType == Short.class) {
                return new ShortRollingSumOperator(pair, affectingColumns, rowRedirection,
                        rs.revTimeScale().timestampCol(),
                        prevTimeScaleUnits, fwdTimeScaleUnits);
            } else if (csType == int.class || csType == Integer.class) {
                return new IntRollingSumOperator(pair, affectingColumns, rowRedirection,
                        rs.revTimeScale().timestampCol(),
                        prevTimeScaleUnits, fwdTimeScaleUnits);
            } else if (csType == long.class || csType == Long.class) {
                return new LongRollingSumOperator(pair, affectingColumns, rowRedirection,
                        rs.revTimeScale().timestampCol(),
                        prevTimeScaleUnits, fwdTimeScaleUnits);
            } else if (csType == float.class || csType == Float.class) {
                return new FloatRollingSumOperator(pair, affectingColumns, rowRedirection,
                        rs.revTimeScale().timestampCol(),
                        prevTimeScaleUnits, fwdTimeScaleUnits);
            } else if (csType == double.class || csType == Double.class) {
                return new DoubleRollingSumOperator(pair, affectingColumns, rowRedirection,
                        rs.revTimeScale().timestampCol(),
                        prevTimeScaleUnits, fwdTimeScaleUnits);
            } else if (csType == BigDecimal.class) {
                return new BigDecimalRollingSumOperator(pair, affectingColumns, rowRedirection,
                        rs.revTimeScale().timestampCol(),
                        prevTimeScaleUnits, fwdTimeScaleUnits, control.mathContextOrDefault());
            } else if (csType == BigInteger.class) {
                return new BigIntegerRollingSumOperator(pair, affectingColumns, rowRedirection,
                        rs.revTimeScale().timestampCol(),
                        prevTimeScaleUnits, fwdTimeScaleUnits);
            }

            throw new IllegalArgumentException("Can not perform RollingSum on type " + csType);
        }
    }
}
