# Model Canvas

Os outros avisam o contador que chegou mensagem do governo. O Atalaia cuida do depois.

## Parcerias principais
Quem ajuda de fora?

Um escritório contábil pra testar a prévia com caso de verdade (escritório também traz cliente e pode ser o cliente, função parceira mútua). Um contador ou advogado que confere cada regra de prazo antes de eu colocar no código, porque regra tirada de blog não entra. Um advogado pros termos de uso e pra LGPD. Mais pra frente o Serpro, que tem a API oficial da caixa da Receita, e quem sabe o dono de um ERP contábil.

## Atividades principais
O que o Atalaia faz todo dia?

Recebe o PDF da notificação que o escritório envia. Lê, separa os dados e confere cada um com o texto original. Calcula o prazo. Resume de forma mais fácil e prática (o original fica do lado pra quem preferir). Manda o caso pra pessoa certa, manda um link pro cliente e cobra de novo se ninguém fizer nada.

Quando o documento tem alguma coisa estranha, tipo duas datas diferentes, ele não chuta: diz "não sei" e passa pra uma pessoa olhar.

## Recursos principais
O que você (eu) precisa ter?

As regras de prazo escritas em código, cada uma com o artigo da lei e a data em que foi conferida. Um lote de notificações de teste, inventadas, pra provar que a leitura acerta. Os agentes de IA. O registro de cada passo do caso. E o meu time, que ainda é pequeno.

O certificado digital da NAM Tech só entra quando tiver acesso direto ao e-CAC, pela API oficial do Serpro.

## Proposta de valor
Que problema resolve e por que é melhor? Qual o diferencial?

Eu descrevi o que o contador quer assim: "Quando chega uma fiscalização de um cliente e eu descubro em cima do prazo, quero saber em minutos o que fazer, ter prova de que avisei e ser pago por isso, para não bancar a multa do meu bolso nem trabalhar de graça."

Monitorar caixa do governo já existe, e bastante. Na pesquisa de concorrentes olhei o Brasil e mais de 15 países. Tratar notificações do governo para escritórios contábeis é um produto que existe em quase todo país, e cada país acertou uma parte diferente. Nenhum dos que achei junta todas as partes. O Atalaia pode ser essa junção.

Onde ele melhora o que já tem:

A ferramenta de hoje avisa que chegou mensagem e o resto é na mão. No Atalaia o aviso já chega como um caso montado: o que o documento pede, quais papéis separar, quanto dinheiro está em jogo e até quando.

Quando a ferramenta tem IA, é a IA que diz o prazo. Aqui a IA só separa os dados e mostra o trecho de onde tirou cada um. Quem faz a conta é o código, com a regra do lado.

Hoje o aviso para no contador. No Atalaia o cliente recebe um link com o resumo e o que ele precisa mandar, confirma que foi avisado, e isso fica gravado com data e hora.

E a tela não é mais uma caixa de entrada cheia de alerta. Ela mostra só o que precisa de alguém hoje.

Tá, mas e se a IA cair? Ele continua avisando, porque a IA é pra revisar e explicar em melhor linguagem, quem faz a maior parte do serviço é o código.

E se a IA errar o prazo? Ela não calcula prazo. O prazo é calculado por código, com o trecho do documento ao lado; o contador sempre assina.

O Atalaia não responde ao governo no lugar do contador e não entra em portal com a senha de ninguém.

## Relacionamento com clientes
Como você trata o cliente?

A primeira notificação é lida junto com o escritório, assim ele acompanha o fluxo que corre por trás e sabe no que está investindo, sem ficar perdido. Lembretes automáticos. Suporte direto com a equipe (eu).

Mais pra frente, um relatório por mês que o escritório manda pro cliente dele, com o que chegou e o que foi resolvido. É um cuidado que o escritório já tem hoje e ninguém vê.

## Canais
Como o cliente fica sabendo e recebe o serviço?

Pra conhecer: contato direto (Instagram, Google Maps, LinkedIn) e indicação também, questão do networking. Na conversa eu mostro funcionando: solto uma notificação de teste e o contador vê o caso pronto.

Pra usar: painel, e-mail, telegram e o link que vai pro cliente. Mais pra frente o whatsapp, quem sabe né.

A ideia é não ser mais um sistema pro contador abrir: entra por PDF ou e-mail e, mais pra frente, se houver parceria, mora dentro do ERP que o escritório já usa.

## Segmentos de clientes
Pra quem é?

Escritórios de contabilidade (porque eles cuidam de muitos CNPJs, e são eles que respondem quando um prazo passa). Começo por Teresina. Quem usa no dia a dia é a equipe fiscal e de pessoal do escritório, e quem recebe o aviso é a empresa cliente dele.

Empresa contratando direto e MEI ficam de fora (por enquanto).

## Estrutura de custos
Onde gasta?

API de IA (menos de R$ 1 por cliente por mês, ainda vou medir no lote de teste), hospedagem (básico), envio de e-mail e as horas de quem confere as regras de prazo.

Certificado digital e consultas à API do Serpro, que é paga por consulta, só quando entrar o acesso direto.

## Fontes de receita
Como entra dinheiro?

Assinatura mensal por CNPJ acompanhado, paga pelo escritório (valor a definir); quanto mais CNPJs, menos por CNPJ. Pra ter uma noção de faixa, as ferramentas de monitoramento que achei cobram de R$ 29,90 por CNPJ a R$ 269 no plano básico.

Em 2026 não tem cobrança: primeiro a prévia funcionando, cobrança só em 2027.

O escritório pagaria porque o Atalaia ajuda a cobrar um trabalho que hoje sai de graça: o cliente confirma o serviço extra no link antes de o trabalho começar.

Lembrete: preço ainda não testado, depende do que os contadores disserem.

---

O que entra na prévia, o que vem depois e o que foi descartado está no [roteiro de funções](roteiro-de-funcoes.md).
