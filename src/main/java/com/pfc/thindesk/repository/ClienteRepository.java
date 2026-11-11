package com.pfc.thindesk.repository;

import com.pfc.thindesk.entity.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ClienteRepository extends MongoRepository<Cliente, String> {
    Page<Cliente> findByNomeContainingIgnoreCase(String nome, Pageable pageable);
}
