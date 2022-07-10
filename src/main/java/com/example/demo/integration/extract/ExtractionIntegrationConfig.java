package com.example.demo.integration.extract;

import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.example.demo.domain.Owner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.*;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.jpa.core.JpaExecutor;
import org.springframework.integration.jpa.inbound.JpaPollingChannelAdapter;
import org.springframework.messaging.MessageChannel;

import javax.persistence.EntityManager;
import java.io.File;
import java.util.Date;
import java.util.List;

@Configuration
@Slf4j
public class ExtractionIntegrationConfig {

    private static final String OWNER_ID = "owner_id",
                                FILE = "file",
                                EXECUTION_TIME = "execution_time";

    private final EntityManager entityManager;
    private final Job extractJob;
    private final JobLauncher jobLauncher;
    private final TransferManager amazonS3TransferManager;

    public ExtractionIntegrationConfig(EntityManager entityManager, @Qualifier("extractJob") Job extractJob, JobLauncher jobLauncher, TransferManager amazonS3TransferManager) {
        this.entityManager = entityManager;
        this.extractJob = extractJob;
        this.jobLauncher = jobLauncher;
        this.amazonS3TransferManager = amazonS3TransferManager;
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }

    @Bean(name = "ownersChannel")
    public MessageChannel ownersChannel() {
        return new DirectChannel();
    }

    @Bean
    public JpaExecutor jpaExecutor() {
        JpaExecutor jpaExecutor = new JpaExecutor(getEntityManager());
        jpaExecutor.setJpaQuery("from Owner");
        return jpaExecutor;
    }

    @Bean
    @InboundChannelAdapter(value = "ownersChannel", poller = @Poller())
    public MessageSource<?> ownerMessageSource(JpaExecutor jpaExecutor) {
        return new JpaPollingChannelAdapter(jpaExecutor);
    }

    @Splitter(inputChannel = "ownersChannel", outputChannel = "ownerChannel")
    public List<Owner> ownersSplitter(List<Owner> owners) {
        log.info("Splitting [{}] owners", owners.size());
        return owners;
    }

    @Transformer(inputChannel = "ownerChannel", outputChannel = "launchJobChannel")
    public JobParameters ownersToJobParametersTransformer(Owner owner) {
        log.info("Building job parameters for owner [{}]", owner.getId());
        return new JobParametersBuilder()
                .addLong(OWNER_ID, owner.getId())
                .addString(FILE, String.format("output/%s-%s.csv", owner.getId(), System.currentTimeMillis()))
                .addDate(EXECUTION_TIME, new Date())
                .toJobParameters();
    }

    @ServiceActivator(inputChannel = "launchJobChannel", outputChannel = "transferToS3")
    public File launchExtractJob(JobParameters jobParameters) throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException {
        log.info("Launching extractJob with parameters [{}]", jobParameters);
        JobExecution jobExecution = jobLauncher.run(extractJob, jobParameters);
        log.info("Job execution [{}] ended at [{}] with status [{}] for parameters [{}]",
                jobExecution.getId(), jobExecution.getEndTime(), jobExecution.getStatus(), jobExecution.getJobParameters());
        return new File(jobParameters.getString(FILE));
    }

    @ServiceActivator(inputChannel = "transferToS3", outputChannel = "deleteLocalFile")
    public File transferToS3(File file) throws InterruptedException {
        log.info("Uploading [{}] to [input] S3 bucket", file.getName());
        PutObjectRequest request = new PutObjectRequest("input", file.getName(), file);
        Upload upload = amazonS3TransferManager.upload(request);
        upload.waitForCompletion();
        return file;
    }

    @ServiceActivator(inputChannel = "deleteLocalFile", outputChannel = "end")
    public Boolean deleteLocalFile(File file) {
        log.info("Deleting file [{}]", file.getName());
        return file.delete();
    }

    @Aggregator(inputChannel = "end")
    public void shutdown() {
        log.info("Integration flow complete, shutting down JVM");
        System.exit(0); // kills the JVM when all integration steps are finished, this allows single run scheduling
    }
}
