package com.pfc.thindesk.repository;

import com.pfc.thindesk.entity.Chamado;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChamadoRepository extends MongoRepository<Chamado, String> {
    Optional<Chamado> findByProtocolo(String protocolo);
}
