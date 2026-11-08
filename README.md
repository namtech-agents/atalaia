# Atalaia

Um agente que junta todas as caixas de mensagem do governo de uma empresa (Receita, Justiça, Trabalho) num lugar só, explica o que chegou em linguagem simples e garante que alguém resolva antes do prazo.

## O problema

Toda empresa com CNPJ recebe comunicações oficiais em caixas eletrônicas: Receita Federal, Justiça, Ministério do Trabalho, Estado. Muitas vezes ninguém olha todas elas, e o prazo começa a contar mesmo que a mensagem não seja nem sequer aberta.

Um exemplo real: no TRT de Goiás, empresas levaram multas porque não confirmaram uma citação no Domicílio Judicial Eletrônico dentro do prazo (processo 0000732-75.2025.5.18.0002).

## O que o Atalaia faz

- Lê os documentos que chegam (PDF).
- Explica em linguagem simples o que a mensagem pede.
- Calcula o prazo, contando feriados e o recesso da Justiça.
- Destaca o que mais importa. Em meio a muitas páginas, o valor e o objetivo da mensagem ficam escondidos, e o Atalaia vai direto ao ponto.
- Manda o caso para a pessoa certa. Hoje, mensagens sobre processos chegam ao contador, que as ignora porque acha que é com o advogado, e as do RH chegam ao advogado, e vice-versa.
- Avisa de novo, e avisa ao dono, se ninguém resolver.
- Avisa e permite ao dono acompanhar o que está acontecendo no fluxo de sua empresa sobre questões governamentais, evitando deixar ele sem o controle da empresa.

## Por que é diferente

Já existem ferramentas que monitoram só as caixas fiscais (Receita, estados e prefeituras), como a Dootax e a Jettax, voltadas para o contador. O Atalaia junta também as caixas da Justiça e do Trabalho, explica o que chegou, calcula o prazo e manda o caso para a pessoa certa, até alguém resolver.

## Situação

O projeto está no **Marco 1 (visão e validação)**. Antes de escrever o sistema, quero confirmar que o problema existe do jeito que eu imagino. Por isso estou entrevistando contadores, advogados e donos de empresa de Teresina: quais caixas eles olham, com que frequência, se já perderam algum prazo e quanto isso custou.

O que já está pronto:
- Model Canvas e mapa de stakeholders ([docs/01-visao](docs/01-visao/))
- Roteiro e mensagens das entrevistas ([docs/02-validacao](docs/02-validacao/))
- Backlog com 24 histórias de usuário nas Issues, organizado no Kanban
- Projeto Spring Boot criado, com banco PostgreSQL

Próximos marcos:
- 12/11 · Marco 1: canvas, stakeholders, backlog e entrevistas
- 19/11 · Marco 2: ler um PDF, calcular o prazo, telas e diagramas
- 26/11 · Marco 3: o caminho completo, do PDF até o aviso por e-mail
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
- Playwright (o robô do e-CAC, se o teste mostrar que dá)

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
- Claude Code (IA): uso como professor e revisor. Ele me explica os conceitos, me dá dicas quando eu travo e revisa os meus pull requests.

O código Java eu escrevo à mão, e as decisões do projeto são minhas.

## Autora

Nicolly Adler, NAM Tech (Teresina, PI)
