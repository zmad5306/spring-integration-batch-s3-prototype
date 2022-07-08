package com.example.demo.integration.extract;

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
import java.util.Date;
import java.util.List;

@Configuration
@Slf4j
public class ExtractionIntegrationConfig {

    private static final String OWNER_ID = "owner_id";
    private static final String EXECUTION_TIME = "execution_time";

    private final EntityManager entityManager;
    private final Job extractJob;
    private final JobLauncher jobLauncher;

    public ExtractionIntegrationConfig(EntityManager entityManager, @Qualifier("extractJob") Job extractJob, JobLauncher jobLauncher) {
        this.entityManager = entityManager;
        this.extractJob = extractJob;
        this.jobLauncher = jobLauncher;
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
        return owners;
    }

    @Transformer(inputChannel = "ownerChannel", outputChannel = "launchJobChannel")
    public JobParameters ownersToJobParametersTransformer(Owner owner) {
        return new JobParametersBuilder()
                .addLong(OWNER_ID, owner.getId())
                .addDate(EXECUTION_TIME, new Date())
                .toJobParameters();
    }

    @ServiceActivator(inputChannel = "launchJobChannel", outputChannel = "end")
    public JobExecution launchExtractJob(JobParameters jobParameters) throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobParametersInvalidException, JobRestartException {
        log.info("Launching extractJob with parameters [{}]", jobParameters);
        return jobLauncher.run(extractJob, jobParameters);
    }

    @Aggregator(inputChannel = "end")
    public void end(List<JobExecution> jobExecutions) {
        jobExecutions.stream().forEach(jobExecution -> log.info("Job execution [{}] ended at [{}] with status [{}] for parameters [{}]",
                jobExecution.getId(), jobExecution.getEndTime(), jobExecution.getStatus(), jobExecution.getJobParameters()));
        System.exit(0); // kills the JVM when all integration steps are finished, this allows single run scheduling
    }
}
