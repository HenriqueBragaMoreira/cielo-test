-- o motivo de rejeição vem da Central sem limite de tamanho; um motivo longo faria toda consulta da operação falhar
alter table operacao_portabilidade alter column motivo_rejeicao type text;
alter table operacao_evento alter column detalhe type text;
