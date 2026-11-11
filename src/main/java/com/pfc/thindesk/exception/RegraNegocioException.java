package com.pfc.thindesk.exception;

/** Gera HTTP 422 (requisicao valida, mas viola regra de negocio). */
public class RegraNegocioException extends RuntimeException {
    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
