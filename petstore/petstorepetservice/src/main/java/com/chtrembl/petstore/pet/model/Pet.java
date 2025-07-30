package com.chtrembl.petstore.pet.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Entity
@Table(name = "pet")
public class Pet {
    @Id
    private Long id;

    @Valid
    @Embedded
    @ManyToOne
    private Category category;

    @NotNull
    private String name;

    @JsonProperty("photoURL")
    @NotNull
    private String photoURL;

    @Valid
    @ManyToMany(cascade = CascadeType.ALL)
    @JoinTable(
            name = "pet_tag", // Match the table name in the database
            joinColumns = @JoinColumn(name = "pet_id"), // Match the column name in the database
            inverseJoinColumns = @JoinColumn(name = "tag_id") // Match the column name in the database
    )
    private List<Tag> tags;

    @Enumerated(EnumType.STRING)
    private Status status;

    public Pet name(String name) {
        this.name = name;
        return this;
    }

    public enum Status {
        available,
        pending,
        sold;

        Status() { }

    }
}
