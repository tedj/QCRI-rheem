package org.qcri.rheem.spark.mapping;

import org.qcri.rheem.basic.operators.DoWhileOperator;
import org.qcri.rheem.basic.operators.LoopOperator;
import org.qcri.rheem.core.function.PredicateDescriptor;
import org.qcri.rheem.core.mapping.*;
import org.qcri.rheem.spark.operators.SparkDoWhileOperator;
import org.qcri.rheem.spark.operators.SparkLoopOperator;
import org.qcri.rheem.spark.platform.SparkPlatform;

import java.util.Collection;
import java.util.Collections;

/**
 * Mapping from {@link LoopOperator} to {@link SparkLoopOperator}.
 */
@SuppressWarnings("unchecked")
public class DoWhileMapping implements Mapping {

    @Override
    public Collection<PlanTransformation> getTransformations() {
        return Collections.singleton(
                new PlanTransformation(
                        this.createSubplanPattern(),
                        this.createReplacementSubplanFactory(),
                        SparkPlatform.getInstance()
                )
        );
    }

    private SubplanPattern createSubplanPattern() {
        final OperatorPattern operatorPattern = new OperatorPattern(
                "loop", new DoWhileOperator<>(null, null, (PredicateDescriptor) null), false);
        return SubplanPattern.createSingleton(operatorPattern);
    }

    private ReplacementSubplanFactory createReplacementSubplanFactory() {
        return new ReplacementSubplanFactory.OfSingleOperators<DoWhileOperator>(
                (matchedOperator, epoch) -> new SparkDoWhileOperator<>(
                        matchedOperator.getInputType(),
                        matchedOperator.getConvergenceType(),
                        matchedOperator.getCriterionDescriptor()
                ).at(epoch)
        );
    }
}
