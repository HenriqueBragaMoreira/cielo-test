-- Trava de parcela (ADR-0005): uma parcela só pode estar em uma operação PENDENTE ou CONFIRMADA; REJEITADA e
-- FALHA_ENVIO liberam as parcelas. `ativa` espelha o status da operação e é mantida só pelos gatilhos abaixo, para a
-- trava acompanhar qualquer transição de estado sem depender de quem a grava.
alter table operacao_parcela add column ativa boolean not null default true;

create function operacao_ativa(status varchar) returns boolean
    language sql immutable
    as $$ select status in ('PENDENTE', 'CONFIRMADA') $$;

create function operacao_parcela_herda_status() returns trigger
    language plpgsql
    as $$
begin
    select operacao_ativa(o.status) into new.ativa from operacao_portabilidade o where o.id = new.operacao_id;
    return new;
end
$$;

create trigger operacao_parcela_herda_status
    before insert on operacao_parcela
    for each row execute function operacao_parcela_herda_status();

create function operacao_portabilidade_propaga_status() returns trigger
    language plpgsql
    as $$
begin
    update operacao_parcela set ativa = operacao_ativa(new.status) where operacao_id = new.id;
    return null;
end
$$;

create trigger operacao_portabilidade_propaga_status
    after update of status on operacao_portabilidade
    for each row when (old.status is distinct from new.status)
    execute function operacao_portabilidade_propaga_status();

-- uma requisição concorrente com a mesma parcela espera aqui até o commit da outra e então viola o índice
create unique index operacao_parcela_ativa_unica on operacao_parcela (parcela_id) where ativa;
