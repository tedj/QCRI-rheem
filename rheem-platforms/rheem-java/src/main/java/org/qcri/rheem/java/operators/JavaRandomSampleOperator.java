package org.qcri.rheem.java.operators;

import org.qcri.rheem.basic.operators.SampleOperator;
import org.qcri.rheem.core.api.Configuration;
import org.qcri.rheem.core.optimizer.costs.DefaultLoadEstimator;
import org.qcri.rheem.core.optimizer.costs.LoadEstimator;
import org.qcri.rheem.core.optimizer.costs.LoadProfileEstimator;
import org.qcri.rheem.core.optimizer.costs.NestableLoadProfileEstimator;
import org.qcri.rheem.core.plan.rheemplan.ExecutionOperator;
import org.qcri.rheem.core.platform.ChannelDescriptor;
import org.qcri.rheem.core.platform.ChannelInstance;
import org.qcri.rheem.core.types.DataSetType;
import org.qcri.rheem.java.channels.CollectionChannel;
import org.qcri.rheem.java.channels.JavaChannelInstance;
import org.qcri.rheem.java.channels.StreamChannel;
import org.qcri.rheem.java.compiler.FunctionCompiler;

import java.util.*;
import java.util.function.Predicate;

/**
 * Java implementation of the {@link JavaRandomSampleOperator}. This sampling method is with replacement (i.e., duplicates may appear in the sample).
 */
public class JavaRandomSampleOperator<Type>
        extends SampleOperator<Type>
        implements JavaExecutionOperator {

    Random rand;

    /**
     * Creates a new instance.
     *
     * @param sampleSize size of sample
     */
    public JavaRandomSampleOperator(int sampleSize, DataSetType type) {
        super(sampleSize, type, Methods.RANDOM);
        rand = new Random();
    }

    /**
     * Creates a new instance.
     *
     * @param sampleSize  size of sample
     * @param datasetSize size of data
     */
    public JavaRandomSampleOperator(int sampleSize, long datasetSize, DataSetType type) {
        super(sampleSize, datasetSize, type, Methods.RANDOM);
        rand = new Random();
    }


    @Override
    @SuppressWarnings("unchecked")
    public void evaluate(ChannelInstance[] inputs, ChannelInstance[] outputs, FunctionCompiler compiler) {
        assert inputs.length == this.getNumInputs();
        assert outputs.length == this.getNumOutputs();

        // FIXME: If the dataset size is unknown, we execute the Stream twice, which should not happen.
        if (datasetSize == 0) //total size of input dataset was not given
            datasetSize = ((JavaChannelInstance) inputs[0]).provideStream().count();

        if (sampleSize >= datasetSize) { //return all
            ((StreamChannel.Instance) outputs[0]).accept(((JavaChannelInstance) inputs[0]).provideStream());
            return;
        }

        final int[] sampleIndices = new int[sampleSize];
        final BitSet data = new BitSet();
        for (int i = 0; i < sampleSize; i++) {
            sampleIndices[i] = rand.nextInt((int) datasetSize);
            while (data.get(sampleIndices[i])) //without replacement
                sampleIndices[i] = rand.nextInt((int) datasetSize);
            data.set(sampleIndices[i]);
        }
        Arrays.sort(sampleIndices);

        ((StreamChannel.Instance) outputs[0]).accept(((JavaChannelInstance) inputs[0]).<Type>provideStream().filter(new Predicate<Type>() {
                    int streamIndex = 0;
                    int sampleIndex = 0;

                    @Override
                    public boolean test(Type element) {
                        if (sampleIndex == sampleIndices.length) //we already picked all our samples
                            return false;
                        if (streamIndex == sampleIndices[sampleIndex]) {
                            sampleIndex++;
                            streamIndex++;
                            return true;
                        }
                        streamIndex++;
                        return false;
                    }
                })
        );
    }

    @Override
    public Optional<LoadProfileEstimator> getLoadProfileEstimator(Configuration configuration) {
        return Optional.of(new NestableLoadProfileEstimator(
                new DefaultLoadEstimator(this.getNumInputs(), 1, 0.9d, (inCards, outCards) -> 25 * inCards[0] + 350000),
                LoadEstimator.createFallback(this.getNumInputs(), 1)
        ));
    }

    @Override
    protected ExecutionOperator createCopy() {
        return new JavaRandomSampleOperator<>(this.sampleSize, this.getType());
    }


    @Override
    public List<ChannelDescriptor> getSupportedInputChannels(int index) {
        assert index <= this.getNumInputs() || (index == 0 && this.getNumInputs() == 0);
        return Arrays.asList(CollectionChannel.DESCRIPTOR, StreamChannel.DESCRIPTOR);
    }

    @Override
    public List<ChannelDescriptor> getSupportedOutputChannels(int index) {
        assert index <= this.getNumOutputs() || (index == 0 && this.getNumOutputs() == 0);
        return Collections.singletonList(StreamChannel.DESCRIPTOR);
    }
}
