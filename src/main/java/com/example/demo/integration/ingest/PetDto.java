package com.example.demo.integration.ingest;

import lombok.Data;

@Data
public class PetDto {
    private Long id;
    private Long ownerId;
    private String name;
}
