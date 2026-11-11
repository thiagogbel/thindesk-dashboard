package com.pfc.thindesk.config;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Usado nos templates: ${@fmt.dataHora(c.dataAbertura)} */
@Component("fmt")
public class Formatador {

    private final DateTimeFormatter formato;

    public Formatador(@Value("${thindesk.fuso-horario:America/Sao_Paulo}") String fuso) {
        this.formato = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of(fuso));
    }

    public String dataHora(Instant instante) {
        return instante == null ? "" : formato.format(instante);
    }
}
