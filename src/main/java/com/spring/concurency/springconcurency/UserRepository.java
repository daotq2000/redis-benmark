package com.spring.concurency.springconcurency;

import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity,Long> {
    @Query(value = "select p from UserEntity p where p.email =:email")
    Optional<UserEntity> findByUserEmail(String email);

    @Query(value = "select p from UserEntity p where p.emailHash =:hash")
    Optional<UserEntity> findByEmailHash(Integer hash);


    Page<UserEntity> findAll(Pageable pageable);
}
