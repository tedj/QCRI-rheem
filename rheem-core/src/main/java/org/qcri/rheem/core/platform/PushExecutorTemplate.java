package org.qcri.rheem.core.platform;

import org.qcri.rheem.core.api.Job;
import org.qcri.rheem.core.plan.executionplan.Channel;
import org.qcri.rheem.core.plan.executionplan.ExecutionStage;
import org.qcri.rheem.core.plan.executionplan.ExecutionTask;
import org.qcri.rheem.core.plan.rheemplan.ExecutionOperator;
import org.qcri.rheem.core.plan.rheemplan.InputSlot;
import org.qcri.rheem.core.plan.rheemplan.LoopHeadOperator;
import org.qcri.rheem.core.util.OneTimeExecutable;
import org.qcri.rheem.core.util.RheemCollections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * {@link Executor} implementation that employs a push model, i.e., data quanta are "pushed"
 * through the {@link ExecutionStage}.
 */
public abstract class PushExecutorTemplate extends ExecutorTemplate {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    protected final Job job;

    public PushExecutorTemplate(Job job) {
        super(job == null ? null : job.getCrossPlatformExecutor());
        this.job = job;
    }

    @Override
    public void execute(ExecutionStage stage, ExecutionState executionState) {
        assert !this.isDisposed() : String.format("%s has been disposed.", this);

        final StageExecution stageExecution = new StageExecution(stage, executionState);
        stageExecution.executeStage();
    }


    /**
     * Executes an {@link ExecutionTask}.
     *
     * @param taskActivator    provides the {@link ExecutionTask} and its input dependenchannelInstancees.
     * @param isForceExecution whether execution is forced, i.e., lazy execution of {@link ExecutionTask} is prohibited
     * @return the output {@link ChannelInstance}s of the {@link ExecutionTask}
     */
    private Collection<ChannelInstance> execute(TaskActivator taskActivator, boolean isForceExecution) {
        // Execute the ExecutionTask.
        this.open(taskActivator.getTask(), taskActivator.getInputChannelInstances());

        return this.execute(
                taskActivator.getTask(),
                taskActivator.getInputChannelInstances(),
                isForceExecution
        );
    }

    /**
     * Prepares the given {@code task} for execution.
     *
     * @param task                  that should be executed
     * @param inputChannelInstances inputs into the {@code task}
     * @return the {@link ChannelInstance}s created as output of {@code task}
     */
    protected abstract void open(ExecutionTask task, List<ChannelInstance> inputChannelInstances);

    /**
     * Executes the given {@code task} and return the output {@link ChannelInstance}s.
     *
     * @param task                  that should be executed
     * @param inputChannelInstances inputs into the {@code task}
     * @param isForceExecution      forbids lazy execution
     * @return the {@link ChannelInstance}s created as output of {@code task}
     */
    protected abstract List<ChannelInstance> execute(ExecutionTask task, List<ChannelInstance> inputChannelInstances, boolean isForceExecution);

    /**
     * Keeps track of state that is required within the execution of a single {@link ExecutionStage}. SpechannelInstancefically,
     * it issues to the {@link PushExecutorTemplate}, which {@link ExecutionTask}s should be executed in which
     * order with which input dependenchannelInstancees.
     */
    protected class StageExecution extends OneTimeExecutable {

        private final Map<ExecutionTask, TaskActivator> stagedActivators = new HashMap<>();

        private final Queue<TaskActivator> readyActivators = new LinkedList<>();

        private final Set<ExecutionTask> terminalTasks;

        private final Collection<ChannelInstance> allChannelInstances = new LinkedList<>();

        /**
         * State from preceeding executions.
         */
        private final ExecutionState executionState;

        private StageExecution(ExecutionStage stage, ExecutionState executionState) {
            this.executionState = executionState;

            // Initialize the readyActivators.
            assert !stage.getStartTasks().isEmpty() : String.format("No start tasks for {}.", stage);
            stage.getStartTasks().forEach(this::scheduleStartTask);

            // Initialize the terminalTasks.
            this.terminalTasks = RheemCollections.asSet(stage.getTerminalTasks());
        }

        private void scheduleStartTask(ExecutionTask startTask) {
            TaskActivator activator = new TaskActivator(startTask, this.executionState);
            assert activator.isReady() : String.format("Stage starter %s is not immediately ready.", startTask);
            this.readyActivators.add(activator);
        }

        /**
         * Executes the {@link ExecutionStage} and contributes results to the {@link #executionState}.
         */
        void executeStage() {
            this.execute();
            this.updateExecutionState();
        }

        @Override
        protected void doExecute() {
            TaskActivator readyActivator;
            while ((readyActivator = this.readyActivators.poll()) != null) {
                // Execute the ExecutionTask.
                final ExecutionTask task = readyActivator.getTask();
                final Collection<ChannelInstance> outputChannelInstances = this.execute(readyActivator, task);
                readyActivator.dispose();

                // Register the outputChannelInstances (to obtain cardinality measurements and for furhter stages).
                outputChannelInstances.stream().filter(Objects::nonNull).forEach(this::store);

                // Activate successor ExecutionTasks.
                this.activateSuccessorTasks(task, outputChannelInstances);
            }
        }

        /**
         * Puts an {@link ExecutionTask} into execution.
         *
         * @param readyActivator activated the {@code task}
         * @param task           should be executed
         * @return the {@link ChannelInstance}s created by the {@code task}
         */
        private Collection<ChannelInstance> execute(TaskActivator readyActivator, ExecutionTask task) {
            final boolean isForceExecution = this.terminalTasks.contains(task);
            return this.executor().execute(readyActivator, isForceExecution);
        }

        /**
         * Stores the {@link ChannelInstance}.
         *
         * @param channelInstance should be stored
         */
        private void store(ChannelInstance channelInstance) {
            this.allChannelInstances.add(channelInstance);
            channelInstance.noteObtainedReference();
        }

        private void activateSuccessorTasks(ExecutionTask task, Collection<ChannelInstance> outputChannelInstances) {
            for (ChannelInstance outputChannelInstance : outputChannelInstances) {
                if (outputChannelInstance == null) continue; // Partial results possible (cf. LoopHeadOperator).

                final Channel channel = outputChannelInstance.getChannel();
                for (ExecutionTask consumer : channel.getConsumers()) {
                    // Stay within ExecutionStage.
                    if (consumer.getStage() != task.getStage() || channel.isStageExecutionBarrier()) continue;

                    // Get or create the TaskActivator.
                    final TaskActivator consumerActivator = this.stagedActivators.computeIfAbsent(
                            consumer, (task1) -> new TaskActivator(task1, this.executionState)
                    );

                    // Register the outputChannelInstance.
                    consumerActivator.accept(outputChannelInstance);

                    // Schedule the consumerActivator if it isReady.
                    if (consumerActivator.isReady()) {
                        this.stagedActivators.remove(consumer);
                        this.readyActivators.add(consumerActivator);
                    }
                }
            }
        }

        private PushExecutorTemplate executor() {
            return PushExecutorTemplate.this;
        }

        /**
         * Put new {@link ChannelInstance}s to the {@link #executionState} and release input {@link ChannelInstance}s.
         */
        private void updateExecutionState() {
            for (final ChannelInstance channelInstance : this.allChannelInstances) {
                // Capture outbound ChannelInstances.
                if (channelInstance.getChannel().isBetweenStages() || channelInstance.getChannel().isStageExecutionBarrier()) {
                    this.executionState.register(channelInstance);
                }

                // Try to store cardinalities.
                PushExecutorTemplate.this.addCardinalityIfNotInLoop(channelInstance);

                // Release the ChannelInstance.
                channelInstance.noteDiscardedReference(true);
            }
            this.allChannelInstances.clear();
        }
    }

    /**
     * Wraps an {@link ExecutionTask} and collects its input dependencies (i.e., {@link ChannelInstance}s). Then,
     * allows for execution of the {@link ExecutionTask}.
     */
    private class TaskActivator {

        private final ExecutionTask task;

        private final ArrayList<ChannelInstance> inputChannelInstances;

        TaskActivator(ExecutionTask task, ExecutionState executionState) {
            this.task = task;
            this.inputChannelInstances = RheemCollections.createNullFilledArrayList(this.getOperator().getNumInputs());
            this.acceptFrom(executionState);
        }

        /**
         * Accept input {@link ChannelInstance}s from the given {@link ExecutionState}.
         *
         * @param executionState provides {@link ChannelInstance}s
         */
        private void acceptFrom(ExecutionState executionState) {
            for (int inputIndex = 0; inputIndex < this.task.getNumInputChannels(); inputIndex++) {
                final Channel channel = this.task.getInputChannel(inputIndex);
                final ChannelInstance channelInstance = executionState.getChannelInstance(channel);
                if (channelInstance != null) {
                    this.accept(channelInstance);
                }
            }
        }

        /**
         * Registers the {@code inputChannelInstance} as input of the wrapped {@link #task}.
         *
         * @param inputChannelInstance the input {@link ChannelInstance}
         */
        public void accept(ChannelInstance inputChannelInstance) {
            // Identify the input index of the inputChannelInstance wrt. the ExecutionTask.
            final Channel channel = inputChannelInstance.getChannel();
            final int inputIndex;
            if (this.task.getOperator().getNumInputs() == 0) {
                // If we have a ChannelInstance but no InputSlots, we fall-back to index 0 as per convention.
                assert this.inputChannelInstances.isEmpty();
                this.inputChannelInstances.add(null);
                inputIndex = 0;
            } else {
                final InputSlot<?> inputSlot = this.task.getInputSlotFor(channel);
                assert inputSlot != null
                        : String.format("Could not identify an InputSlot in %s for %s.", this.task, inputChannelInstance);
                inputIndex = inputSlot.getIndex();

            }
            // Register the inputChannelInstance (and check for conflicts).
            assert this.inputChannelInstances.get(inputIndex) == null
                    : String.format("Tried to replace %s with %s as %dth input of %s.",
                    this.inputChannelInstances.get(inputIndex), inputChannelInstance, inputIndex, this.task);
            this.inputChannelInstances.set(inputIndex, inputChannelInstance);
            inputChannelInstance.noteObtainedReference();

        }

        public ExecutionTask getTask() {
            return this.task;
        }

        protected ExecutionOperator getOperator() {
            return this.task.getOperator();
        }

        protected List<ChannelInstance> getInputChannelInstances() {
            return this.inputChannelInstances;
        }

        /**
         * Disposes this instance.
         */
        protected void dispose() {
            // Drop references towards the #inputChannelInstances.
            for (ChannelInstance inputChannelInstance : this.inputChannelInstances) {
                if (inputChannelInstance != null) {
                    inputChannelInstance.noteDiscardedReference(true);
                }
            }
        }

        /**
         * @return whether the {@link #task} has suffichannelInstanceent input dependenchannelInstancees satisfied
         */
        protected boolean isReady() {
            return this.isLoopInitializationReady() || this.isLoopIterationReady() || this.isPlainReady();
        }

        /**
         * @return whether the {@link #task} has all input dependenchannelInstancees satisfied
         */
        protected boolean isPlainReady() {
            for (InputSlot<?> inputSlot : this.getOperator().getAllInputs()) {
                if (this.inputChannelInstances.get(inputSlot.getIndex()) == null) return false;
            }
            return true;
        }

        /**
         * @return whether the {@link #task} has all loop initialization input dependenchannelInstancees satisfied
         */
        protected boolean isLoopInitializationReady() {
            if (!this.getOperator().isLoopHead()) return false;
            final LoopHeadOperator loopHead = (LoopHeadOperator) this.getOperator();
            if (loopHead.getState() != LoopHeadOperator.State.NOT_STARTED) return false;
            for (InputSlot<?> inputSlot : loopHead.getLoopInitializationInputs()) {
                if (this.inputChannelInstances.get(inputSlot.getIndex()) == null) return false;
            }
            return true;
        }

        /**
         * @return whether the {@link #task} has all loop iteration input dependenchannelInstancees satisfied
         */
        protected boolean isLoopIterationReady() {
            if (!this.getOperator().isLoopHead()) return false;
            final LoopHeadOperator loopHead = (LoopHeadOperator) this.getOperator();
            if (loopHead.getState() != LoopHeadOperator.State.RUNNING) return false;
            for (InputSlot<?> inputSlot : loopHead.getLoopBodyInputs()) {
                if (this.inputChannelInstances.get(inputSlot.getIndex()) == null) return false;
            }
            return true;
        }
    }
}
