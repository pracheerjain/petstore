package com.chtrembl.petstore.pet.model;

import com.chtrembl.petstore.pet.repository.PetRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableConfigurationProperties
@EnableJpaRepositories
//@AllArgsConstructor
//@NoArgsConstructor
//@RequiredArgsConstructor
//@ConfigurationProperties("data")
@Data
public class DataPreload {

//	@Autowired
//	private final PetRepository petRepository;
//
//	public DataPreload(PetRepository petRepository){
//		this.petRepository = petRepository;
//	}
//
//	public List<Pet> getPets() {
//		return petRepository.findAll();
//	}
//
////	private List<Pet> pets = petRepository.findAll();
}
