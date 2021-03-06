package org.qcri.rheem.core.plan.rheemplan;

import org.qcri.rheem.core.api.Configuration;
import org.qcri.rheem.core.optimizer.costs.LoadProfile;
import org.qcri.rheem.core.optimizer.costs.LoadProfileEstimator;
import org.qcri.rheem.core.plan.executionplan.Channel;
import org.qcri.rheem.core.platform.ChannelDescriptor;
import org.qcri.rheem.core.platform.Platform;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * An execution operator is handled by a certain platform.
 */
public interface ExecutionOperator extends ElementaryOperator {

    /**
     * @return the platform that can run this operator
     */
    Platform getPlatform();

    /**
     * @return a copy of this instance; it's {@link Slot}s will not be connected
     */
    ExecutionOperator copy();

    /**
     * @return this instance or, if it was derived via {@link #copy()}, the original instance
     */
    ExecutionOperator getOriginal();

    /**
     * Developers of {@link ExecutionOperator}s can provide a default {@link LoadProfileEstimator} via this method.
     *
     * @param configuration in which the {@link LoadProfile} should be estimated.
     * @return an {@link Optional} that might contain the {@link LoadProfileEstimator} (but {@link Optional#empty()}
     * by default)
     */
    default Optional<LoadProfileEstimator> getLoadProfileEstimator(Configuration configuration) {
        return Optional.empty();
    }

    /**
     * Display the supported {@link Channel}s for a certain {@link InputSlot}.
     *
     * @param index the index of the {@link InputSlot}
     * @return an {@link List} of {@link Channel}s' {@link Class}es, ordered by their preference of use
     */
    List<ChannelDescriptor> getSupportedInputChannels(int index);

    /**
     * Display the supported {@link Channel}s for a certain {@link OutputSlot}.
     *
     * @param index the index of the {@link OutputSlot}
     * @return an {@link List} of {@link Channel}s' {@link Class}es, ordered by their preference of use
     * @see #getOutputChannelDescriptor(int)
     * @deprecated {@link ExecutionOperator}s should only support a single {@link ChannelDescriptor}
     */
    @Deprecated
    List<ChannelDescriptor> getSupportedOutputChannels(int index);

    /**
     * Display the {@link Channel} used to implement a certain {@link OutputSlot}.
     *
     * @param index index of the {@link OutputSlot}
     * @return the {@link ChannelDescriptor} for the mentioned {@link Channel}
     */
    default ChannelDescriptor getOutputChannelDescriptor(int index) {
        final List<ChannelDescriptor> supportedOutputChannels = this.getSupportedOutputChannels(index);
        assert !supportedOutputChannels.isEmpty() : String.format("No supported output channels for %s.", this);
        if (supportedOutputChannels.size() > 1) {
            LoggerFactory.getLogger(this.getClass()).warn("Treat {} as the only supported channel for {}.",
                    supportedOutputChannels.get(0), this.getOutput(index)
            );
        }
        return supportedOutputChannels.get(0);
    }

}
