package org.qcri.rheem.core.plan.rheemplan;

import org.qcri.rheem.core.types.DataSetType;

/**
 * This operator has two inputs and a single output.
 */
public abstract class BinaryToUnaryOperator<InputType0, InputType1, OutputType> extends OperatorBase implements ElementaryOperator {

    /**
     * Creates a new instance.
     */
    public BinaryToUnaryOperator(DataSetType<InputType0> inputType0, DataSetType<InputType1> inputType1,
                                 DataSetType<OutputType> outputType, boolean isSupportingBroadcastInputs) {
        super(2, 1, isSupportingBroadcastInputs, null);
        this.inputSlots[0] = new InputSlot<>("input0", this, inputType0);
        this.inputSlots[1] = new InputSlot<>("input1", this, inputType1);
        this.outputSlots[0] = new OutputSlot<>("output", this, outputType);
    }


    public DataSetType<InputType0> getInputType0() {
        return ((InputSlot<InputType0>) this.getInput(0)).getType();
    }

    public DataSetType<InputType1> getInputType1() {
        return ((InputSlot<InputType1>) this.getInput(1)).getType();
    }


    public DataSetType<OutputType> getOutputType() {
        return ((OutputSlot<OutputType>) this.getOutput(0)).getType();
    }
}
