# Linguagem de domínio em português

Os termos de domínio ficam em português no código, na API e na documentação (`Beneficiario`, `Parcela`, `Agenda`, `Autorizacao`, `OperacaoPortabilidade`, `Protocolo`), exatamente como no contrato da Central de Convênios e no [glossário](../../GLOSSARY.md). Os termos técnicos ficam em inglês (`Controller`, `Repository`, `Client`, `Service`, `Config`). Traduzir o domínio para inglês obrigaria quem lê a mapear de cabeça cada campo entre o nosso código e a Central.

## Consequências

- Nomes como `AutorizacaoController` e `ParcelaRepository` misturam os dois idiomas de propósito, e não devem ser "corrigidos".
- Identificadores não levam acento (`Autorizacao`, `beneficiarioId`).
- Os nomes de arquivos de documentação ficam em en-US, em kebab-case, e o conteúdo fica em português.
