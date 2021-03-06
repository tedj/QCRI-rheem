package org.qcri.rheem.java.mapping;

import org.qcri.rheem.basic.operators.ReduceByOperator;
import org.qcri.rheem.core.mapping.*;
import org.qcri.rheem.java.operators.JavaReduceByOperator;
import org.qcri.rheem.java.JavaPlatform;

import java.util.Collection;
import java.util.Collections;

/**
 * Mapping from {@link ReduceByOperator} to {@link JavaReduceByOperator}.
 */
@SuppressWarnings("unchecked")
public class ReduceByOperatorToJavaReduceByOperatorMapping implements Mapping {

    @Override
    public Collection<PlanTransformation> getTransformations() {
        return Collections.singleton(
                new PlanTransformation(
                        this.createSubplanPattern(),
                        this.createReplacementSubplanFactory(),
                        JavaPlatform.getInstance()
                )
        );
    }

    private SubplanPattern createSubplanPattern() {
        final OperatorPattern operatorPattern = new OperatorPattern(
                "reduceBy", new ReduceByOperator<>(null, null, null), false);
        return SubplanPattern.createSingleton(operatorPattern);
    }

    private ReplacementSubplanFactory createReplacementSubplanFactory() {
        return new ReplacementSubplanFactory.OfSingleOperators<ReduceByOperator>(
                (matchedOperator, epoch) -> new JavaReduceByOperator<>(
                        matchedOperator.getType(),
                        matchedOperator.getKeyDescriptor(),
                        matchedOperator.getReduceDescriptor()
                ).at(epoch)
        );
    }
}
