package com.example.demo.integration.ingest;

import com.example.demo.domain.Pet;
import com.example.demo.repository.PetRepository;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public class PetItemProcessor implements ItemProcessor<PetDto, Pet> {

    private final PetRepository petRepository;

    public PetItemProcessor(PetRepository petRepository) {
        this.petRepository = petRepository;
    }

    @Override
    @Transactional
    public Pet process(PetDto petDto) {
         Optional<Pet> pet = petRepository.findById(petDto.getId());
         pet.ifPresent(p -> p.setName(petDto.getName()));
         return pet.orElse(null);
    }
}
