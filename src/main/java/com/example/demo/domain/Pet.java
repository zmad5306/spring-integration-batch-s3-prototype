package com.example.demo.domain;

import lombok.Data;

import javax.persistence.*;

@Entity
@Data
public class Pet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @ManyToOne
    Owner owner;
    String name;
}
