package com.example.demo.integration.extract;

import com.example.demo.domain.Pet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaCursorItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.transform.BeanWrapperFieldExtractor;
import org.springframework.batch.item.file.transform.DelimitedLineAggregator;
import org.springframework.batch.item.file.transform.FieldExtractor;
import org.springframework.batch.item.file.transform.LineAggregator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import javax.persistence.EntityManagerFactory;
import java.util.Collections;

@Configuration
@Slf4j
public class ExtractionBatchConfig {

    private final EntityManagerFactory entityManagerFactory;
    private final StepBuilderFactory stepBuilderFactory;
    private final JobBuilderFactory jobBuilderFactory;

    public ExtractionBatchConfig(EntityManagerFactory entityManagerFactory, StepBuilderFactory stepBuilderFactory, JobBuilderFactory jobBuilderFactory) {
        this.entityManagerFactory = entityManagerFactory;
        this.stepBuilderFactory = stepBuilderFactory;
        this.jobBuilderFactory = jobBuilderFactory;
    }

    @Bean
    public FieldExtractor<Pet> petFieldExtractor() {
        BeanWrapperFieldExtractor<Pet> extractor = new BeanWrapperFieldExtractor<>();
        extractor.setNames(new String[] {
                "id",
                "owner.id",
                "name"
        });
        return extractor;
    }

    @Bean
    public LineAggregator<Pet> petLineAggregator(@Qualifier("petFieldExtractor") FieldExtractor<Pet> fieldExtractor) {
        DelimitedLineAggregator<Pet> lineAggregator = new DelimitedLineAggregator<>();
        lineAggregator.setFieldExtractor(fieldExtractor);
        return lineAggregator;
    }

    @Bean
    @StepScope
    public JpaCursorItemReader<Pet> petReader(@Value("#{jobParameters['owner_id']}") Long ownerId) {
        log.debug("Creating reader for owner id [{}]", ownerId);
        JpaCursorItemReader<Pet> petReader = new JpaCursorItemReader<>();
        petReader.setQueryString("from Pet where owner_id = :owner_id and name is null");
        petReader.setEntityManagerFactory(entityManagerFactory);
        petReader.setParameterValues(Collections.singletonMap("owner_id", ownerId));
        return petReader;
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<Pet> petWriter(@Value("#{jobParameters['owner_id']}") Long ownerId, @Value("#{jobParameters['file']}") String file, @Qualifier("petLineAggregator") LineAggregator<Pet> lineAggregator) {
        log.debug("Creating writer for owner id [{}]", ownerId);
        FlatFileItemWriter<Pet> petWriter = new FlatFileItemWriter<>();
        petWriter.setResource(new FileSystemResource(file));
        petWriter.setLineAggregator(lineAggregator);
        petWriter.setHeaderCallback(writer -> writer.write("id,owner_id,name"));
        return petWriter;
    }

    @Bean
    public Step extractStep(@Qualifier("petReader") ItemReader petReader, @Qualifier("petWriter") ItemWriter petWriter) {
        return stepBuilderFactory.get("extract")
                .chunk(5)
                .reader(petReader)
                .writer(petWriter)
                .build();
    }

    @Bean
    public Job extractJob(@Qualifier("extractStep") Step extractStep) {
        return jobBuilderFactory.get("extractJob")
                .incrementer(new RunIdIncrementer())
                .start(extractStep)
                .build();
    }

}
