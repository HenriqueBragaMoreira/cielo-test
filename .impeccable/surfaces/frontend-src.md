---
version: 1
slug: "frontend-src"
primary_target: "frontend/src"
related_targets: []
---

# Portabilidade — app do operador

Escopo: o app inteiro (autorização → agenda → operação → histórico). Modo: Operate. Build guiado por código.

Público e tarefa: operador de backoffice da nova credora conduzindo a portabilidade. Restrições: só tema claro; a marca aparece com precisão, não com volume; o estado nunca pode ficar ambíguo; regras de idempotência, polling e elegibilidade intactas; nova rota `GET /api/operacoes-portabilidade` para o histórico.

## Direction contract

THESIS: a agenda de descontos é um calendário. Cada parcela fica na data real, numa régua de meses proporcional ao tempo, e a operação continua no mesmo eixo. Recusa a tabela solta dentro de um card cinza.

OWN-WORLD: barra de topo azul-marinho Cielo (~#1E4B8C) com o wordmark branco e o "o" ciano; chão gelo (#F3F6FA) com superfícies brancas; ciano (~#12A5E3) só para parcelas selecionadas na régua, ação primária e o passo atual; tinta azul-escura (#10233F); verde, vermelho e âmbar só como estado. Algarismos tabulares e botão primário em pílula.
Pegadas dos desafiantes descartados: distância é duração (labanotation); palavras-guia com a primeira e a última data à vista (lexicon); hierarquia por contraste de escala (specimen); inelegíveis em cinza com o motivo fixo abaixo (mascots); a mesma forma de dinheiro em três tamanhos (bitmap).

STORY: o operador vê o intervalo da agenda e onde cada parcela cai em relação a hoje, marca as parcelas na tabela (a régua acende as marcas), confere o total e a conta no resumo, envia, e acompanha a operação: a linha do tempo de eventos usa a mesma gramática de régua. O histórico fica no topo.

FIRST VIEWPORT: barra de topo de 64px (logo, "Portabilidade de convênios", stepper 1 Autorização / 2 Parcelas e conta / 3 Acompanhamento, Histórico); à esquerda, o cabeçalho do beneficiário com a confirmação da autorização, depois o cartão da agenda com palavras-guia, a régua de meses (marca "hoje", parcelas com valor e data) e a tabela; à direita, uma coluna de 400px de resumo com o total em 44px, a seleção, a conta de destino e o "Enviar portabilidade". Aviso de avaliação no rodapé.

FORM: assigned, candidato 7 de 7 da minha lista ancorada; seed: 203da447b3ccc707.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
