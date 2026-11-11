package com.pfc.thindesk.repository;

import com.pfc.thindesk.entity.Tecnico;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TecnicoRepository extends MongoRepository<Tecnico, String> {
    List<Tecnico> findByAtivoTrueOrderByNomeAsc();
}
