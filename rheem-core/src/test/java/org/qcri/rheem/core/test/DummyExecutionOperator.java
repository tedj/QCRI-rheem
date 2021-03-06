package org.qcri.rheem.core.test;

import org.qcri.rheem.core.plan.rheemplan.*;
import org.qcri.rheem.core.platform.ChannelDescriptor;
import org.qcri.rheem.core.platform.Platform;
import org.qcri.rheem.core.types.DataSetType;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * TODO
 */
public class DummyExecutionOperator extends OperatorBase implements ExecutionOperator {

    public List<List<ChannelDescriptor>> supportedInputChannelDescriptors = new ArrayList<>();

    public List<List<ChannelDescriptor>> supportedOutputChannelDescriptors = new ArrayList<>();

    public DummyExecutionOperator(int numInputs, int numOutputs, boolean isSupportingBroadcastInputs) {
        super(numInputs, numOutputs, isSupportingBroadcastInputs, null);
        for (int inputIndex = 0; inputIndex < numInputs; inputIndex++) {
            this.inputSlots[inputIndex] = new InputSlot<Object>(String.format("input%d", inputIndex), this, DataSetType.createDefault(Integer.class));
            supportedInputChannelDescriptors.add(new LinkedList<>());
        }
        for (int outputIndex = 0; outputIndex < numOutputs; outputIndex++) {
            this.outputSlots[outputIndex] = new OutputSlot<Object>(String.format("output%d", outputIndex), this, DataSetType.createDefault(Integer.class));
            supportedOutputChannelDescriptors.add(new LinkedList<>());
        }
    }

    @Override
    public Platform getPlatform() {
        return DummyPlatform.getInstance();
    }

    @Override
    public List<ChannelDescriptor> getSupportedInputChannels(int index) {
        return this.supportedInputChannelDescriptors.get(index);
    }

    @Override
    public List<ChannelDescriptor> getSupportedOutputChannels(int index) {
        return this.supportedOutputChannelDescriptors.get(index);
    }
}
