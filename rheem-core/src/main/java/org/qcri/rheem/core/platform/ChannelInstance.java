package org.qcri.rheem.core.platform;

import org.qcri.rheem.core.plan.executionplan.Channel;

import java.util.OptionalLong;

/**
 * Represents the actual, allocated resource represented by {@link Channel}.
 */
public interface ChannelInstance extends ExecutionResource {

    /**
     * @return the {@link Channel} that is implemented by this instance
     */
    Channel getChannel();

    /**
     * Optionally provides the measured cardinality of this instance. However, such a cardinality might not be available
     * for several reasons. For instance, the measurement might not have been requested or could not be implemented
     * by the executing {@link Platform}.
     *
     * @return the measured cardinality if available
     */
    OptionalLong getMeasuredCardinality();

    /**
     * Register the measured cardinality with this instance.
     */
    void setMeasuredCardinality(long cardinality);

    /**
     * Tells whether this instance should be instrumented
     */
    default boolean isMarkedForInstrumentation() {
        return this.getChannel().isMarkedForInstrumentation();
    }

}
