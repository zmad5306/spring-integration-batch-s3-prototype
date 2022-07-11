package com.example.demo.integration.ingest;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
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
import org.springframework.integration.channel.QueueChannel;
import org.springframework.integration.core.MessageSource;
import org.springframework.messaging.PollableChannel;
import org.springframework.messaging.support.GenericMessage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.List;

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
    public MessageSource<List<String>> s3MessageSource(@Qualifier("outputInboundFileSynchronizer") S3InboundFileSynchronizer synchronizer) {
        return () -> {
            ObjectListing listing = amazonS3.listObjects("output");
            return new GenericMessage<>(listing.getObjectSummaries().stream().map(S3ObjectSummary::getKey).toList());
        };
    }

    @Splitter(inputChannel = "s3InputChannel", outputChannel = "downloadChannel")
    public List<String> ownersSplitter(List<String> fileKeys) {
        log.info("Splitting [{}] file keys", fileKeys.size());
        return fileKeys;
    }

    @ServiceActivator(inputChannel = "downloadChannel", outputChannel = "deleteRemoteFileChannel")
    public File downloadFile(String fileKey) {
        File localFile = new File("output", fileKey);
        GetObjectRequest request = new GetObjectRequest("output", fileKey);
        S3Object object = amazonS3.getObject(request);
        S3ObjectInputStream inputStream = object.getObjectContent();
        try (FileOutputStream localOut = new FileOutputStream(localFile)) {
            inputStream.transferTo(localOut);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return localFile;
    }

    @ServiceActivator(inputChannel = "deleteRemoteFileChannel", outputChannel = "fileChannel")
    public File deleteRemoteFile(File file) {
        amazonS3.deleteObject("output", file.getName());
        return file;
    }

    @Transformer(inputChannel = "fileChannel", outputChannel = "launchJobChannel")
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
