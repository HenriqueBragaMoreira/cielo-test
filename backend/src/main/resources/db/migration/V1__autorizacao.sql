create table autorizacao (
    beneficiario_id varchar(100) primary key,
    autorizacao_id  varchar(100)             not null,
    autorizada_em   timestamp with time zone not null
);
