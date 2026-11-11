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

O projeto está no **Marco 1 (visão e validação)**. Antes de escrever o sistema, quero confirmar que o problema existe do jeito que eu imagino. Por isso estou procurando contadores, advogados e donos de empresa de Teresina: quais caixas eles olham, com que frequência, se já perderam algum prazo e quanto isso custou.

Em 10/11 a pesquisa de concorrentes mostrou que só monitorar as caixas já existe. Em 11/11 o rumo mudou para o que vem depois do aviso, e o Canvas, o backlog e este README foram ajustados.

O que já está pronto:
- Model Canvas, mapa de stakeholders, pré-mortem e roteiro de funções ([docs/01-visao](docs/01-visao/))
- Roteiro e mensagens das entrevistas ([docs/02-validacao](docs/02-validacao/))
- Backlog com 36 histórias de usuário nas Issues, organizado no Kanban
- Projeto Spring Boot criado, com banco PostgreSQL

Próximos marcos:
- 12/11 · Marco 1: canvas, stakeholders, backlog e entrevistas
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

### Vão entrar:
- PDFBox (Ler PDF)
- LangChain4j (a IA, Claude ou Gemini)
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

## Como trabalho 

- Scrum adaptado para uma pessoa só, com uma entrega a cada marco do projeto.
- Sprints de uma semana, uma por marco.
- O backlog com prioridade P1/P2/P3 e o marco de cada história, mais o Kanban.
- "Validar antes de construir" (as entrevistas mudam o Canvas e o backlog) e uma revisão toda sexta.
- O caminho de uma tarefa em 5 passos: Issue → branch → código e teste → PR com "Resolve #N" → merge e card em "Feito".

## Ferramentas usadas

- VS Code: editor, com as extensões de Java e Spring
- start.spring.io: onde gerei o projeto
- Postgres.app: o PostgreSQL no meu Mac
- GitHub: código, Issues, Kanban e pull requests
- Claude Code (IA): uso como professor e revisor. Ele me explica os conceitos, me dá dicas quando eu travo e revisa os meus pull requests. Também me ajuda na pesquisa de concorrentes e na redação dos documentos e das histórias do backlog.

O código Java eu escrevo à mão, e as decisões do projeto são minhas.

## Autora

Nicolly Adler, NAM Tech (Teresina, PI)
