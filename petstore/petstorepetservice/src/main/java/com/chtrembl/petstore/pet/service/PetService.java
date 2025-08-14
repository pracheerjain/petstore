package com.chtrembl.petstore.pet.service;

import com.chtrembl.petstore.pet.model.Pet;
import com.chtrembl.petstore.pet.repository.PetRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class PetService {

    private final PetRepository petRepository;

//    @Value("${dbUrl}") // Replace with the actual secret name in Key Vault
//    private String mySecretValue;

    public PetService(PetRepository petRepository) {
        this.petRepository = petRepository;
    }
    public List<Pet> findPetsByStatus(List<String> status) {
        log.info("Finding pets with status: {}", status);

        return getAllPets().stream()
                .filter(pet -> status.contains(pet.getStatus().toString()))
                .toList();
    }

    public Optional<Pet> findPetById(Long petId) {
        log.info("Finding pet with id: {}", petId);

        return getAllPets().stream()
                .filter(pet -> pet.getId().equals(petId))
                .findFirst();
    }

    public List<Pet> getAllPets() {
        log.info("Getting all pets");
        return petRepository.findAll();
    }

    public int getPetCount() {
        return petRepository.findAll().size();
    }
}