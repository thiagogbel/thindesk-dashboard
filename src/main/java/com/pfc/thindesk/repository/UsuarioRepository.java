package com.pfc.thindesk.repository;

import com.pfc.thindesk.entity.Usuario;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface UsuarioRepository extends MongoRepository<Usuario, String> {
    Usuario findByUsername(String username);
}
