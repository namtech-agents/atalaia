# Pré-mortem · Atalaia (10/11/2026)

Exercício de risco: imaginar que o projeto já deu errado e perguntar por quê, antes de acontecer.

**A cena:** hoje é 07/01/2027. O Atalaia fracassou: a prévia não ficou pronta para o pitch de 03/12 e ninguém se interessou em ver.

Foram levantados 13 motivos possíveis (validação, acesso às caixas, técnica, tempo e custo). Abaixo estão os 3 mais prováveis, cada um com o sinal de alerta, o que fazer para evitar e o plano B.

## Os 3 riscos mais prováveis

### 1. Poucas entrevistas de validação
- **Sinal:** quarta, 11/11, com menos de 3 conversas marcadas.
- **Evitar:** enviar as mensagens aos 20 contatos da lista pedindo só 15 minutos de conversa, e pedir uma indicação no fim de cada uma.
- **Plano B:** mais 10 escritórios de Teresina por Instagram e WhatsApp comercial. Se chegar em 12/11 com 4 entrevistas, seguir com 4 e registrar o resultado como está.

### 2. Sem acesso direto às caixas
- **Sinal:** o certificado e o custo do Integra Contador (Serpro) continuam sem resposta, ou a caixa que mais dá trabalho não tem API oficial.
- **Evitar:** não prometer acesso automático. A pesquisa de 10/11 mostrou que a API oficial do e-CAC responde no ambiente de demonstração, mas em produção exige certificado e procuração, e não cobre a caixa trabalhista (DET) nem a da Justiça.
- **Plano B:** a prévia funciona pela Recepção: a pessoa envia os PDFs e o Atalaia lê, calcula o prazo e avisa, seja qual for a caixa de origem. O acesso direto fica para 2027.

### 3. Atraso no cronograma
- **Sinal:** dois dias seguidos sem terminar a peça do dia, ou um marco chegando sem a entrega prevista.
- **Evitar:** seguir o cronograma, uma peça por dia, estudando só o que a peça da semana precisa.
- **Plano B:** aplicar a ordem de corte. Nunca saem: Calculador de Prazo com testes, Verificador, revisão humana, histórico, login e segurança.

## Outros riscos acompanhados

| Risco | Resposta |
|---|---|
| Escritórios dizerem que já monitoram as caixas | Perguntar por Justiça e Trabalho, não só Receita |
| Receio de dar procuração | Recepção (envio manual dos PDFs), sem procuração |
| A IA errar um prazo | O prazo nunca vem da IA: é calculado por código, com testes, e um humano aprova antes do aviso |
| O foco estar na caixa errada | Perguntar em toda entrevista qual caixa dá mais trabalho (DET, malha fiscal, e-CAC, Domicílio Judicial) e ajustar a ordem do que o Atalaia cobre |
| Abrir a mensagem pela API contar como ciência e fazer o prazo começar | Avisar só com o indicador e a lista de mensagens; o texto completo só é aberto com autorização do cliente |
| Custo de IA acima do esperado | Cada documento é analisado uma só vez, com limite diário; medir o custo por documento no lote de 20 |

## Decisão que saiu daqui

Em 2026 fica pronta a prévia, validada com conversas e, se possível, com documentos antigos enviados por quem se interessar. Acesso por procuração e cobrança ficam para 2027, depois de a pessoa ver o Atalaia funcionando.
