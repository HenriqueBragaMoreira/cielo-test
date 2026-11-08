---
name: Portabilidade de convênios
description: Ferramenta interna do operador de backoffice da nova credora, com a identidade visual da Cielo.
colors:
  brand-navy: "#214986"
  brand-cyan: "#00aeef"
  primary: "#0a7db8"
  primary-foreground: "#ffffff"
  foreground: "#10233f"
  background: "#f3f6fa"
  card: "#ffffff"
  muted: "#eef2f7"
  muted-foreground: "#4a5d78"
  secondary: "#e8eef6"
  accent: "#e6f5fc"
  border: "#dde4ee"
  input: "#7f8ea3"
  success: "#1a7f4b"
  warning: "#9a5b00"
  destructive: "#b42318"
typography:
  display:
    fontFamily: "Nunito Sans Variable, sans-serif"
    fontSize: "2.75rem"
    fontWeight: 800
    lineHeight: 1.25
    letterSpacing: "-0.02em"
    fontFeature: "tnum"
  headline:
    fontFamily: "Nunito Sans Variable, sans-serif"
    fontSize: "2rem"
    fontWeight: 800
    lineHeight: 1.25
    letterSpacing: "-0.02em"
  title:
    fontFamily: "Nunito Sans Variable, sans-serif"
    fontSize: "1.75rem"
    fontWeight: 800
    lineHeight: 1.25
    letterSpacing: "-0.02em"
  body:
    fontFamily: "Nunito Sans Variable, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.43
    fontFeature: "tnum"
  label:
    fontFamily: "Nunito Sans Variable, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 700
    lineHeight: 1
  section:
    fontFamily: "Nunito Sans Variable, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 700
    letterSpacing: "0.08em"
rounded:
  sm: "6px"
  md: "8px"
  lg: "12px"
  xl: "16px"
  full: "9999px"
spacing:
  page-x: "32px"
  page-x-narrow: "16px"
  gap: "24px"
  card-x: "28px"
  card-y: "24px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.primary-foreground}"
    typography: "{typography.label}"
    rounded: "{rounded.full}"
    padding: "0 20px"
    height: "40px"
  button-primary-lg:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.primary-foreground}"
    rounded: "{rounded.full}"
    padding: "0 24px"
    height: "48px"
  button-outline:
    backgroundColor: "{colors.card}"
    textColor: "{colors.foreground}"
    rounded: "{rounded.full}"
    padding: "0 20px"
    height: "40px"
  input:
    backgroundColor: "{colors.card}"
    textColor: "{colors.foreground}"
    rounded: "{rounded.md}"
    padding: "4px 12px"
    height: "40px"
  card:
    backgroundColor: "{colors.card}"
    rounded: "{rounded.lg}"
    padding: "24px 28px"
  top-bar:
    backgroundColor: "{colors.brand-navy}"
    textColor: "#ffffff"
    height: "64px"
  status-pill:
    rounded: "{rounded.full}"
    padding: "2px 10px 2px 8px"
---

# Design System: Portabilidade de convênios

## Overview

**Creative North Star: "A agenda é um calendário"**

Uma ferramenta interna da Cielo para quem conduz a portabilidade o dia todo: barra azul-marinho com o wordmark, chão gelo, superfícies brancas e a tinta azul-escura. A marca aparece com precisão, não com volume. O ciano marca o que o operador escolheu ou vai fazer agora (a parcela selecionada, a ação principal, o passo atual), e verde, vermelho e âmbar só dizem o estado da operação.

A assinatura é a **régua da agenda**: cada parcela fica na sua data de desconto, num eixo de meses proporcional ao tempo, com a marca de "hoje" e o intervalo à vista no cabeçalho. A tabela embaixo é o controle; a régua só espelha a seleção. A linha do tempo da operação usa a mesma gramática (eixo, haste, ponto), então o operador lê a operação como continuação da agenda.

A densidade é de backoffice: números alinhados em algarismos tabulares, hierarquia por contraste de escala (o total em 44px, o estado da operação em 32px), nenhum texto didático. Só tema claro.

**Key Characteristics:**
- Barra de topo azul-marinho de 64px com o wordmark branco, o stepper e o link "Histórico".
- Chão gelo com cartões brancos contornados por uma linha de 1px, sem sombra.
- Ciano reservado: seleção, ação primária, passo atual.
- Verde, vermelho e âmbar só para estado, sempre com palavra e ícone.
- Dinheiro na mesma forma em três tamanhos (linha, subtotal, total).
- Régua de meses proporcional como assinatura, repetida na linha do tempo.

## Colors

Uma paleta de marca contida: dois azuis da Cielo, um ciano aprofundado para agir, neutros frios e três cores de estado.

### Primary
- **Azul-marinho Cielo** (`brand-navy`): o fundo da barra de topo e a cor do wordmark na versão azul; também a tinta da marca de "hoje" na régua. Conferido contra a logo de referência ([ADR-0012](docs/adr/0012-cielo-visual-identity.md)).
- **Ciano Cielo** (`brand-cyan`): o pingo do "i" e o arco de cima do "o" no wordmark, a parcela selecionada na régua, o sublinhado e o círculo do passo atual. É forma, nunca texto sobre branco: o contraste não sustenta texto.
- **Ciano de ação** (`primary`): o ciano aprofundado das ações primárias, das caixas marcadas e do anel de foco; texto branco sobre ele passa em AA.

### Neutral
- **Tinta azul-escura** (`foreground`): todo o texto principal, o eixo da régua, as hastes de parcelas elegíveis.
- **Chão gelo** (`background`): o fundo da página, por trás dos cartões.
- **Superfície branca** (`card`): cartões, resumo, campos.
- **Ardósia** (`muted-foreground`): rótulos secundários, datas pequenas, motivos de inelegibilidade, parcelas inelegíveis.
- **Névoa** (`muted`, `secondary`, `accent`): fundos de esqueletos de carregamento, botões secundários e a linha selecionada da tabela (`accent`, um véu de ciano).
- **Linha** (`border`) e **contorno de campo** (`input`): divisórias e contornos de cartões; o contorno de campo é mais escuro para o campo se distinguir do cartão.

### Estado
- **Verde** (`success`): operação confirmada, autorização registrada.
- **Âmbar** (`warning`): operação pendente (o painel de situação e a pílula do histórico), aviso de falha na atualização do status.
- **Vermelho** (`destructive`): operação rejeitada, falha no envio, erros de campo e da Central.

### Named Rules
**The Reserved Cyan Rule.** O ciano só marca seleção, ação primária e passo atual. Um elemento decorativo em ciano rouba o sinal de "isto é o que você escolheu".

**The State Is Never Only Color Rule.** Verde, vermelho e âmbar aparecem só como estado, e sempre junto de uma palavra e de um ícone.

## Typography

**Display Font:** Nunito Sans Variable (com fallback sans-serif)
**Body Font:** Nunito Sans Variable (com fallback sans-serif)

**Character:** Uma sans humanista e arredondada, da família das letras da marca, que fica legível pequena e firme em extrabold nos números grandes. Uma família só; a hierarquia vem de escala e peso.

### Hierarchy
- **Display** (800, 2.75rem, 1.25): o total a portar e o total da operação. Algarismos tabulares.
- **Headline** (800, 2rem, 1.25): o estado da operação no painel de situação ("Operação confirmada").
- **Title** (800, 1.75rem, 1.25): o título de cada tela e o identificador do beneficiário.
- **Body** (400, 0.875rem, 1.43): tabelas, detalhes, mensagens. Algarismos tabulares em toda a página.
- **Label** (700, 0.875rem): rótulos de campo e botões.
- **Section** (700, 0.75rem, 0.08em, maiúsculas): o título de cada cartão ("Agenda de descontos", "Resumo da operação", "Linha do tempo") e as palavras-guia do intervalo.

### Named Rules
**The Same Money Rule.** Um valor tem a mesma forma (`R$ 150,00`, tabular) na linha da tabela, na marca da régua e no total; só o tamanho muda.

## Layout

O conteúdo vive num contêiner de até 1536px, com margem lateral de 32px (16px em telas estreitas). A agenda é um workspace de duas colunas: a coluna da agenda, fluida, e o resumo, fixo em 400px e preso ao topo ao rolar (`sticky`). Abaixo de 1024px o resumo empilha sob a agenda. A operação usa a mesma divisão: painel de situação e linha do tempo à esquerda, o resumo do pedido à direita. A autorização é uma coluna estreita centrada (448px); o histórico, uma tabela de até 896px.

Ritmo: 24px entre blocos, 20px dentro das colunas, cartões com 24–28px de respiro. Em telas estreitas (abaixo de 640px), as tabelas tiram as colunas secundárias em vez de rolar: a agenda tira o convênio (que segue no resumo) e leva a situação para baixo do id da parcela; o histórico tira a quantidade de parcelas e o total (que seguem na operação), e a data quebra em duas linhas. A régua mostra um rótulo de mês sim, outro não (mais o último). Nunca há rolagem horizontal da página.

## Elevation & Depth

Sem sombras. A profundidade é tonal: cartões brancos sobre o chão gelo, contornados por uma linha de 1px (`border`). O painel de situação da operação troca a superfície e o contorno pela cor do estado, num véu de 5–7%. O resumo fica preso ao rolar, sem sombra.

### Named Rules
**The Flat Ledger Rule.** Elevação é declarada uma vez, pelo contorno de 1px. Nada de sombra sob cartão contornado.

## Shapes

Cantos suavemente arredondados: campos com 8px, cartões da agenda e do resumo com 12px, painéis da operação e cartões do histórico com 16px. Botões e pílulas de status são cápsulas completas. Na régua, as hastes são traços de 3px com pontas arredondadas e as parcelas são pontos de 18px com um aro branco que as separa do eixo.

## Components

### Buttons
Confiantes e contidos: cápsulas sem sombra.
- **Shape:** cápsula completa (`rounded.full`).
- **Primary:** ciano de ação com texto branco em negrito, 40px de altura (48px no tamanho grande do envio, da autorização e das ações finais).
- **Hover / Focus:** escurece 15% em direção ao preto; foco por contorno de 2px no ciano de ação com afastamento de 2px.
- **Outline:** superfície branca com contorno de linha; usado para "Tentar novamente", "Atualizar" e a ação secundária de um estado final.
- **Disabled:** 50% de opacidade. Durante um envio, o botão mostra um indicador girando e fica desabilitado.

### Chips
- **Pílula de status do histórico:** véu de 10% da cor do estado, texto e ícone na cor do estado, 12px em negrito ("Pendente", "Confirmada", "Rejeitada", "Falha no envio").

### Cards / Containers
- **Corner Style:** 12–16px.
- **Background:** branco sobre o chão gelo.
- **Shadow Strategy:** nenhuma; ver Elevation & Depth.
- **Border:** contorno de 1px na cor de linha.
- **Internal Padding:** 24px vertical, 20–28px horizontal.
- **Título:** o estilo Section, alinhado à esquerda; à direita, quando houver, as palavras-guia do intervalo.

### Tabela de parcelas
- **Linha selecionada:** véu de ciano (`accent`).
- **Linha inelegível:** fundo em névoa, texto em ardósia, caixa de seleção desabilitada com fundo em névoa e contorno claro. O motivo fica numa sublinha colada, logo abaixo do id da parcela, em 11px, e corre sob as outras colunas: cabe numa linha do tablet para cima sem alargar a coluna da parcela. A caixa de seleção aponta para ele (`aria-describedby`).
- **Valores:** alinhados à direita, em algarismos tabulares.

### Inputs / Fields
- **Style:** branco, contorno de 1px mais escuro que a linha dos cartões, 8px de canto, 40px de altura.
- **Focus:** o contorno global de 2px no ciano de ação.
- **Error:** contorno vermelho com um véu de 20%; a mensagem fica logo abaixo do campo, em vermelho, 12px.

### Navigation
- **Barra de topo:** azul-marinho, 64px. Wordmark branco à esquerda, separado por um divisor de "Portabilidade de convênios"; os dois juntos são o link para o início do fluxo, de qualquer tela. À direita, o stepper (1 Autorização · 2 Parcelas e conta · 3 Acompanhamento) e o link "Histórico" depois de um divisor.
- **Stepper:** o passo atual em branco e negrito, com o número num círculo ciano e sublinhado ciano de 2px; os concluídos com um visto num círculo translúcido; os seguintes com o número num aro. Em telas estreitas, só o passo atual mostra o nome.
- **Histórico:** branco a 75%; na página do histórico, branco em negrito com o mesmo sublinhado ciano. O cabeçalho da página tem "Nova portabilidade" à direita do título, com ou sem operações na lista.
- **Voltar:** cada etapa depois da autorização tem um link com seta, em ciano de ação, acima do título: "Trocar beneficiário" na agenda, "Agenda do BEN001" na operação.
- **Cabeçalho do beneficiário:** "Beneficiário" em ardósia e o id em extrabold, no tamanho Title; abaixo, os dados da autorização como campos rotulados (Autorização, Identificador na Central), separados por um divisor, nunca uma frase corrida.

### Régua da agenda (assinatura)
O eixo de meses é uma linha de 2px na tinta, com marcas altas no início de cada mês e curtas nos dias 10 e 20. "Hoje" é uma linha tracejada azul-marinho com o rótulo em cima. Cada parcela tem uma haste e um ponto na data de desconto, com o valor (15px, extrabold) e "dd/mm · id" acima. Estados: selecionada em ciano, elegível na tinta com o ponto vazado, inelegível em cinza opaco. O rótulo de uma parcela próxima demais de outra sobe para o nível mais baixo livre (até três níveis), e cada rótulo tem o fundo do cartão para a haste vizinha passar por trás. A régua é oculta para leitores de tela: a tabela é o controle.

### Linha do tempo da operação
A mesma gramática da régua: um eixo de 2px, uma haste e um ponto por evento, com o nome do evento e a data e hora acima. Os eventos de passagem ficam na tinta; o final, na cor do estado. Aqui a distância é a ordem, não a duração. Num cartão estreito (abaixo de 42rem) ou com mais de cinco eventos, o eixo fica de pé: cada evento vira uma linha, com o ponto no eixo e a haste deitada até o rótulo.

### Painel de situação
O elemento dominante da operação: ícone de 40px e o estado em palavras no estilo Headline, na cor do estado, sobre um véu da mesma cor. Pendente é âmbar, confirmada é verde, rejeitada e falha no envio são vermelhas. O painel tem a altura do conteúdo: o estado à esquerda e as ações à direita, que só descem quando o cartão estreita. As ações do estado: "Atualizar" e o indicador de consulta automática enquanto pendente; "Nova portabilidade" e, depois de rejeição ou falha no envio, "Voltar à agenda".

## Do's and Don'ts

### Do:
- **Do** manter o ciano de marca (`brand-cyan`) só em formas (marcas da régua, passo atual, wordmark) e usar o ciano de ação (`primary`) em botões e texto clicável.
- **Do** escrever cada estado com palavra, ícone e cor, nessa ordem de importância.
- **Do** usar algarismos tabulares e o mesmo formato `R$ 0,00` em todos os tamanhos.
- **Do** desenhar qualquer sequência temporal na gramática da régua: eixo, haste, ponto.
- **Do** separar superfícies por contorno de 1px sobre o chão gelo.

### Don't:
- **Don't** pintar áreas grandes de azul-marinho ou ciano fora da barra de topo; a marca aparece com precisão, não com volume.
- **Don't** usar verde, vermelho ou âmbar como decoração ou destaque; são só estado.
- **Don't** somar sombra a um cartão já contornado.
- **Don't** introduzir modo escuro: o produto é só tema claro (ADR-0012).
- **Don't** repetir a logo: ela aparece uma vez, na barra de topo.
