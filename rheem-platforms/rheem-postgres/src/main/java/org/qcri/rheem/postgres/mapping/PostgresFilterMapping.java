package org.qcri.rheem.postgres.mapping;

import org.qcri.rheem.basic.operators.FilterOperator;
import org.qcri.rheem.core.function.PredicateDescriptor;
import org.qcri.rheem.core.mapping.*;
import org.qcri.rheem.postgres.PostgresPlatform;
import org.qcri.rheem.postgres.operators.PostgresFilterOperator;

import java.util.Collection;
import java.util.Collections;


/**
 * Mapping from {@link FilterOperator} to {@link PostgresFilterOperator}.
 */
@SuppressWarnings("unchecked")
public class PostgresFilterMapping implements Mapping {

    @Override
    public Collection<PlanTransformation> getTransformations() {
        return Collections.singleton(
                new PlanTransformation(
                        this.createSubplanPattern(),
                        this.createReplacementSubplanFactory(),
                        PostgresPlatform.getInstance()
                )
        );
    }

    private SubplanPattern createSubplanPattern() {
        final OperatorPattern operatorPattern = new OperatorPattern(
                "filter", new FilterOperator<>((PredicateDescriptor) null, null), false);
        return SubplanPattern.createSingleton(operatorPattern);
    }

    private ReplacementSubplanFactory createReplacementSubplanFactory() {
        return new ReplacementSubplanFactory.OfSingleOperators<FilterOperator>(
                (matchedOperator, epoch) -> new PostgresFilterOperator<>(
                        matchedOperator.getType(),
                        matchedOperator.getPredicateDescriptor()
                ).at(epoch)
        );
    }
}
