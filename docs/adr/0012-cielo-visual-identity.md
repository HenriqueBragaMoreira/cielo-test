# Identidade visual Cielo, só tema claro

A interface se apresenta como uma ferramenta interna da Cielo para o operador de backoffice da nova credora: wordmark "cielo" no topo, azul-marinho e ciano da marca, chão claro. O avaliador é da Cielo, e uma ferramenta que já parece da casa mostra cuidado com o contexto dele. A marca aparece com precisão, não com volume:
- o ciano fica reservado para a seleção, a ação principal e o passo atual;
- verde, vermelho e âmbar só aparecem como estado da operação;
- a logo aparece uma vez, no topo.

A assinatura da interface é a **régua da agenda**: as parcelas ficam marcadas numa régua de meses proporcional ao tempo real, com a marca de "hoje" e o intervalo à vista no cabeçalho. Ela espelha a seleção feita na tabela, que continua sendo o controle acessível, e por isso fica oculta para leitores de tela. A linha do tempo da operação usa a mesma gramática.

## Decisões

- **Logo recriada em SVG** a partir da referência pública, em duas versões: branca sobre azul e azul sobre branco. Não há arquivo oficial no projeto.
- **Cores conferidas contra a referência:** amostradas da logo, o azul-marinho é `#214986` e o ciano é `#00AEEF` (no pingo do "i" e na metade de cima do "o"), e não as aproximações `#1E4B8C` e `#12A5E3` do início do redesign. O ciano da marca não tem contraste para texto sobre branco, então fica para formas e marcas; a ação primária usa um ciano aprofundado, `#0A7DB8`, com texto branco em AA.
- **Aviso fixo no rodapé:** "Projeto de avaliação técnica, sem vínculo oficial com a Cielo." O repositório é público, e a marca é da Cielo.
- **Só tema claro.** O operador trabalha num escritório durante o dia, e a marca é clara. O `ThemeProvider` e os tokens do modo escuro saem.
- **Os componentes shadcn/ui seguem como base** ([0011](./0011-frontend-stack.md)), mas são reestilizados pelos tokens da marca: nada fica com a cara padrão do template.

## Opções consideradas

- **Só a paleta, com um wordmark próprio:** evitaria qualquer questão de marca, mas perde o reconhecimento imediato.
- **Claro e escuro com toggle:** dobra o trabalho de acabamento por um ganho que o operador não pede.
- **Anel de estado inspirado no "o" da logo** como assinatura: descartado em favor da régua, que expressa o domínio (a agenda é um calendário) em vez de repetir a marca.

## Consequências

- Uso da marca de terceiros num repositório público, mitigado pelo aviso de que não há vínculo oficial. Se a Cielo pedir, basta trocar o wordmark e a paleta nos tokens.
- Com as 1 a 2 parcelas do simulador a régua fica esparsa; ela ganha peso com agendas de vários meses.
