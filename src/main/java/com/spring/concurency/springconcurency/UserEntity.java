package com.spring.concurency.springconcurency;


import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "email_hash", unique = true, nullable = false)
    private Integer emailHash;
    // Constructors
    public UserEntity() {
    }

    public UserEntity(String email,Integer emailHash) {
        this.email = email;
        this.createdAt = LocalDateTime.now();
        this.emailHash = emailHash;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getEmailHash() {
        return emailHash;
    }

    public void setEmailHash(Integer emailHash) {
        this.emailHash = emailHash;
    }
}