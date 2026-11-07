create table operacao_portabilidade (
    id                 uuid primary key,
    beneficiario_id    varchar(100)             not null,
    status             varchar(20)              not null,
    -- nulo até a Central aceitar o envio
    protocolo          varchar(100),
    motivo_rejeicao    varchar(500),
    banco              varchar(3)               not null,
    agencia            varchar(4)               not null,
    conta              varchar(20)              not null,
    digito             varchar(1)               not null,
    chave_idempotencia uuid                     not null unique,
    -- SHA-256 (hex) do corpo da requisição, para reconhecer a mesma intenção (ADR-0005)
    hash_corpo         varchar(64)              not null,
    criada_em          timestamp with time zone not null,
    atualizada_em      timestamp with time zone not null
);

create table operacao_parcela (
    operacao_id uuid           not null references operacao_portabilidade (id),
    parcela_id  varchar(100)   not null,
    valor       numeric(15, 2) not null,
    primary key (operacao_id, parcela_id)
);

-- só de inserção (ADR-0007)
create table operacao_evento (
    id          bigint generated always as identity primary key,
    operacao_id uuid                     not null references operacao_portabilidade (id),
    tipo        varchar(20)              not null,
    data_hora   timestamp with time zone not null,
    detalhe     varchar(500)
);

create index operacao_evento_operacao_id on operacao_evento (operacao_id);
