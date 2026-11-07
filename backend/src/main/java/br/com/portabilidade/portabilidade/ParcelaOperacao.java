package br.com.portabilidade.portabilidade;

import jakarta.persistence.Embeddable;

import java.math.BigDecimal;

/** Parcela incluída na operação, com o valor lido da agenda da Central na criação. */
@Embeddable
public record ParcelaOperacao(String parcelaId, BigDecimal valor) {
}
