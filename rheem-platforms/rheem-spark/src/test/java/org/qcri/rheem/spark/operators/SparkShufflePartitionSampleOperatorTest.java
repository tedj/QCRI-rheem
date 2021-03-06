package org.qcri.rheem.spark.operators;

import org.apache.spark.api.java.JavaRDD;
import org.junit.Assert;
import org.junit.Test;
import org.qcri.rheem.core.platform.ChannelInstance;
import org.qcri.rheem.core.types.DataSetType;
import org.qcri.rheem.core.util.RheemCollections;
import org.qcri.rheem.java.channels.CollectionChannel;
import org.qcri.rheem.spark.channels.RddChannel;
import org.qcri.rheem.spark.compiler.FunctionCompiler;

import java.util.Arrays;
import java.util.List;

/**
 * Test suite for {@link SparkShufflePartitionSampleOperator}.
 */
public class SparkShufflePartitionSampleOperatorTest extends SparkOperatorTestBase {

    @Test
    public void testExecution() {
        // Prepare test data.
        RddChannel.Instance input = this.createRddChannelInstance(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        CollectionChannel.Instance output = this.createCollectionChannelInstance();
        int sampleSize = 3;

        // Build the distinct operator.
        SparkShufflePartitionSampleOperator<Integer> sampleOperator =
                new SparkShufflePartitionSampleOperator<>(
                        sampleSize,
                        10,
                        DataSetType.createDefaultUnchecked(Integer.class)
                );

        // Set up the ChannelInstances.
        final ChannelInstance[] inputs = new ChannelInstance[]{input};
        final ChannelInstance[] outputs = new ChannelInstance[]{output};

        // Execute.
        sampleOperator.evaluate(inputs, outputs, new FunctionCompiler(), this.sparkExecutor);

        // Verify the outcome.
        final List<Integer> result = RheemCollections.asList(output.provideCollection());
        System.out.println(result);
        Assert.assertEquals(sampleSize, result.size());

    }

}
