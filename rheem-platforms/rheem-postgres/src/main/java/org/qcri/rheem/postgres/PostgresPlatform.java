package org.qcri.rheem.postgres;

import org.qcri.rheem.core.api.Configuration;
import org.qcri.rheem.core.mapping.Mapping;
import org.qcri.rheem.core.optimizer.channels.ChannelConversionGraph;
import org.qcri.rheem.core.optimizer.costs.LoadProfileToTimeConverter;
import org.qcri.rheem.core.optimizer.costs.LoadToTimeConverter;
import org.qcri.rheem.core.platform.Executor;
import org.qcri.rheem.core.platform.Platform;
import org.qcri.rheem.core.util.ReflectionUtils;
import org.qcri.rheem.postgres.execution.PostgresExecutor;
import org.qcri.rheem.postgres.mapping.PostgresFilterMapping;
import org.qcri.rheem.postgres.mapping.PostgresProjectionMapping;
import org.qcri.rheem.postgres.mapping.PostgresTableSourceMapping;

import java.sql.Connection;
import java.util.Collection;
import java.util.LinkedList;

/**
 * {@link Platform} implementation for the PostgreSQL database.
 */
public class PostgresPlatform extends Platform {


    public static final String CPU_MHZ_PROPERTY = "rheem.postgres.cpu.mhz";

    public static final String CORES_PROPERTY = "rheem.postgres.cores";

    public static final String HDFS_MS_PER_MB_PROPERTY = "rheem.postgres.hdfs.ms-per-mb";

    public static final String JDBC_URL_PROPERTY = "rheem.postgres.jdbc.url";

    public static final String USER_PROPERTY = "rheem.postgres.user";

    public static final String PASSWORD_PROPERTY = "rheem.postgres.password";

    private static final String DEFAULT_CONFIG_FILE = "rheem-postgres-defaults.properties";

    private static final String PLATFORM_NAME = "postgres";

    private final Collection<Mapping> mappings = new LinkedList<>();

    private static PostgresPlatform instance = null;

    public Connection getConnection() {
        return connection;
    }

    private Connection connection = null;


    public static PostgresPlatform getInstance() {
        if (instance == null) {
            instance = new PostgresPlatform();
        }
        return instance;
    }

    private PostgresPlatform() {
        super(PLATFORM_NAME);
        this.initializeMappings();
        this.initializeConfiguration();
    }

    private void initializeConfiguration() {
        final Configuration defaultConfiguration = Configuration.getDefaultConfiguration();
        defaultConfiguration.load(ReflectionUtils.loadResource(DEFAULT_CONFIG_FILE));
    }

    private void initializeMappings() {
        this.mappings.add(new PostgresTableSourceMapping());
        this.mappings.add(new PostgresFilterMapping());
        this.mappings.add(new PostgresProjectionMapping());
    }

    @Override
    public Collection<Mapping> getMappings() {
        return this.mappings;
    }

    @Override
    public boolean isExecutable() {
        return true;
    }

    @Override
    public void addChannelConversionsTo(ChannelConversionGraph channelConversionGraph) {

    }

    @Override
    public Executor.Factory getExecutorFactory() {
        return job -> new PostgresExecutor(this, job);
    }

    @Override
    public LoadProfileToTimeConverter createLoadProfileToTimeConverter(Configuration configuration) {
        int cpuMhz = (int) configuration.getLongProperty(CPU_MHZ_PROPERTY);
        int numCores = (int) configuration.getLongProperty(CORES_PROPERTY);
        double hdfsMsPerMb = configuration.getDoubleProperty(HDFS_MS_PER_MB_PROPERTY);
        return LoadProfileToTimeConverter.createDefault(
                LoadToTimeConverter.createLinearCoverter(1 / (numCores * cpuMhz * 1000)),
                LoadToTimeConverter.createLinearCoverter(hdfsMsPerMb / 1000000),
                LoadToTimeConverter.createLinearCoverter(0),
                (cpuEstimate, diskEstimate, networkEstimate) -> cpuEstimate.plus(diskEstimate).plus(networkEstimate)
        );
    }
}