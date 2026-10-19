package com.gpstl.joyn.common.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gpstl.joyn.common.models.User;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    // Vérifie l'existence de l'email sans distinction de casse
    boolean existsByEmailIgnoreCase(String email);

    // Permettra aussi de retrouver l'utilisateur (utile pour le futur login)
    Optional<User> findByEmailIgnoreCase(String email);
}