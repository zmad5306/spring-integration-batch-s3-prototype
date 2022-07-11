package com.example.demo.integration.ingest;

import com.example.demo.domain.Pet;
import com.example.demo.repository.PetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.batch.item.file.mapping.DefaultLineMapper;
import org.springframework.batch.item.file.transform.DelimitedLineTokenizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;

import javax.persistence.EntityManagerFactory;

@Configuration
@Slf4j
public class IngestionBatchConfig {

    private final EntityManagerFactory entityManagerFactory;
    private final StepBuilderFactory stepBuilderFactory;
    private final JobBuilderFactory jobBuilderFactory;

    public IngestionBatchConfig(EntityManagerFactory entityManagerFactory, StepBuilderFactory stepBuilderFactory, JobBuilderFactory jobBuilderFactory) {
        this.entityManagerFactory = entityManagerFactory;
        this.stepBuilderFactory = stepBuilderFactory;
        this.jobBuilderFactory = jobBuilderFactory;
    }

    @Bean
    public PetItemProcessor petItemProcessor(PetRepository petRepository) {
        return new PetItemProcessor(petRepository);
    }

    @Bean
    @StepScope
    public FlatFileItemReader<PetDto> petNameFileReader(@Value("#{jobParameters['file']}") String file) {
        DelimitedLineTokenizer tokenizer = new DelimitedLineTokenizer();
        tokenizer.setNames(new String[] { "id", "ownerId", "name" });

        BeanWrapperFieldSetMapper fieldSetMapper = new BeanWrapperFieldSetMapper<>();
        fieldSetMapper.setTargetType(PetDto.class);

        DefaultLineMapper lineMapper = new DefaultLineMapper();
        lineMapper.setLineTokenizer(tokenizer);
        lineMapper.setFieldSetMapper(fieldSetMapper);

        FlatFileItemReader<PetDto> petNameFileReader = new FlatFileItemReader<>();
        petNameFileReader.setResource(new FileSystemResource(file));
        petNameFileReader.setLinesToSkip(1);
        petNameFileReader.setLineMapper(lineMapper);

        return petNameFileReader;
    }

    @Bean
    @StepScope
    public JpaItemWriter<Pet> petUpdateWriter() {
        JpaItemWriter<Pet> petUpdateWriter = new JpaItemWriter<>();
        petUpdateWriter.setEntityManagerFactory(entityManagerFactory);
        return petUpdateWriter;
    }

    @Bean
    public Step ingestStep(@Qualifier("petNameFileReader") ItemReader petNameFileReader, PetItemProcessor processor, @Qualifier("petUpdateWriter") ItemWriter petUpdateWriter) {
        return stepBuilderFactory.get("ingest")
                .<PetDto, Pet> chunk(5)
                .reader(petNameFileReader)
                .processor(processor)
                .writer(petUpdateWriter)
                .build();
    }

    @Bean(name = "ingestJob")
    public Job ingestJob(@Qualifier("ingestStep") Step extractStep) {
        return jobBuilderFactory.get("ingestJob")
                .incrementer(new RunIdIncrementer())
                .start(extractStep)
                .build();
    }

}
