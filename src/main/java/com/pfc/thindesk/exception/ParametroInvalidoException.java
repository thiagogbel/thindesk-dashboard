package com.pfc.thindesk.exception;

/** Gera HTTP 400 (filtro ou parametro invalido). */
public class ParametroInvalidoException extends RuntimeException {
    public ParametroInvalidoException(String mensagem) {
        super(mensagem);
    }
}
