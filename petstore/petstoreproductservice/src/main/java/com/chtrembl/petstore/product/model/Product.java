package com.chtrembl.petstore.product.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Entity
@Table(name = "product")
public class Product {
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
            name = "product_tag", // Match the table name in the database
            joinColumns = @JoinColumn(name = "product_id"), // Match the column name in the database
            inverseJoinColumns = @JoinColumn(name = "tag_id") // Match the column name in the database
    )
    private List<Tag> tags;

    @Enumerated(EnumType.STRING)
    private Status status;

    public Product name(String name) {
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
