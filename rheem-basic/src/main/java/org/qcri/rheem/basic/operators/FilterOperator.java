package org.qcri.rheem.basic.operators;

import org.apache.commons.lang3.Validate;
import org.qcri.rheem.core.api.Configuration;
import org.qcri.rheem.core.function.PredicateDescriptor;
import org.qcri.rheem.core.optimizer.cardinality.CardinalityEstimate;
import org.qcri.rheem.core.plan.rheemplan.UnaryToUnaryOperator;
import org.qcri.rheem.core.types.BasicDataUnitType;
import org.qcri.rheem.core.types.DataSetType;

import java.util.Optional;


/**
 * This operator returns a new dataset after filtering by applying predicateDescriptor.
 */
public class FilterOperator<Type> extends UnaryToUnaryOperator<Type, Type> {

    /**
     * Function that this operator applies to the input elements.
     */
    protected final PredicateDescriptor<Type> predicateDescriptor;

    /**
     * Creates a new instance.
     */
    public FilterOperator(PredicateDescriptor.SerializablePredicate<Type> predicateDescriptor, Class<Type> typeClass) {
        this(new PredicateDescriptor<>(predicateDescriptor, BasicDataUnitType.createBasic(typeClass)));
    }

    /**
     * Creates a new instance.
     */
    public FilterOperator(PredicateDescriptor<Type> predicateDescriptor) {
        super(DataSetType.createDefault(predicateDescriptor.getInputType()),
                DataSetType.createDefault(predicateDescriptor.getInputType()),
                true,
                null);
        this.predicateDescriptor = predicateDescriptor;
    }

    /**
     * Creates a new instance.
     *
     * @param type type of the dataunit elements
     */
    public FilterOperator(DataSetType<Type> type, PredicateDescriptor.SerializablePredicate<Type> predicateDescriptor) {
        this(new PredicateDescriptor<>(predicateDescriptor, (BasicDataUnitType) type.getDataUnitType()), type);
    }

    /**
     * Creates a new instance.
     *
     * @param type type of the dataunit elements
     */
    public FilterOperator(PredicateDescriptor<Type> predicateDescriptor, DataSetType<Type> type) {
        super(type, type, true, null);
        this.predicateDescriptor = predicateDescriptor;
    }

    public PredicateDescriptor<Type> getPredicateDescriptor() {
        return this.predicateDescriptor;
    }

    @Override
    public Optional<org.qcri.rheem.core.optimizer.cardinality.CardinalityEstimator> getCardinalityEstimator(
            final int outputIndex,
            final Configuration configuration) {
        Validate.inclusiveBetween(0, this.getNumOutputs() - 1, outputIndex);
        return Optional.of(new FilterOperator.CardinalityEstimator());
    }

    public DataSetType getType() {
        return this.getInputType();
    }

    /**
     * Custom {@link org.qcri.rheem.core.optimizer.cardinality.CardinalityEstimator} for {@link FilterOperator}s.
     */
    private class CardinalityEstimator implements org.qcri.rheem.core.optimizer.cardinality.CardinalityEstimator {

        public static final double DEFAULT_SELECTIVITY_CORRECTNESS = 0.9;

        @Override
        public CardinalityEstimate estimate(Configuration configuration, CardinalityEstimate... inputEstimates) {
            Validate.isTrue(inputEstimates.length == FilterOperator.this.getNumInputs());
            final CardinalityEstimate inputEstimate = inputEstimates[0];

            final Optional<Double> selectivity = configuration.getPredicateSelectivityProvider()
                    .optionallyProvideFor(FilterOperator.this.predicateDescriptor);
            if (selectivity.isPresent()) {
                return new CardinalityEstimate(
                        (long) (inputEstimate.getLowerEstimate() * selectivity.get()),
                        (long) (inputEstimate.getUpperEstimate() * selectivity.get()),
                        inputEstimate.getCorrectnessProbability() * DEFAULT_SELECTIVITY_CORRECTNESS
                );
            } else {
                return new CardinalityEstimate(
                        0l,
                        inputEstimate.getUpperEstimate(),
                        inputEstimate.getCorrectnessProbability()
                );
            }
        }
    }
}
