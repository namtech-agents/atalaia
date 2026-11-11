# Model Canvas

Atalaia em uma frase: os outros avisam o contador que chegou uma mensagem do governo. O Atalaia pega daí: explica, calcula o prazo, avisa o cliente guardando o registro e não deixa o caso parar.

## Segmentos de clientes
Pra quem é?

Quem paga: escritórios de contabilidade, porque cuidam de muitos CNPJs e são eles que respondem quando um prazo passa. Começo por Teresina.

Quem usa no dia a dia: a equipe fiscal e de departamento pessoal do escritório.

Quem recebe o aviso: a empresa cliente do escritório, pelo link.

Empresa contratando direto e MEI ficam de fora por enquanto.

## Proposta de valor
Que problema resolve e por que é melhor? Qual o diferencial?

O trabalho que o contador quer ver resolvido: "Quando chega uma fiscalização de um cliente e eu descubro em cima do prazo, quero saber em minutos o que fazer, ter prova de que avisei e ser pago por isso, para não bancar a multa do meu bolso nem trabalhar de graça."

Tratar notificações do governo para escritórios contábeis é um produto que existe em quase todo país, e cada país acertou uma parte diferente. Nenhum dos que achamos na pesquisa junta todas as partes. O Atalaia pode ser essa junção.

O que ele melhora no que já existe:

| Hoje, com as ferramentas que existem | Com o Atalaia |
|---|---|
| O contador recebe o aviso de que chegou algo e o resto é na mão | O aviso já vira um caso com explicação simples, documentos pedidos, valor em jogo e prazo |
| Quando tem IA, ela lê e diz o prazo sozinha | A IA só separa os dados e mostra o trecho de onde tirou. O prazo é calculado por código, com a regra ao lado |
| Se o documento tem erro ou dúvida, ninguém percebe | O Atalaia diz "não sei" e manda pra uma pessoa conferir |
| O aviso para no contador | O cliente recebe um link com o resumo, o prazo e o que separar, e a confirmação dele fica gravada |
| Mais uma caixa de entrada cheia de alerta | Uma tela limpa: só o que precisa de você hoje |

Os dois medos de quem vai contratar:

- E se a IA cair? Ele continua avisando, porque a IA é pra explicar em melhor linguagem. Quem faz a maior parte do serviço é o código.
- E se a IA errar o prazo? Ela não calcula prazo. O prazo é calculado por código, com o trecho do documento ao lado; o contador sempre assina.

O que o Atalaia não faz: não responde ao governo no lugar do contador e não entra em portal com a senha de ninguém.

## Canais
Como o cliente fica sabendo e recebe o serviço?

Pra conhecer: contato direto (Instagram, Google Maps, LinkedIn) e indicação. Na conversa eu mostro funcionando: solto uma notificação de teste e o contador vê o caso pronto em minutos.

Pra usar: painel, e-mail, Telegram e o link que vai pro cliente. WhatsApp mais pra frente, só pelo caminho oficial.

A ideia é não ser mais um sistema pro contador abrir: entra por PDF ou e-mail e, mais pra frente, se houver parceria, mora dentro do ERP que o escritório já usa.

## Relacionamento com clientes
Como você trata o cliente?

A primeira notificação é lida junto com o escritório, assim ele acompanha o fluxo que corre por trás e confia no que está contratando. Depois: lembretes automáticos e suporte direto comigo.

Mais pra frente, um relatório mensal que o escritório manda pro cliente dele, mostrando o que chegou, o que foi tratado e o que foi evitado. É o cuidado que hoje ninguém vê.

## Fontes de receita
Como entra dinheiro?

Assinatura mensal por CNPJ acompanhado, paga pelo escritório, com preço menor por CNPJ conforme a carteira cresce. Valor a definir: as ferramentas de monitoramento que achei cobram de R$ 29,90 por CNPJ a R$ 269 no plano básico, e isso serve de referência.

Em 2026 não tem cobrança: primeiro a prévia funcionando, cobrança só em 2027.

Por que o escritório pagaria: o Atalaia ajuda a cobrar o que hoje sai de graça. O cliente confirma o serviço extra no link antes do trabalho começar, e a contagem de casos por cliente dá base pra rever a mensalidade.

## Atividades principais
O que o Atalaia faz todo dia?

Recebe o PDF que o escritório envia, lê, confere os dados com o texto original, calcula o prazo, resume em linguagem simples, manda o caso pra pessoa certa, avisa o cliente e cobra de novo se ninguém fizer nada.

Por trás: manter as regras de prazo conferidas na lei e testar a leitura com notificações inventadas, pra provar que ela acerta.

## Recursos principais
O que você (eu) precisa ter?

As regras de prazo em código, cada uma com o artigo e a data em que foi conferida. As notificações de teste. Os agentes de IA. O registro de cada passo do caso. E o meu time, pequeno por enquanto.

O certificado digital da NAM Tech só entra quando houver acesso direto ao e-CAC, pela API oficial do Serpro.

## Parcerias principais
Quem ajuda de fora?

- Um escritório contábil parceiro, pra testar a prévia com casos de verdade.
- Um contador ou advogado que confere cada regra de prazo antes de virar código.
- Um advogado pra termos de uso e LGPD.
- O provedor de IA.
- Mais pra frente: o Serpro (acesso oficial à caixa da Receita) e o dono de um ERP contábil.

## Estrutura de custos
Onde gasta?

API de IA (menos de R$ 1 por cliente por mês, a confirmar no lote de teste), hospedagem básica e envio de e-mail. As horas de quem confere as regras de prazo.

Só quando entrar o acesso direto: certificado digital e consultas à API do Serpro, que é paga por consulta.

---

O que entra na prévia, o que vem depois (nota Sintonia da carteira, relatório mensal, lote de casos iguais) e o que foi descartado está no [roteiro de funções](roteiro-de-funcoes.md).
