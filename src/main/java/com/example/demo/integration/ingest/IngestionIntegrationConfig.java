package com.example.demo.integration.ingest;

import com.amazonaws.services.s3.AmazonS3;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.annotation.*;
import org.springframework.integration.aws.inbound.S3InboundFileSynchronizer;
import org.springframework.integration.aws.inbound.S3InboundFileSynchronizingMessageSource;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.file.filters.AcceptOnceFileListFilter;
import org.springframework.messaging.PollableChannel;

import java.io.File;
import java.util.Date;

@Configuration
@Slf4j
@Profile("ingestion")
public class IngestionIntegrationConfig {

    private static final String FILE = "file",
            EXECUTION_TIME = "execution_time";

    private final AmazonS3 amazonS3;
    private final JobLauncher jobLauncher;
    private final Job job;

    public IngestionIntegrationConfig(AmazonS3 amazonS3, JobLauncher jobLauncher, @Qualifier("ingestJob") Job job) {
        this.amazonS3 = amazonS3;
        this.jobLauncher = jobLauncher;
        this.job = job;
    }

    @Bean(name = "s3InputChannel")
    public PollableChannel s3InputChannel() {
        return new QueueChannel();
    }

    @Bean(name = "outputInboundFileSynchronizer")
    public S3InboundFileSynchronizer inboundFileSynchronizer() {
        S3InboundFileSynchronizer synchronizer = new S3InboundFileSynchronizer(amazonS3);
        synchronizer.setDeleteRemoteFiles(true);
        synchronizer.setPreserveTimestamp(true);
        synchronizer.setRemoteDirectory("output");
        return synchronizer;
    }

    @Bean
    @InboundChannelAdapter(value="s3InputChannel", poller = @Poller)
    public S3InboundFileSynchronizingMessageSource s3MessageSource(@Qualifier("outputInboundFileSynchronizer") S3InboundFileSynchronizer synchronizer) {
        S3InboundFileSynchronizingMessageSource messageSource = new S3InboundFileSynchronizingMessageSource(synchronizer);
        messageSource.setAutoCreateLocalDirectory(true);
        messageSource.setLocalDirectory(new File("output"));
        messageSource.setLocalFilter(new AcceptOnceFileListFilter<>());
        return messageSource;
    }

    @Transformer(inputChannel = "s3InputChannel", outputChannel = "launchJobChannel")
    public JobParameters fileToJobParametersTransformer(File file) {
        log.info("Building job parameters for file [{}]", file);
        return new JobParametersBuilder()
                .addString(FILE, file.getAbsolutePath())
                .addDate(EXECUTION_TIME, new Date())
                .toJobParameters();
    }

    @ServiceActivator(inputChannel = "launchJobChannel", outputChannel = "deleteLocalFileChannel")
    public File launchExtractJob(JobParameters jobParameters) throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException {
        log.info("Launching ingestJob with parameters [{}]", jobParameters);
        JobExecution jobExecution = jobLauncher.run(job, jobParameters);
        log.info("Job execution [{}] ended at [{}] with status [{}] for parameters [{}]",
                jobExecution.getId(), jobExecution.getEndTime(), jobExecution.getStatus(), jobExecution.getJobParameters());
        return new File(jobParameters.getString(FILE));
    }

    @ServiceActivator(inputChannel = "deleteLocalFileChannel", outputChannel = "endChannel")
    public Boolean deleteLocalFile(File file) {
        log.info("Deleting file [{}]", file.getName());
        return file.delete();
    }

    @Aggregator(inputChannel = "endChannel")
    public void shutdown() {
        log.info("Integration flow complete, shutting down JVM");
        System.exit(0); // kills the JVM when all integration steps are finished, this allows single run scheduling
    }

}
