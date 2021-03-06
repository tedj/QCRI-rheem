package org.qcri.rheem.core.plan.executionplan;

import org.qcri.rheem.core.optimizer.OptimizationContext;
import org.qcri.rheem.core.optimizer.cardinality.CardinalityEstimate;
import org.qcri.rheem.core.plan.rheemplan.*;
import org.qcri.rheem.core.platform.*;
import org.qcri.rheem.core.types.DataSetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Models the data movement between to {@link ExecutionTask}s.
 */
public abstract class Channel {

    protected final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * Was used to set up this instance.
     */
    private final ChannelDescriptor descriptor;

    /**
     * {@link OutputSlot} that creates this instance.
     */
    private final OutputSlot<?> producerSlot;

    /**
     * Produces the data flowing through this instance.
     */
    private ExecutionTask producer;

    /**
     * Consuming {@link ExecutionTask}s of this instance.
     */
    private final List<ExecutionTask> consumers = new LinkedList<>();

    /**
     * Mimed instance. Nullable.
     */
    private final Channel original;

    /**
     * Flag whether this instance should be instrumented to detect its actual cardinality.
     */
    private boolean isMarkedForInstrumentation = false;

    /**
     * Other {@link Channel}s that represent the same {@link OutputSlot}-to-{@link InputSlot} connection from a
     * {@link RheemPlan} and share properties such as {@link #getCardinalityEstimate(OptimizationContext)} and {@link #getDataSetType()}.
     */
    private Set<Channel> siblings = new HashSet<>(2);

    /**
     * This flag indicates whether this instance must not be used to halt at a {@link Breakpoint}.
     */
    private boolean isBreakingProhibited = false;

    /**
     * This flag indicates whether the {@link #producer} and the {@link #consumers} of this instance should never be
     * executed in the same {@link ExecutionStage} instance.
     */
    private boolean isStageExecutionBarrier = false;


    /**
     * Creates a new, non-hierarchical instance and registers it with the given {@link ExecutionTask}.
     *
     * @param descriptor used to create this instance
     */
    protected Channel(ChannelDescriptor descriptor, OutputSlot<?> producerSlot) {
        this.descriptor = descriptor;
        this.original = null;
        this.producerSlot = producerSlot;
    }

    /**
     * Creates a new, hierarchical instance. Mimes the {@code original}'s properties except for the {@link #consumers}.
     *
     * @param original the original instance whose properties will be mimed
     */
    protected Channel(Channel original) {
        this.descriptor = original.getDescriptor();
        this.original = original.getOriginal();
        assert this.original == null || !this.original.isCopy();
        this.producer = original.getProducer();
        this.producerSlot = original.getProducerSlot();
    }

    public static CardinalityEstimate extractCardinalityEstimate(ExecutionTask task, int outputIndex) {
        return task.getOperator().getOutput(outputIndex).getCardinalityEstimate();
    }

    /**
     * Set up a consumer {@link ExecutionTask} for this instance.
     *
     * @param consumer   the new consumer
     * @param inputIndex the input index for this instance into the consumer
     */
    public void addConsumer(ExecutionTask consumer, int inputIndex) {
        if (!this.consumers.contains(consumer)) {
            assert this.isReusable() || this.consumers.isEmpty() :
                    String.format("Cannot add %s as consumer of non-reusable %s, there is already %s.",
                            consumer, this, this.consumers);
            this.consumers.add(consumer);
            consumer.setInputChannel(inputIndex, this);
        }
    }

    /**
     * Declares whether this is not a read-once instance.
     *
     * @return whether this instance can have multiple consumers
     */
    public boolean isReusable() {
        return this.getDescriptor().isReusable();
    }

    /**
     * Declares whether this instance can be shared among two different {@link ExecutionStage}s (of the same
     * {@link PlatformExecution}, though).
     */
    public boolean isInterStageCapable() {
        return this.getDescriptor().isInterStageCapable();
    }

    /**
     * Declares whether this instance can be shared among two different {@link PlatformExecution}s.
     */
    public boolean isInterPlatformCapable() {
        return this.getDescriptor().isInterPlatformCapable();
    }

    /**
     * Tells whether this instance connects different {@link PlatformExecution}s. The answer is not necessarily
     * only determined by whether this instance connects {@link ExecutionTask}s of different {@link Platform}s.
     *
     * @return whether this instance connects different {@link PlatformExecution}s
     */
    public boolean isExecutionBreaker() {
        // Default implementation: Check if all producers/consumers are from the same Platform.
        Platform singlePlatform = null;
        if (this.producer != null) {
            singlePlatform = this.producer.getOperator().getPlatform();
        }

        for (ExecutionTask consumer : this.consumers) {
            if (singlePlatform == null) {
                singlePlatform = consumer.getOperator().getPlatform();
            } else if (!singlePlatform.equals(consumer.getOperator().getPlatform())) {
                return true;
            }
        }

        return false;
    }

    public ExecutionTask getProducer() {
        return this.producer;
    }

    public List<ExecutionTask> getConsumers() {
        return this.consumers;
    }

    public CardinalityEstimate getCardinalityEstimate(OptimizationContext optimizationContext) {
        return this.withSiblings(false)
                .map(sibling -> {
                    final OutputSlot<?> output = sibling.getProducerSlot();
                    if (output == null) return null;
                    final OptimizationContext.OperatorContext operatorCtx =
                            optimizationContext.getOperatorContext(output.getOwner());
                    if (operatorCtx == null) return null;
                    return operatorCtx.getOutputCardinality(output.getIndex());
                }).filter(Objects::nonNull)
                .findAny()
                .orElseThrow(() -> new IllegalStateException(String.format(
                        "No CardinalityEstimate for %s (available: %s).",
                        this, optimizationContext.getLocalOperatorContexts()
                )));
    }

    public boolean isMarkedForInstrumentation() {
        return this.withSiblings(false).anyMatch(sibling -> sibling.isMarkedForInstrumentation);

    }

    /**
     * Creates a {@link Stream} of this instance and its siblings. The sibling relationship must not be altered
     * while processing the {@link Stream}.
     *
     * @return the {@link Stream}
     */
    public Stream<Channel> withSiblings() {
        return this.withSiblings(false);
    }

    /**
     * Creates a {@link Stream} of this instance and its siblings.
     *
     * @param isWithConcurrentModification whether {@link #siblings} may be modificated while processing the {@link Stream}
     * @return the {@link Stream}
     */
    private Stream<Channel> withSiblings(boolean isWithConcurrentModification) {
        return Stream.concat(
                Stream.of(this),
                (isWithConcurrentModification ? new ArrayList<>(this.siblings) : this.siblings).stream()
        );
    }

    public void markForInstrumentation() {
        this.withSiblings(false).forEach(channel -> {
            channel.isMarkedForInstrumentation = true;
            LoggerFactory.getLogger(this.getClass()).debug("Marked {} for instrumentation.", channel);
        });
    }

    @Override
    public String toString() {
        return String.format("%s[%s->%s]",
                this.getClass().getSimpleName(),
                this.getProducer() == null ? this.getProducerSlot() : this.getProducer(),
                this.getConsumers());
    }

    /**
     * Acquaints the given instance with this instance and all existing {@link #siblings}.
     */
    public void addSibling(Channel sibling) {
        if (sibling == this) return;
        this.withSiblings(true).forEach(olderSibling -> olderSibling.relateTo(sibling));
    }

    /**
     * Detaches this instance from all its {@link #siblings}.
     */
    public void removeSiblings() {
        this.removeSiblingsWhere((channel) -> true);
    }

    /**
     * Detaches this instance from all its {@link #siblings}.
     */
    public void removeSiblingsWhere(Predicate<Channel> condition) {
        // Detach with siblings.
        List<Channel> removedSiblings = new LinkedList<>();
        for (Iterator<Channel> i = this.siblings.iterator(); i.hasNext(); ) {
            final Channel sibling = i.next();
            if (condition.test(sibling)) {
                i.remove();
                sibling.siblings.remove(this);
                removedSiblings.add(sibling);
            }
        }

        // Bring the lingering and former siblings into a consistent state.
        for (Channel sibling : this.siblings) {
            sibling.siblings.removeAll(removedSiblings);
        }
        for (Channel removedSibling : removedSiblings) {
            removedSibling.siblings.removeAll(this.siblings);
        }
    }

    /**
     * Makes this and the given instance siblings.
     */
    private void relateTo(Channel sibling) {
        this.siblings.add(sibling);
        sibling.siblings.add(this);
    }

    /**
     * @return all {@link InputSlot}s and {@link OutputSlot}s that are represented by this instance and its
     * {@link #siblings}
     */
    public Collection<Slot<?>> getCorrespondingSlots() {
        return this.withSiblings(false)
                .map(Channel::getCorrespondingSlotsLocal)
                .reduce(Stream.empty(), Stream::concat)
                .collect(Collectors.toList());
    }

    /**
     * Collect the {@link OutputSlot} of the producer and the {@link InputSlot}s of the consumers that are implemented
     * by this instance.
     * @return a {@link Stream} of said {@link Slot}s
     */
    private Stream<Slot<?>> getCorrespondingSlotsLocal() {
        final Stream<? extends OutputSlot<?>> outputSlotStream = streamNullable(this.getProducerSlot());
        final Stream<? extends InputSlot<?>> inputSlotStream
                = this.consumers.stream().flatMap(consumer -> streamNullable(consumer.getInputSlotFor(this)));
        return Stream.concat(inputSlotStream, outputSlotStream);
    }

    /**
     * @return an empty {@link Stream} if given {@code null}, otherwise a singleton {@link Stream}
     */
    private static <T> Stream<T> streamNullable(T nullable) {
        return nullable == null ? Stream.empty() : Stream.of(nullable);
    }

    /**
     * Scrap any consumer {@link ExecutionTask}s and sibling {@link Channel}s that are not within the given
     * {@link ExecutionStage}s.
     */
    public void retain(Set<ExecutionStage> retainableStages) {
        this.consumers.removeIf(consumer -> !retainableStages.contains(consumer.getStage()));
        this.removeSiblingsWhere((sibling) -> !retainableStages.contains(sibling.getProducer().getStage()));
    }

    /**
     * Create a copy of this instance. Mimics everything apart from the consumers. Also delimits already executed
     * {@link ExecutionTask}s and those that are not executed yet. Be careful when revising this invariant.
     */
    public abstract Channel copy();

    /**
     * @return if this is not a copy, then this instance, otherwise the root original instance
     */
    public Channel getOriginal() {
        return this.original == null ? this : this.original;
    }

    /**
     * Tells whether this instance is a copy. If so, it delimits already executed
     * {@link ExecutionTask}s and those that are not executed yet. Be careful when revising this invariant.
     *
     * @see #copy()
     * @see #getOriginal()
     */
    public boolean isCopy() {
        return this.original != null;
    }

    /**
     * Merges this instance into the original instance ({@link #getOriginal()}.
     * <p>The consumers of the original instance are cleared and replaced with the consumers of this instance.
     * For all other properties, the original and this instance should agree.</p>
     */
    public void mergeIntoOriginal() {
        if (!this.isCopy()) return;
        this.getOriginal().copyConsumersFrom(this);
        this.getOriginal().adoptSiblings(this);
    }

    /**
     * Copies the consumers of the given {@code channel} into this instance.
     */
    private void copyConsumersFrom(Channel channel) {
        assert channel.getOriginal() == this;
        for (ExecutionTask consumer : new ArrayList<>(channel.getConsumers())) {
            // We must take care not to copy back channels, that we already have in the original.
            assert this.consumers.stream()
                    .noneMatch(existingConsumer -> existingConsumer.getOperator().equals(consumer.getOperator())) :
                    String.format("Conflict when copying consumers from %s (%s) to %s (%s).",
                            this, this.consumers,
                            channel, channel.getConsumers()
                    );
            consumer.exchangeInputChannel(channel, this);
        }
    }

    /**
     * @return the {@link DataSetType} of this instance (requires a producer)
     * @see #setProducer(ExecutionTask)
     */
    public DataSetType<?> getDataSetType() {
        return this.withSiblings(false)
                .filter(sibling -> sibling.getProducerSlot() != null)
                .findAny()
                .orElseThrow(() -> new IllegalStateException(String.format("No DataSetType for %s.", this)))
                .getProducerSlot().getType();
    }

    /**
     * Copies the siblings of the given {@code channel} into this instance.
     */
    private void adoptSiblings(Channel channel) {
        for (Channel newSibling : channel.siblings) {
            this.addSibling(newSibling);
        }
        channel.removeSiblings();
    }

    void setProducer(ExecutionTask producer) {
        assert this.producerSlot == null || producer.getOperator() == this.producerSlot.getOwner();
        this.producer = producer;
    }

    public ChannelDescriptor getDescriptor() {
        return this.descriptor;
    }

    public OutputSlot<?> getProducerSlot() {
        return this.producerSlot;
    }

    /**
     * Try to obtain the {@link ExecutionOperator} producing this instance, either from a given {@link OutputSlot} or
     * a {@link ExecutionTask} that was specified as producer.
     *
     * @return the {@link ExecutionOperator} or {@code null} if none is set up
     */
    public ExecutionOperator getProducerOperator() {
        if (this.producerSlot != null) {
            return (ExecutionOperator) this.producerSlot.getOwner();
        } else if (this.producer != null) {
            return this.producer.getOperator();
        }
        return null;
    }

    public Set<Channel> getSiblings() {
        return this.siblings;
    }

    /**
     * Create a {@link ChannelInstance} for this instance.
     *
     * @param executor that manages the resource or {@code null} if none
     */
    public abstract ChannelInstance createInstance(Executor executor);

    /**
     * Tests for inter-stage instances.
     *
     * @return whether this instance connects {@link ExecutionTask}s of different {@link ExecutionStage}s.
     */
    public boolean isBetweenStages() {
        if (this.producer == null || this.consumers.isEmpty()) {
            return false;
        }
        final ExecutionStage producerStage = this.producer.getStage();
        if (producerStage == null) {
            return false;
        }
        for (ExecutionTask consumer : this.consumers) {
            if (consumer.getStage() != null && !producerStage.equals(consumer.getStage())) {
                return true;
            }
        }
        return false;
    }

    /**
     * We use this flag to indicate that we cannot re-optimize starting from this instance for various reasons.
     *
     * @return whether halting execution at this instance is prohibited
     */
    public boolean isBreakingProhibited() {
        return this.isBreakingProhibited;
    }

    /**
     * We use this flag to indicate that we cannot re-optimize starting from this instance for various reasons.
     *
     * @param breakingProhibited whether halting execution at this instance is prohibited
     */
    public void setBreakingProhibited(boolean breakingProhibited) {
        this.isBreakingProhibited = breakingProhibited;
    }

    /**
     * This flag indicates whether the {@link #producer} and the {@link #consumers} of this instance should never be
     * executed in the same {@link ExecutionStage} instance.
     *
     * @return the flag value
     */
    public boolean isStageExecutionBarrier() {
        return this.isStageExecutionBarrier;
    }

    /**
     * This flag indicates whether the {@link #producer} and the {@link #consumers} of this instance should never be
     * executed in the same {@link ExecutionStage} instance.
     *
     * @param stageExecutionBarrier the new flag value
     */
    public void setStageExecutionBarrier(boolean stageExecutionBarrier) {
        this.isStageExecutionBarrier = stageExecutionBarrier;
    }
}
