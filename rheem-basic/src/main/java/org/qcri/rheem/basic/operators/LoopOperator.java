package org.qcri.rheem.basic.operators;

import org.qcri.rheem.core.api.Configuration;
import org.qcri.rheem.core.function.PredicateDescriptor;
import org.qcri.rheem.core.optimizer.cardinality.CardinalityEstimator;
import org.qcri.rheem.core.optimizer.cardinality.SwitchForwardCardinalityEstimator;
import org.qcri.rheem.core.plan.rheemplan.*;
import org.qcri.rheem.core.types.BasicDataUnitType;
import org.qcri.rheem.core.types.DataSetType;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

/**
 * This operator has three inputs and three outputs.
 */
public class LoopOperator<InputType, ConvergenceType> extends OperatorBase implements ElementaryOperator, LoopHeadOperator {

    public static final int INITIAL_INPUT_INDEX = 0;
    public static final int INITIAL_CONVERGENCE_INPUT_INDEX = 1;
    public static final int ITERATION_INPUT_INDEX = 2;
    public static final int ITERATION_CONVERGENCE_INPUT_INDEX = 3;

    public static final int ITERATION_OUTPUT_INDEX = 0;
    public static final int ITERATION_CONVERGENCE_OUTPUT_INDEX = 1;
    public static final int FINAL_OUTPUT_INDEX = 2;

    /**
     * Function that this operator applies to the input elements.
     */
    protected final PredicateDescriptor<Collection<ConvergenceType>> criterionDescriptor;

    private State state;

    @Override
    public State getState() {
        return state;
    }

    @Override
    public void setState(State state) {
        this.state = state;
    }

    // TODO: Add convenience constructors as in the other operators.

    public LoopOperator(DataSetType<InputType> inputType, DataSetType<ConvergenceType> convergenceType,
                        PredicateDescriptor.SerializablePredicate<Collection<ConvergenceType>> criterionPredicate) {
        this(inputType, convergenceType,
                new PredicateDescriptor<>(criterionPredicate, (BasicDataUnitType) convergenceType.getDataUnitType()));
    }

    /**
     * Creates a new instance.
     */
    public LoopOperator(DataSetType<InputType> inputType, DataSetType<ConvergenceType> convergenceType,
                        PredicateDescriptor<Collection<ConvergenceType>> criterionDescriptor) {
        super(4, 3, true, null);
        this.criterionDescriptor = criterionDescriptor;
        this.inputSlots[INITIAL_INPUT_INDEX] = new InputSlot<>("initialInput", this, inputType);
        this.inputSlots[INITIAL_CONVERGENCE_INPUT_INDEX] = new InputSlot<>("initialConvergenceInput", this, convergenceType);
        this.inputSlots[ITERATION_INPUT_INDEX] = new InputSlot<>("iterationInput", this, inputType);
        this.inputSlots[ITERATION_CONVERGENCE_INPUT_INDEX] = new InputSlot<>("convergenceInput", this, convergenceType);

        this.outputSlots[ITERATION_OUTPUT_INDEX] = new OutputSlot<>("iterationOutput", this, inputType);
        this.outputSlots[ITERATION_CONVERGENCE_OUTPUT_INDEX] = new OutputSlot<>("convergenceOutput", this, convergenceType);
        this.outputSlots[FINAL_OUTPUT_INDEX] = new OutputSlot<>("output", this, inputType);
        this.state = State.NOT_STARTED;
    }


    public DataSetType<InputType> getInputType() {
        return ((InputSlot<InputType>) this.getInput(INITIAL_INPUT_INDEX)).getType();
    }

    public DataSetType<ConvergenceType> getConvergenceType() {
        return ((InputSlot<ConvergenceType>) this.getInput(INITIAL_CONVERGENCE_INPUT_INDEX)).getType();
    }

    public void initialize(Operator initOperator, int initOpOutputIndex, Operator convOperator, int convOpOutputIndex) {
        initOperator.connectTo(initOpOutputIndex, this, INITIAL_INPUT_INDEX);
        convOperator.connectTo(convOpOutputIndex, this, INITIAL_CONVERGENCE_INPUT_INDEX);
    }

    public void initialize(Operator initOperator, Operator convOperator) {
        this.initialize(initOperator, 0, convOperator, 0);
    }

    public void beginIteration(Operator beginOperator, int beginInputIndex, Operator convergeOperator,
                               int convergeInputIndex) {
        this.connectTo(ITERATION_OUTPUT_INDEX, beginOperator, beginInputIndex);
        this.connectTo(ITERATION_CONVERGENCE_OUTPUT_INDEX, convergeOperator, convergeInputIndex);
    }

    public void beginIteration(Operator beginOperator, Operator convergeOperator) {
        this.beginIteration(beginOperator, 0, convergeOperator, 0);
    }

    public void endIteration(Operator endOperator, int endOpOutputIndex, Operator convergeOperator,
                             int convergeOutputIndex) {
        endOperator.connectTo(endOpOutputIndex, this, ITERATION_INPUT_INDEX);
        convergeOperator.connectTo(convergeOutputIndex, this, ITERATION_CONVERGENCE_INPUT_INDEX);
    }

    public void endIteration(Operator endOperator, Operator convergeOperator) {
        this.endIteration(endOperator, 0, convergeOperator, 0);
    }

    public void outputConnectTo(Operator outputOperator, int thatInputIndex) {
        this.connectTo(FINAL_OUTPUT_INDEX, outputOperator, thatInputIndex);
    }

    public void outputConnectTo(Operator outputOperator) {
        this.outputConnectTo(outputOperator, 0);
    }

    public PredicateDescriptor<Collection<ConvergenceType>> getCriterionDescriptor() {
        return this.criterionDescriptor;
    }

    @Override
    public Collection<OutputSlot<?>> getForwards(InputSlot<?> input) {
        assert this.isOwnerOf(input);
        switch (input.getIndex()) {
            case INITIAL_CONVERGENCE_INPUT_INDEX:
            case ITERATION_CONVERGENCE_INPUT_INDEX:
                return Collections.singleton(this.getOutput(ITERATION_CONVERGENCE_OUTPUT_INDEX));
            case INITIAL_INPUT_INDEX:
            case ITERATION_INPUT_INDEX:
                return Arrays.asList(this.getOutput(ITERATION_OUTPUT_INDEX), this.getOutput(FINAL_OUTPUT_INDEX));
            default:
                return super.getForwards(input);
        }
    }

    @Override
    public boolean isReading(InputSlot<?> input) {
        assert this.isOwnerOf(input);
        switch (input.getIndex()) {
            case INITIAL_CONVERGENCE_INPUT_INDEX:
            case ITERATION_CONVERGENCE_INPUT_INDEX:
            case INITIAL_INPUT_INDEX:
            case ITERATION_INPUT_INDEX:
                return true;
            default:
                return super.isReading(input);
        }
    }

    @Override
    public Optional<CardinalityEstimator> getCardinalityEstimator(int outputIndex, Configuration configuration) {
        switch (outputIndex) {
            case ITERATION_CONVERGENCE_OUTPUT_INDEX:
                return Optional.of(new SwitchForwardCardinalityEstimator(
                        INITIAL_CONVERGENCE_INPUT_INDEX,
                        ITERATION_CONVERGENCE_INPUT_INDEX
                ));
            case ITERATION_OUTPUT_INDEX:
            case FINAL_OUTPUT_INDEX:
                return Optional.of(new SwitchForwardCardinalityEstimator(INITIAL_INPUT_INDEX, ITERATION_INPUT_INDEX));
            default:
                throw new IllegalArgumentException("Illegal output index " + outputIndex + ".");
        }
    }

    @Override
    public Collection<OutputSlot<?>> getLoopBodyOutputs() {
        return Arrays.asList(this.getOutput(ITERATION_OUTPUT_INDEX), this.getOutput(ITERATION_CONVERGENCE_OUTPUT_INDEX));
    }

    @Override
    public Collection<OutputSlot<?>> getFinalLoopOutputs() {
        return Collections.singletonList(this.getOutput(FINAL_OUTPUT_INDEX));
    }

    @Override
    public Collection<InputSlot<?>> getLoopBodyInputs() {
        return Arrays.asList(this.getInput(ITERATION_INPUT_INDEX), this.getInput(ITERATION_CONVERGENCE_INPUT_INDEX));
    }

    @Override
    public Collection<InputSlot<?>> getLoopInitializationInputs() {
        return Arrays.asList(
                this.getInput(INITIAL_INPUT_INDEX),
                this.getInput(INITIAL_CONVERGENCE_INPUT_INDEX)
        );
    }

    @Override
    public int getNumExpectedIterations() {
        return 100;
    }

}