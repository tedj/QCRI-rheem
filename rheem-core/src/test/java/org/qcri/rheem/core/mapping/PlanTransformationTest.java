package org.qcri.rheem.core.mapping;

import org.junit.Assert;
import org.junit.Test;
import org.qcri.rheem.core.mapping.test.TestSinkToTestSink2Factory;
import org.qcri.rheem.core.plan.rheemplan.*;
import org.qcri.rheem.core.plan.rheemplan.test.TestSink;
import org.qcri.rheem.core.plan.rheemplan.test.TestSink2;
import org.qcri.rheem.core.plan.rheemplan.test.TestSource;
import org.qcri.rheem.core.test.TestDataUnit;
import org.qcri.rheem.core.types.DataSetType;

/**
 * Test suite for the {@link org.qcri.rheem.core.mapping.PlanTransformation} class.
 */
public class PlanTransformationTest {

    @Test
    public void testReplace() {
        // Build the plan.
        UnarySource source = new TestSource(DataSetType.createDefault(TestDataUnit.class));
        UnarySink sink = new TestSink(DataSetType.createDefault(TestDataUnit.class));
        source.connectTo(0, sink, 0);
        RheemPlan plan = new RheemPlan();
        plan.addSink(sink);

        // Build the pattern.
        OperatorPattern sinkPattern = new OperatorPattern("sink", new TestSink(DataSetType.createDefault(TestDataUnit.class)), false);
        SubplanPattern subplanPattern = SubplanPattern.createSingleton(sinkPattern);

        // Build the replacement strategy.
        ReplacementSubplanFactory replacementSubplanFactory = new TestSinkToTestSink2Factory();
        PlanTransformation planTransformation = new PlanTransformation(subplanPattern, replacementSubplanFactory).thatReplaces();

        // Apply the replacement strategy to the graph.
        planTransformation.transform(plan, Operator.FIRST_EPOCH + 1);

        // Check the correctness of the transformation.
        Assert.assertEquals(1, plan.getSinks().size());
        final Operator replacedSink = plan.getSinks().iterator().next();
        Assert.assertTrue(replacedSink instanceof TestSink2);
        Assert.assertEquals(source, replacedSink.getInputOperator(0));
    }

    @Test
    public void testIntroduceAlternative() {
        // Build the plan.
        UnarySource source = new TestSource(DataSetType.createDefault(TestDataUnit.class));
        UnarySink sink = new TestSink(DataSetType.createDefault(TestDataUnit.class));
        source.connectTo(0, sink, 0);
        RheemPlan plan = new RheemPlan();
        plan.addSink(sink);

        // Build the pattern.
        OperatorPattern sinkPattern = new OperatorPattern("sink", new TestSink(DataSetType.createDefault(TestDataUnit.class)), false);
        SubplanPattern subplanPattern = SubplanPattern.createSingleton(sinkPattern);

        // Build the replacement strategy.
        ReplacementSubplanFactory replacementSubplanFactory = new TestSinkToTestSink2Factory();
        PlanTransformation planTransformation = new PlanTransformation(subplanPattern, replacementSubplanFactory);

        // Apply the replacement strategy to the graph.
        planTransformation.transform(plan, Operator.FIRST_EPOCH + 1);

        // Check the correctness of the transformation.
        Assert.assertEquals(1, plan.getSinks().size());
        final Operator replacedSink = plan.getSinks().iterator().next();
        Assert.assertTrue(replacedSink instanceof OperatorAlternative);
        OperatorAlternative operatorAlternative = (OperatorAlternative) replacedSink;
        Assert.assertEquals(2, operatorAlternative.getAlternatives().size());
        Assert.assertTrue(operatorAlternative.getAlternatives().get(0).getOperator() instanceof TestSink);
        Assert.assertTrue(operatorAlternative.getAlternatives().get(1).getOperator() instanceof TestSink2);
        Assert.assertEquals(source, replacedSink.getInputOperator(0));
    }

    @Test
    public void testFlatAlternatives() {
        // Build the plan.
        UnarySource source = new TestSource(DataSetType.createDefault(TestDataUnit.class));
        UnarySink sink = new TestSink(DataSetType.createDefault(TestDataUnit.class));
        source.connectTo(0, sink, 0);
        RheemPlan plan = new RheemPlan();
        plan.addSink(sink);

        // Build the pattern.
        OperatorPattern sinkPattern = new OperatorPattern("sink", new TestSink(DataSetType.createDefault(TestDataUnit.class)), false);
        SubplanPattern subplanPattern = SubplanPattern.createSingleton(sinkPattern);

        // Build the replacement strategy.
        ReplacementSubplanFactory replacementSubplanFactory = new TestSinkToTestSink2Factory();
        PlanTransformation planTransformation = new PlanTransformation(subplanPattern, replacementSubplanFactory);

        // Apply the replacement strategy to the graph twice.
        planTransformation.transform(plan, Operator.FIRST_EPOCH + 1);
        planTransformation.transform(plan, Operator.FIRST_EPOCH + 1);

        // Check the correctness of the transformation.
        Assert.assertEquals(1, plan.getSinks().size());
        final Operator replacedSink = plan.getSinks().iterator().next();
        Assert.assertTrue(replacedSink instanceof OperatorAlternative);
        OperatorAlternative operatorAlternative = (OperatorAlternative) replacedSink;
        Assert.assertEquals(3, operatorAlternative.getAlternatives().size());
        Assert.assertTrue(operatorAlternative.getAlternatives().get(0).getOperator() instanceof TestSink);
        Assert.assertTrue(operatorAlternative.getAlternatives().get(1).getOperator() instanceof TestSink2);
        Assert.assertTrue(operatorAlternative.getAlternatives().get(2).getOperator() instanceof TestSink2);
        Assert.assertEquals(source, replacedSink.getInputOperator(0));
    }

}
