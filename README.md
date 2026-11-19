# Atalaia

Um agente para escritórios de contabilidade que pega a notificação do governo que chegou para um cliente, explica em linguagem simples, calcula o prazo por código, avisa o cliente guardando o registro e garante que alguém resolva antes do prazo.

## O problema

Toda empresa com CNPJ recebe comunicações oficiais em caixas eletrônicas: Receita Federal, Justiça, Ministério do Trabalho, Estado, prefeitura. Muitas vezes ninguém olha todas elas, e o prazo começa a contar mesmo que a mensagem não seja nem sequer aberta.

Já existem ferramentas que avisam o contador quando chega uma mensagem. O problema que sobra vem depois do aviso: entender o que o documento pede, saber o prazo certo, avisar o cliente com registro e não deixar o caso parado.

Um exemplo real: no TRT de Goiás, empresas levaram multas porque não confirmaram uma citação no Domicílio Judicial Eletrônico dentro do prazo (processo 0000732-75.2025.5.18.0002).

## O que o Atalaia faz

- Recebe o PDF da notificação, enviado pelo escritório. Na prévia não há robô entrando em portal nem certificado de cliente.
- Lê o documento e separa os dados, mostrando o trecho de onde tirou cada um.
- Explica em linguagem simples o que a mensagem pede.
- Calcula o prazo por código, com a regra ao lado. A IA nunca calcula prazo.
- Quando há dúvida ou conflito no documento, diz "não sei" e manda o caso para uma pessoa conferir.
- Manda ao cliente um link com o resumo, o prazo e os documentos pedidos. A confirmação dele fica gravada.
- Destaca o que mais importa. Em meio a muitas páginas, o valor e o objetivo da mensagem ficam escondidos, e o Atalaia vai direto ao ponto.
- Manda o caso para a pessoa certa. Hoje, mensagens sobre processos chegam ao contador, que as ignora porque acha que é com o advogado, e as do RH chegam ao advogado, e vice-versa.
- Avisa de novo, e avisa ao dono, se ninguém resolver.
- Avisa e permite ao dono acompanhar o que está acontecendo no fluxo de sua empresa sobre questões governamentais, evitando deixar ele sem o controle da empresa.

## Por que é diferente

Monitorar as caixas do governo já tem dono. Há ferramentas para a caixa da Receita (Dootax, Jettax, e-Auditoria) e para a trabalhista (Agilizza, Alerte), todas voltadas a avisar o contador. O Atalaia não disputa esse aviso: ele começa onde elas param.

A pesquisa de concorrentes de 10/11 olhou o Brasil e mais 16 países. Tratar notificações do governo para escritórios contábeis existe em quase todo país, e cada país acertou uma parte diferente. Nenhum dos que achamos junta todas as partes. O Atalaia quer ser essa junção, com três cuidados:

- o prazo é calculado por código e o contador sempre confere;
- o sistema prefere dizer "não sei" a mostrar uma data errada;
- o caso termina no cliente, com o registro de que ele foi avisado.

O Atalaia não responde ao governo. Quem responde é o contador.

A lista do que entra na prévia, do que vem depois e do que foi descartado está no [roteiro de funções](docs/01-visao/roteiro-de-funcoes.md).

## Situação

O projeto está no **Marco 2 (protótipo e arquitetura)**. O que já funciona: criar a conta do escritório e entrar com e-mail e senha, cadastrar as empresas clientes (cada escritório só vê as suas), enviar um PDF, tirar o texto dele, classificar e extrair os dados com IA (Analista), conferir cada dado contra o texto, mostrar tudo na tela, listar os casos em grupos pelo próximo passo (precisa de gente, esperando o cliente, pronto para fechar), com filtro por cliente e a contagem de casos de cada cliente no período, e marcar um caso como resolvido, com o desfecho. Cada caso guarda o histórico de quem fez o quê e quando. O prazo já aparece na tela: o escritório cadastra as regras que ele mesmo conferiu na norma (o Atalaia não traz regra pronta), informa o dia da ciência, e a data limite é calculada por código, com a conta passo a passo e o artigo ao lado. A lista mostra primeiro o prazo mais perto. O caminho já fecha: o escritório aprova um resumo simples (rascunhado pela IA), gera um link para o cliente, e a confirmação dele fica gravada como registro de ciência, com uma pergunta que confere se ele entendeu a data. O que falta até 03/12: colocar no ar. Aviso por e-mail e responsável por área ficaram para depois.

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
