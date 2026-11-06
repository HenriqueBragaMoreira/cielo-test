# Portabilidade de Convênios

Plataforma que permite a uma nova credora assumir o direito de receber parcelas de convênios de desconto em folha que hoje pertencem a outro credor.

## Language

### Participantes

**Beneficiário**:
Pessoa que tem parcelas descontadas em folha e cuja autorização é necessária para a portabilidade.
_Avoid_: cliente, servidor, tomador

**Credor original**:
Quem hoje tem o direito de receber as parcelas de um convênio.
_Avoid_: credor antigo, banco de origem

**Nova credora**:
A empresa que opera esta plataforma e passa a receber as parcelas portadas.
_Avoid_: credor de destino, cessionária

**Central de Convênios**:
Sistema externo que concentra as autorizações dos beneficiários, a agenda de descontos e o processamento das operações de portabilidade.
_Avoid_: Central, mock, registradora

### Autorização e agenda

**Autorização**:
Consentimento (opt-in) do beneficiário para que a nova credora consulte sua agenda e porte suas parcelas, registrado na Central de Convênios.
_Avoid_: consentimento, aceite, opt-in (como termo isolado)

**Convênio**:
Acordo de desconto em folha ao qual um conjunto de parcelas pertence.
_Avoid_: contrato, empréstimo

**Agenda**:
O conjunto de parcelas futuras de um beneficiário, como informado pela Central de Convênios.
_Avoid_: agenda de recebíveis, extrato, cronograma

**Parcela**:
Um desconto futuro, com valor e data de desconto, pertencente a um convênio.
_Avoid_: prestação, recebível, título

### Portabilidade

**Operação de portabilidade**:
Pedido para transferir uma ou mais parcelas de um beneficiário para a nova credora, que termina confirmado ou rejeitado pela Central de Convênios.
_Avoid_: portabilidade (como substantivo contável), pedido, transação, solicitação

**Protocolo**:
Identificador que a Central de Convênios atribui a uma operação de portabilidade aceita para processamento.
_Avoid_: ticket, número do pedido

**Conta de recebimento de destino**:
Conta da nova credora onde passam a ser creditadas as parcelas portadas.
_Avoid_: conta destino, conta do cliente

**Confirmação**:
Decisão da Central de Convênios que efetiva uma operação de portabilidade, transferindo as parcelas para a nova credora. Não tem relação com a autorização do beneficiário.
_Avoid_: aceite, aprovação

**Rejeição**:
Decisão da Central de Convênios que recusa uma operação de portabilidade, acompanhada de um motivo de rejeição.
_Avoid_: falha, erro, cancelamento

**Motivo de rejeição**:
Justificativa dada pela Central de Convênios ao rejeitar uma operação de portabilidade.
_Avoid_: erro, falha
