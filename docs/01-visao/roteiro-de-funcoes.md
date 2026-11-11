# Roteiro de funções · Atalaia (11/11/2026)

O que entra na prévia de 03/12, o que vem logo depois e o que fica para longe. Saiu da pesquisa de concorrentes de 10/11 (Brasil e mais 16 países) e da análise de 11/11.

## A régua

Monitorar as caixas do governo já tem dono: no Brasil há várias ferramentas que avisam o contador quando chega mensagem. O Atalaia começa onde elas param, que é depois do aviso.

Cada função daqui só pode usar três fontes:

1. o PDF que o escritório envia;
2. o que o próprio Atalaia vai guardando, caso a caso;
3. o que é público.

Nada depende de ERP, de sistema de outra empresa nem de robô entrando em portal do governo.

Sobre concorrentes, tudo aqui é "não achamos na pesquisa", nunca "não existe".

## Prévia de 03/12

### Núcleo

| # | Função | O que entrega | História |
|---|---|---|---|
| 1 | Entrada por PDF | O caso nasce de um arquivo enviado, sem robô nem certificado | US07, US09 |
| 2 | Leitura com trecho conferido | Explicação simples, documentos pedidos e valor em jogo, cada dado com o trecho de onde saiu | US10, US11, US16 |
| 3 | Prazo por código | A data limite calculada por regra, com o artigo ao lado | US12 |
| 4 | Segunda passada e fila "precisa de gente" | O sistema sabe dizer "não sei" | US25 |
| 5 | Um toque do cliente | Link com resumo, prazo, documentos e confirmação gravada | US26 |
| 6 | Tarefa com dono | Cada caso tem responsável e data | US02, US04, US13, US14 |
| 7 | Tela inicial limpa | "O que precisa de você hoje", por urgência | US03 |

### Reforços, nesta ordem, se sobrar tempo

| # | Função | História |
|---|---|---|
| 1 | Trilha encadeada (registro que não dá para alterar sem aparecer) | US27 |
| 2 | Degrau (aviso, pedido de documento, intimação, auto de infração ou cobrança) | US28 |
| 3 | Teste por variação do leitor | US29 |
| 4 | Simulado (notificação de teste com cronômetro) | US30 |
| 5 | Prova de que o cliente entendeu | US31 |
| 6 | Faxina de sexta (casos parados) | US32 |

## Como a IA não erra calada

Seis barreiras em sequência. A IA só participa de duas.

| # | Barreira | Quem faz |
|---|---|---|
| 1 | A IA extrai os dados e devolve o trecho literal de cada um | IA |
| 2 | O código procura cada trecho no texto do PDF; se não achar, rejeita o dado | Código |
| 3 | Conferências de formato: CNPJ, datas, valor em número igual ao por extenso | Código |
| 4 | Segunda passada: falta algo ou há conflito? | IA |
| 5 | Prazo calculado por código, com o artigo e a data em que a regra foi conferida | Código |
| 6 | O contador vê data, artigo e trecho lado a lado e confirma | Pessoa |

Se qualquer barreira de 2 a 5 falhar, o caso não ganha prazo: vai para a fila "precisa de gente", com o motivo e o PDF original.

A prévia usa só notificações inventadas, marcadas como documento de teste. Nenhum documento real vai para a IA nesta fase.

## Logo depois da prévia

1. Nota Sintonia da carteira: guardar a classificação de cada cliente a cada trimestre e mostrar quem caiu (US33)
2. Relatório mensal ao cliente (US34)
3. Endereço de e-mail de entrada, para encaminhar avisos e fotos de papel (US35)
4. Separar o que é notificação do resto, nesse endereço
5. Lote: responder uma vez, valer para vários casos iguais (US36)
6. Contagem de casos por cliente, para o escritório rever a cobrança
7. Anexar provas ao caso
8. Ronda periódica com o cliente: "chegou algum papel do governo?"
9. Registro de quase-perda de prazo
10. Lista de "caixa configurada?" por cliente
11. Relógio do desconto, com uma regra
12. Sinais de golpe no documento recebido
13. Dossiê de passagem de cliente
14. Raio-X do cliente novo
15. Rascunho de resposta para o contador revisar e assinar (US17)
16. Validade das regras de prazo ("conferida em tal dia, reconferir em seis meses")
17. Alerta ao supervisor quando o caso para (US15)

## Longo prazo

1. Radar de onda entre clientes (só em carteira grande)
2. Memória do escritório, com os casos do próprio Atalaia
3. Recall de respostas
4. Placar de desfecho
5. Espelho e simulador da nota Sintonia
6. Planos do escritório, com fiscalização inclusa (só com advogado)
7. Nota do cliente dada pelo escritório
8. Certidão do escritório com código de conferência
9. Tabela de multas estruturada
10. Conferência da assinatura digital do documento
11. Selo de estabilidade por documento
12. Base aberta de notificações de teste
13. Vigia automático das regras
14. Acesso direto às caixas por via oficial e sentinela (US01, US05, US06, US08, US19, US20)
15. Onda e estatística entre escritórios, sem expor dado
16. Morar dentro do ERP que o escritório já usa
17. Guardadas e travadas: malha antes da malha, termômetro do setor, operação de portal por via oficial, ajuda com tela aberta

## Descartado

- Resposta ao governo direto da IA. O Atalaia não tem botão de enviar ao governo; o contador sempre assina.
- Pasta pronta (depende do sistema dos outros).
- Robô entrando em portal com a identidade de outra pessoa, e guardar certificado de cliente.
- Cadastrar um e-mail por cliente em cada portal.
- Gerar guia de pagamento e calcular parcelamento.
- Carga tributária no relatório ao cliente.
- Ler a caixa de e-mail ou o WhatsApp do escritório.
- Robô de voz ligando para o cliente.
- Preço sugerido pela IA.

## Travas

- Nenhuma regra de prazo ou multa entra em código sem ser lida na norma e conferida por contador ou advogado.
- Documento real de cliente só vai para a IA com contrato e autorização.
- A confirmação do cliente é chamada de "registro de ciência" até um advogado dizer o que ela vale como prova.
- Repositório público só com dado inventado.
