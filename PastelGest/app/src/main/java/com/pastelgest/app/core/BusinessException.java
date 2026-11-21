package com.pastelgest.app.core;

/** Erro de regra de negócio com mensagem pronta a mostrar ao utilizador (pt-PT). */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
