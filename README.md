<p align="center">
  <img src="docs/atalaia.webp" width="100%" alt="Pixel art: a sombra do relógio de sol do Atalaia cresce e gira como ponteiro até a marca vermelha do prazo acender">
</p>

# Atalaia

> Multa é coisa de quem não tem Atalaia.

Atalaia é a torre de vigia, o posto de onde alguém avisa antes do perigo chegar. Aqui, é um agente de IA para escritórios de contabilidade que cuida do que vem depois de uma notificação oficial chegar. É o primeiro produto da [Galad](https://github.com/somosgalad).

## O problema

Chega uma notificação do governo para um cliente do escritório, e daí em diante é tudo na mão: entender o que o documento pede no meio de várias páginas, descobrir o prazo certo, mandar para quem resolve (contador, advogado ou RH), avisar o cliente de um jeito que fique registrado e acompanhar até fechar. Qualquer passo esquecido vira multa.

E o prazo não espera ninguém abrir a mensagem:

- No e-CAC, a intimação conta como feita 15 dias depois de chegar na caixa postal, mesmo sem ninguém abrir (Decreto 70.235/72, art. 23).
- No Domicílio Judicial Eletrônico, são 3 dias úteis para confirmar a citação, com multa de até 5% do valor da causa para quem não confirma (CPC, art. 246).

Um exemplo real: no TRT de Goiás, empresas levaram multas porque não confirmaram uma citação no Domicílio Judicial Eletrônico dentro do prazo (processo 0000732-75.2025.5.18.0002).

## O que o Atalaia faz

- Recebe o PDF da notificação, enviado pelo escritório, lê e mostra de que trecho tirou cada dado.
- Explica em linguagem simples o que a mensagem pede e destaca valor, objetivo e prazo.
- Calcula o prazo por código, com a regra ao lado. A IA nunca calcula prazo.
- Na dúvida, diz "não sei" e manda o caso para uma pessoa conferir.
- Envia ao cliente um link com o resumo, o prazo e os documentos pedidos, e guarda a confirmação dele como registro de ciência.
- Organiza os casos pelo próximo passo, com o prazo mais perto primeiro.
- Registra quem fez o quê e quando, numa trilha que sai em PDF.

## O que ele não faz

- Não responde ao governo. Quem responde é o contador.
- Não entra sozinho nos portais do governo nem usa o certificado do cliente.
- Não avisa por WhatsApp nem Telegram.

## Por que é diferente

Monitorar as caixas do governo já tem dono. Há ferramentas para a caixa da Receita (Dootax, Jettax, e-Auditoria) e para a trabalhista (Agilizza, Alerte), todas voltadas a avisar o contador. O Atalaia não disputa esse aviso: ele começa onde elas param.

A pesquisa de concorrentes de 10/11 olhou o Brasil e mais 16 países. Tratar notificações do governo para escritórios contábeis existe em quase todo país, e cada país acertou uma parte diferente. Nenhum dos que achamos junta todas as partes. O Atalaia quer ser essa junção, com três cuidados:

- o prazo é calculado por código e o contador sempre confere;
- o sistema prefere dizer "não sei" a mostrar uma data errada;
- o caso termina no cliente, com o registro de que ele foi avisado.

A lista do que entra na prévia, do que vem depois e do que foi descartado está no [roteiro de funções](docs/01-visao/roteiro-de-funcoes.md).

## Situação

O projeto está no **Marco 2 (protótipo e arquitetura)**. O que já funciona: criar a conta do escritório e entrar com e-mail e senha, cadastrar as empresas clientes (cada escritório só vê as suas), enviar um PDF, tirar o texto dele, classificar e extrair os dados com IA (Analista), conferir cada dado contra o texto, perguntar de novo à IA se falta algo ou há conflito no documento (com dúvida, o caso vai para uma pessoa com o trecho), mostrar o degrau da notificação (de aviso a cobrança) e o que costuma vir em seguida, com correção em um clique, mostrar tudo na tela, listar os casos em grupos pelo próximo passo (precisa de gente, esperando o cliente, pronto para fechar), com filtro por cliente e a contagem de casos de cada cliente no período, e marcar um caso como resolvido, com o desfecho. Uma vez por semana, a faxina mostra os casos abertos sem movimento há mais de 7 dias. O dono pode soltar um simulado (notificação de teste) e ver quanto tempo a equipe levou para abrir, avisar o cliente e fechar. Cada caso guarda o histórico de quem fez o quê e quando, numa trilha encadeada: mexer num registro antigo aparece na conferência, e a trilha sai em PDF. O prazo já aparece na tela: o escritório cadastra as regras que ele mesmo conferiu na norma (o Atalaia não traz regra pronta), informa o dia da ciência, e a data limite é calculada por código, com a conta passo a passo e o artigo ao lado. A lista mostra primeiro o prazo mais perto. O caminho já fecha: o escritório aprova um resumo simples (rascunhado pela IA), gera um link para o cliente, e a confirmação dele fica gravada como registro de ciência, com uma pergunta que confere se ele entendeu a data. O que falta até 03/12: colocar no ar. Aviso por e-mail e responsável por área ficaram para depois.

Em 10/11 a pesquisa de concorrentes mostrou que só monitorar as caixas já existe. Em 11/11 o rumo mudou para o que vem depois do aviso, e o Canvas, o backlog e este README foram ajustados.

As entrevistas com contadores ficaram para depois do protótipo. É mais fácil marcar a conversa tendo alguma coisa pra mostrar, e a resposta vem melhor quando a pessoa vê funcionando. O roteiro já está pronto.

O que já está pronto:
- Model Canvas, mapa de stakeholders, pré-mortem e roteiro de funções ([docs/01-visao](docs/01-visao/))
- Roteiro e mensagens das entrevistas ([docs/02-validacao](docs/02-validacao/))
- Requisitos funcionais e não funcionais ([docs/03-requisitos](docs/03-requisitos/requisitos.md))
- Backlog com 36 histórias de usuário nas Issues, organizado no Kanban
- Projeto Spring Boot criado, com banco PostgreSQL e as tabelas criadas pelo Flyway
- Leitor de PDF, Analista, Verificador e Calculadora de prazo, cada um com os seus testes
- Um teste do caminho inteiro (`CaminhoInteiroTest`): do PDF enviado ao caso resolvido, pela tela
- Testes por variação: a mesma notificação inventada com outra data, outra ordem ou outro CNPJ dá o mesmo resultado
- Login com cadastro do escritório, com teste de que um escritório não vê os dados de outro
- Aviso ao cliente por link, com resumo rascunhado pela IA e aprovado pelo escritório, pergunta de conferência e registro de ciência; a lista inicial mostra quem ainda não confirmou
- Arquitetura e diagramas UML ([docs/04-arquitetura](docs/04-arquitetura/arquitetura.md))

Próximos marcos:
- 12/11 · Marco 1: canvas, stakeholders, pré-mortem, roteiro de funções e backlog
- 19/11 · Marco 2: ler um PDF, conferir os dados, calcular o prazo, telas e diagramas
- 26/11 · Marco 3: o caminho completo, do PDF até a confirmação do cliente
- 03/12 · Marco 4: pitch e demonstração

## Tecnologias

### Já no projeto: 
- Java 21
- Spring Boot 4 (web, JPA, validação, Thymeleaf, Actuator)
- PostgreSQL
- H2 nos testes
- Maven
- JUnit (testes)
- Git e GitHub com o Projects
- PDFBox (ler PDF)
- Flyway (criar e mudar as tabelas do banco)
- LangChain4j com o Gemini (a IA que classifica e extrai os dados)
- Spring Security (login, sessão e a separação entre escritórios)

### Vão entrar:
- Spring Mail e Telegram (os avisos)
- GitHub Actions (Testes automáticos)
- Docker com um servidor VPS (colocar no ar)

## Como rodar

Precisa do Java 21 e do PostgreSQL. O Maven não precisa instalar, o mvnw já baixa sozinho.

Baixar o projeto:

    git clone https://github.com/namtech-agents/atalaia.git
    cd atalaia

Rodar os testes (funcionam sem o PostgreSQL, porque usam o H2 em memória):

    ./mvnw test

Criar o banco:

    createdb atalaia

Subir o sistema:

    ./mvnw spring-boot:run

Para ver se subiu, abra http://localhost:8080/actuator/health. Tem que aparecer "status":"UP".

O endereço, o usuário e a senha do banco podem vir das variáveis `ATALAIA_DB_URL`, `ATALAIA_DB_USUARIO` e `ATALAIA_DB_SENHA`.

No ar (com HTTPS e um proxy na frente), defina também:

| Variável | Para quê |
|---|---|
| `ATALAIA_ENDERECO` | o endereço público, usado no link do aviso ao cliente |
| `ATALAIA_COOKIE_SEGURO=true` | o cookie da sessão só viaja por HTTPS |
| `ATALAIA_PROXY=native` | o Atalaia lê do proxy o endereço real de quem pediu (trava de tentativas) e que veio por HTTPS (liga o HSTS) |
| `ATALAIA_PROXY_IP` | o IP do proxy, como expressão regular (ex.: `172\.18\.0\.5`). Sem ela, só um proxy na mesma máquina é aceito. A porta do Atalaia fica aberta só para o proxy |
| `GEMINI_API_KEY` | a chave da IA. Sem ela, tudo vai para revisão |

## Como trabalho 

- Scrum adaptado para um time pequeno, com uma entrega a cada marco do projeto.
- Sprints de uma semana, uma por marco.
- O backlog com prioridade P1/P2/P3 e o marco de cada história, mais o Kanban.
- Pesquisar antes de construir (a pesquisa de concorrentes já mudou o Canvas e o backlog uma vez, e as entrevistas podem mudar de novo) e uma revisão toda sexta.
- O caminho de uma tarefa em 5 passos: Issue → branch → código e teste → PR com "Resolve #N" → merge e card em "Feito". O combinado completo está no [CONTRIBUTING.md](CONTRIBUTING.md).

## Ferramentas usadas

- VS Code: editor, com as extensões de Java e Spring
- start.spring.io: onde gerei o projeto
- Postgres.app: o PostgreSQL no meu Mac
- GitHub: código, Issues, Kanban e pull requests
- Claude Code (IA): uso como professor e revisor. Ele me explica os conceitos, me dá dicas quando eu travo e revisa os meus pull requests. Também me ajuda na pesquisa de concorrentes e na redação dos documentos e das histórias do backlog.

O código Java é escrito à mão pelo time, e as decisões do projeto são nossas.

## Time

- Nicolly Adler, NAM Tech (Teresina, PI): produto, backlog e código
- Itallo Sampaio ([itallo-dev-code](https://github.com/itallo-dev-code)): banco de dados
