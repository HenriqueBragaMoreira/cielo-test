package br.com.portabilidade.autorizacao;

import org.springframework.data.jpa.repository.JpaRepository;

interface AutorizacaoRepository extends JpaRepository<Autorizacao, String> {
}
