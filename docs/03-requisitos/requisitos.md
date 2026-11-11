# Requisitos

O que o Atalaia precisa fazer na prévia de 03/12 e como ele precisa se comportar. Saiu do backlog (as histórias estão nas Issues) e do [roteiro de funções](../01-visao/roteiro-de-funcoes.md). Não tem função nova aqui, só a mesma lista organizada de outro jeito.

Separei do jeito que vimos na disciplina:

- normal: o que o contador pediria se eu perguntasse;
- esperado: o que ele nem fala, mas ficaria bravo se faltasse;
- fascinante: o que ele não espera e é onde o Atalaia se diferencia.

## Requisitos funcionais

### Normais

| # | O sistema deve | História |
|---|---|---|
| RF01 | Receber o PDF de uma notificação enviado pelo escritório e abrir um caso | US07 |
| RF02 | Tirar o texto do PDF | US09 |
| RF03 | Identificar o tipo do documento e separar os dados: quem mandou, pra qual empresa, o que pede, valor, datas | US10 |
| RF04 | Explicar o documento em português simples, com o original ao lado | US16 |
| RF05 | Mostrar a data limite de cada caso | US12 |
| RF06 | Deixar o escritório definir quem cuida de cada área (fiscal, pessoal, jurídico) | US02 |
| RF07 | Mandar o caso pro responsável certo | US13 |
| RF08 | Avisar o responsável e lembrar de novo antes do prazo | US14 |
| RF09 | Listar os casos por urgência | US03 |
| RF10 | Deixar abrir um caso e marcar como resolvido | US04 |

### Esperados

| # | O sistema deve | História |
|---|---|---|
| RF11 | Conferir cada dado extraído contra o texto do PDF e rejeitar o que não achar lá | US11 |
| RF12 | Não deixar uma empresa ver os dados de outra | US23 |
| RF13 | Continuar avisando mesmo se a IA estiver fora do ar | US21 |
| RF14 | Mandar o aviso por outro canal se o primeiro falhar | US24 |
| RF15 | Fazer backup todo dia, fora do servidor | US22 |

### Fascinantes

| # | O sistema deve | História |
|---|---|---|
| RF16 | Calcular o prazo por código, mostrando o artigo da lei e a data em que a regra foi conferida. A IA nunca calcula prazo | US12 |
| RF17 | Dizer "não sei" quando o documento tem dúvida ou conflito, e mandar o caso pra fila "precisa de gente" com o motivo | US25 |
| RF18 | Mandar ao cliente um link com o resumo, o prazo e os documentos pedidos, e gravar a confirmação dele com data e hora | US26 |
| RF19 | Abrir numa tela que mostra só o que precisa de alguém hoje, em vez de uma caixa de entrada | US03 |

### Se sobrar tempo, nesta ordem

| # | O sistema deve | História |
|---|---|---|
| RF20 | Guardar a trilha de cada caso de um jeito que não dá pra alterar sem aparecer | US27 |
| RF21 | Dizer em que degrau a notificação está: aviso, pedido de documento, intimação, auto de infração ou cobrança | US28 |
| RF22 | Testar o leitor com variações do mesmo documento | US29 |
| RF23 | Rodar um simulado: notificação de teste com cronômetro, pra ver quanto o escritório demora | US30 |
| RF24 | Conferir se o cliente entendeu o aviso, além de ter recebido | US31 |
| RF25 | Listar toda sexta os casos parados | US32 |

## Requisitos não funcionais

| # | Requisito | Como eu confiro |
|---|---|---|
| RNF01 | Nenhum prazo aparece sem passar pelas conferências. Se uma falhar, o caso fica sem prazo e vai pra uma pessoa | Teste com documentos que têm duas datas ou valor diferente do extenso |
| RNF02 | A leitura acerta pelo menos 9 em cada 10 documentos do lote de teste | Lote de notificações inventadas, medido antes do pitch |
| RNF03 | Na prévia só entra documento inventado, marcado como teste. Documento real só com contrato e autorização | Revisão do lote e do repositório |
| RNF04 | Repositório público só com dado inventado | Revisão em todo PR |
| RNF05 | LGPD: guardar só o necessário, registrar quem acessou, ter política de privacidade e canal de contato antes de ir ao ar | Lista legal da NAM Tech |
| RNF06 | Segurança: teste de invasão antes de ir ao ar, e nada crítico ou alto em aberto | Relatório do teste |
| RNF07 | Acessibilidade básica: contraste, teclado, foco visível e texto alternativo | Auditoria das telas |
| RNF08 | Toda mudança entra por PR, com os testes passando | Histórico do GitHub |
| RNF09 | Nenhuma regra de prazo ou multa entra no código sem ser lida na norma e conferida por contador ou advogado | Cada regra guarda o artigo e a data da conferência |

## O que o sistema não faz

- Não responde ao governo. Quem responde e assina é o contador.
- Não entra em portal do governo e não guarda certificado digital de cliente.
- Não calcula guia de pagamento nem parcelamento.
- Não sugere preço de serviço.

A lista completa do que foi descartado, e por quê, está no roteiro de funções.
