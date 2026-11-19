# Arquitetura e diagramas · Atalaia (Marco 2, 18/11/2026, já com o login e o prazo na tela)

Como o Atalaia é montado por dentro e como as peças conversam. Os desenhos são feitos em Mermaid, que o GitHub desenha sozinho.

Em cada diagrama separei o que **já existe no código** do que está **planejado**. Planejado aparece tracejado ou com a marca "(planejado)".

## A ideia em uma frase

O PDF passa por uma fila de peças pequenas. Cada peça faz uma coisa só e sabe dizer "precisa de gente". A IA participa de duas peças e nunca calcula prazo.

## 1. Casos de uso

Quem usa o Atalaia e para quê. A IA aparece como ator, porque é um serviço de fora que o sistema chama.

```mermaid
flowchart LR
    contador(["Contador ou equipe do escritório"])
    responsavel(["Responsável pela área"])
    dono(["Dono do escritório"])
    cliente(["Cliente (dono da empresa)"])
    ia(["IA (serviço de fora)"])

    subgraph Atalaia
        conta["Criar a conta do escritório e entrar"]
        empresas["Cadastrar as empresas clientes"]
        regras["Cadastrar as regras de prazo que o escritório conferiu"]
        enviar["Enviar o PDF de uma notificação"]
        ver["Ver a notificação: original, texto lido e dados conferidos"]
        conferir["Informar a ciência, escolher a regra e ver o prazo calculado"]
        fila["Ver o que precisa de alguém hoje"]
        resolver["Abrir um caso e marcar resolvido"]
        areas["Definir quem cuida de cada área"]
        ciencia["Confirmar que recebeu o aviso"]
        extrair["Extrair os dados do documento"]
        resumo["Escrever o resumo simples"]
    end

    contador --> conta
    contador --> empresas
    contador --> regras
    contador --> enviar
    contador --> ver
    contador --> conferir
    responsavel --> fila
    responsavel --> resolver
    dono --> areas
    dono --> fila
    cliente --> ciencia
    extrair --> ia
    resumo --> ia

    classDef pronto fill:#dfe5ff,stroke:#2438a6,color:#141922
    classDef planejado fill:#ffffff,stroke:#aab3bf,stroke-dasharray:4 3,color:#47505e
    class conta,empresas,regras,enviar,ver,conferir,fila,resolver,extrair pronto
    class areas,ciencia,resumo planejado
```

Azul: já dá para fazer. Tracejado: planejado.

Hoje toda pessoa que entra pode o mesmo, dentro do próprio escritório. Os papéis (responsável pela área, dono) entram junto com "definir quem cuida de cada área".

## 2. Arquitetura: as peças e quem chama quem

Um sistema só (monólito) em Spring Boot. As telas são páginas HTML montadas no servidor (Thymeleaf), sem aplicativo separado.

```mermaid
flowchart TB
    navegador["Navegador do escritório"]

    subgraph app["Atalaia (Spring Boot, Java 21)"]
        seguranca["Segurança (Spring Security)<br/>fica na frente de todo pedido:<br/>sem sessão, leva para a tela de entrar"]
        conta["Conta<br/>entrar e criar a conta do escritório"]
        empresas["Empresas<br/>os clientes do escritório"]
        painel["Painel<br/>lista: precisa de gente, conferidas, resolvidas"]
        recepcao["Recepção<br/>recebe o PDF e recusa o que não serve"]
        leitor["Leitor<br/>tira o texto do PDF (PDFBox)"]
        analista["Analista<br/>classifica e extrai os dados com o trecho de cada um"]
        verificador["Verificador<br/>confere cada trecho no texto do PDF"]
        regras["Regras de prazo<br/>cadastradas pelo escritório, com artigo e data da conferência"]
        prazo["Prazo<br/>recebe a ciência e a regra, grava a conta na notificação"]
        calculadora["Calculadora de prazo<br/>conta de dois jeitos, mostra os passos"]
        avisos["Avisos (planejado)<br/>e-mail e Telegram"]
        revisao["Fila 'precisa de gente'<br/>(primeiro grupo do Painel)"]
    end

    banco[("PostgreSQL<br/>tabelas criadas pelo Flyway")]
    ia["Serviço de IA (Gemini)"]

    navegador -->|todo pedido| seguranca
    seguranca -->|entra ou cria a conta| conta
    seguranca -->|cadastra um cliente| empresas
    seguranca -->|abre a lista| painel
    seguranca -->|envia o PDF| recepcao
    seguranca -->|cadastra uma regra| regras
    seguranca -->|informa a ciência e escolhe a regra| prazo
    prazo --> regras
    prazo --> calculadora
    prazo --> banco
    regras --> banco
    conta --> banco
    empresas --> banco
    painel --> banco
    recepcao --> leitor
    recepcao --> analista
    analista --> ia
    recepcao --> verificador
    calculadora -.-> avisos
    recepcao --> banco
    leitor -->|PDF sem texto| revisao
    analista -->|IA em dúvida ou fora do ar| revisao
    verificador -->|dado não bate| revisao

    classDef planejado fill:#ffffff,stroke:#aab3bf,stroke-dasharray:4 3,color:#47505e
    class avisos planejado
```

Seta cheia: ligação que já funciona. Seta tracejada: a peça existe (ou está planejada), mas a ligação ainda não foi feita.

Decisões:

- **A IA nunca calcula prazo.** O prazo sai da Calculadora, que é só `java.time`.
- **Nenhuma regra de prazo vem no código.** Cada escritório cadastra as regras que ele mesmo leu na norma, com o artigo e a data da conferência (RNF09). Seis meses depois, a regra aparece como vencida, pedindo nova conferência.
- **O prazo nasce de uma pessoa.** Ela informa o dia da ciência e escolhe a regra; a Calculadora faz a conta de dois jeitos e a notificação guarda a data com a conta inteira, o artigo, quem pediu e quando. A conta é uma cópia: excluir a regra depois não muda o que foi mostrado. Se as duas contas não baterem, a notificação fica sem data e mostra o motivo.
- **Feriados: só os nacionais de data fixa.** Toda conta avisa que feriado estadual, municipal, ponto facultativo e Sexta-feira Santa não entram, para a pessoa conferir.
- **Toda peça devolve um aviso `precisaDeRevisao`.** É o mesmo desenho no Leitor, no Verificador e na Calculadora. Se uma falhar, o caso fica sem prazo e vai para uma pessoa (RNF01).
- **O escritório é a cerca dos dados.** Cada usuário e cada empresa pertencem a um escritório. Toda busca de empresa ou de notificação leva o `escritorioId` de quem entrou, e a notificação de outro escritório responde como se não existisse (404). O `IsolamentoEntreEscritoriosTest` tenta furar essa cerca de quatro jeitos.
- **A pessoa se cadastra sozinha.** O cadastro grava o escritório e a primeira pessoa dele juntos (ou os dois, ou nenhum) e a conta já nasce com a sessão aberta. Ainda não há papéis nem convite de colegas.
- **A senha nunca é guardada.** Só o embaralhado dela (bcrypt). Quem confere e-mail e senha é o Spring Security; a sessão anda só pelo cookie, todo formulário leva o código contra pedido forjado (CSRF) e sair é por POST.
- **Toda resposta sai com cabeçalhos de segurança** (filtro `CabecalhosDeSeguranca`): a página só carrega o que é do próprio Atalaia e não pode ser mostrada dentro de outro site.
- **O banco é criado pelo Flyway**, por arquivos SQL numerados. O Hibernate só confere se as classes batem com as tabelas.
- **A Recepção rege o caminho.** Ela chama o Leitor, o Analista e o Verificador, um depois do outro, e junta os motivos de revisão. Com um motivo que seja, a notificação vai para uma pessoa.
- **A IA fica atrás de uma interface** (`ModeloDeIA`). Hoje quem responde é o Gemini; os testes usam uma IA de mentira, sem internet. Sem a chave `GEMINI_API_KEY`, o sistema sobe igual e manda tudo para revisão.
- **O que a IA diz é conferido por código.** Cada dado precisa aparecer no texto do PDF (Verificador), e um "informativo" que fala em prazo, intimação, citação ou multa vai para revisão.
- **Peças conversando por eventos** (uma publica "documento lido", a outra escuta): fica para quando a análise sair do pedido da tela. Hoje a pessoa espera a resposta da IA com a tela aberta.

## 3. Classes

O que está no código hoje (o `PainelController` e os controllers da conta estão no 3.2). Os pacotes são `leitor`, `analista`, `verificador`, `prazo`, `notificacao`, `recepcao`, `painel`, `conta` e `seguranca`: cada peça mora no pacote com o nome dela.

São dois desenhos, para caber na tela: primeiro o caminho do PDF, depois a conta e a cerca do escritório.

### 3.1 O caminho do PDF

```mermaid
classDiagram
    direction LR

        class LeitorPdf {
            +ler(bytes pdf) ResultadoLeitura
        }
        class ResultadoLeitura {
            <<record>>
            String texto
            boolean precisaDeRevisao
        }

        class Verificador {
            +conferir(String textoDoPdf, List~DadoExtraido~ dados) ResultadoVerificacao
        }
        class DadoExtraido {
            <<record>>
            String campo
            String valor
            String trecho
        }
        class ResultadoVerificacao {
            <<record>>
            boolean precisaDeRevisao
            List~String~ motivos
        }

        class CalculadoraDePrazo {
            -Set~LocalDate~ feriados
            -Clock relogio
            +calcular(RegraDePrazo regra, LocalDate ciencia) PrazoCalculado
            +calcularComCienciaTacita(RegraDePrazo regra, LocalDate entrega, int dias) PrazoCalculado
        }
        class RegraDePrazo {
            <<record>>
            String nome
            int dias
            TipoDeContagem contagem
            boolean suspendeNoRecesso
            String artigo
            LocalDate conferidaEm
            +prazoPorExtenso() String
            +proximaConferencia() LocalDate
            +conferenciaVencida(LocalDate hoje) boolean
        }
        class TipoDeContagem {
            <<enumeration>>
            DIAS_CORRIDOS
            DIAS_UTEIS
        }
        class PrazoCalculado {
            <<record>>
            LocalDate dataLimite
            List~String~ passos
            boolean precisaDeRevisao
        }
        class RegraCadastrada {
            Long id
            +paraAConta() RegraDePrazo
        }
        class RegraController {
            +lista()
            +cadastrar(String nome, String dias, String contagem, String artigo, String conferidaEm)
            +excluir(Long id)
        }
        class PrazoController {
            +calcular(Long id, Long regraId, String ciencia)
        }
        class FeriadosNacionais {
            +entre(int primeiroAno, int ultimoAno) Set~LocalDate~
        }

        class Analista {
            +analisar(String textoDoDocumento) Analise
        }
        class Analise {
            <<record>>
            TipoDeDocumento tipo
            List~DadoExtraido~ dados
            boolean precisaDeRevisao
            String motivo
        }
        class TipoDeDocumento {
            <<enumeration>>
            CITACAO
            INTIMACAO
            COBRANCA
            EXCLUSAO
            INFORMATIVO
        }
        class ModeloDeIA {
            <<interface>>
            +responder(String pergunta) String
        }
        class Gemini {
            +responder(String pergunta) String
        }

        class Empresa {
            Long id
            String cnpj
            String nome
        }
        class Notificacao {
            Long id
            bytes arquivo
            String texto
            TipoDeDocumento tipo
            List~DadoExtraido~ dados
            LocalDate dataLimite
            boolean precisaDeRevisao
            String motivoDaRevisao
            Instant recebidaEm
            Instant resolvidaEm
            Desfecho desfecho
            String observacao
            LocalDate ciencia
            String contaDoPrazo
            Instant prazoCalculadoEm
            String prazoCalculadoPor
            +registrarPrazo(LocalDate ciencia, LocalDate dataLimite, List~String~ passos, String quem)
            +resolver(Desfecho desfecho, String observacao)
        }
        class Desfecho {
            <<enumeration>>
            ARQUIVADO
            MULTA_REDUZIDA
            MULTA_CHEIA
            OUTRO
        }

        class RecepcaoController {
            +formulario()
            +enviar(Long empresaId, MultipartFile arquivo)
            +notificacao(Long id)
            +resolver(Long id, Desfecho desfecho, String observacao)
            +pdf(Long id)
        }

    LeitorPdf ..> ResultadoLeitura : devolve
    Verificador ..> DadoExtraido : recebe
    Verificador ..> ResultadoVerificacao : devolve
    CalculadoraDePrazo ..> RegraDePrazo : recebe
    CalculadoraDePrazo ..> PrazoCalculado : devolve
    RegraDePrazo --> TipoDeContagem
    RegraCadastrada ..> RegraDePrazo : entrega
    RegraController ..> RegraCadastrada : guarda
    PrazoController ..> RegraCadastrada : busca
    PrazoController ..> CalculadoraDePrazo : usa
    PrazoController ..> FeriadosNacionais : usa
    PrazoController ..> Notificacao : grava o prazo
    Notificacao "*" --> "1" Empresa
    RecepcaoController ..> LeitorPdf : usa
    RecepcaoController ..> Analista : usa
    RecepcaoController ..> Verificador : usa
    Analista ..> ModeloDeIA : pergunta
    Analista ..> Analise : devolve
    Analise --> TipoDeDocumento
    Gemini ..|> ModeloDeIA
    Notificacao "1" --> "*" DadoExtraido
    Notificacao --> Desfecho
    RecepcaoController ..> Notificacao : guarda
```

### 3.2 A conta e a cerca do escritório

```mermaid
classDiagram
    direction LR

    class Escritorio {
        Long id
        String nome
    }
    class Usuario {
        Long id
        String email
        String senha
    }
    class Empresa {
        Long id
        String cnpj
        String nome
    }
    class Notificacao

    class UsuarioLogado {
        <<record>>
        String email
        String senha
        Long escritorioId
    }
    class Contas {
        <<service>>
        +loadUserByUsername(String email) UsuarioLogado
        +criar(String nomeDoEscritorio, String email, String senhaEmbaralhada)
    }
    class ContaController {
        +entrar()
        +formulario()
        +cadastrar(String escritorio, String email, String senha, String senhaRepetida)
    }
    class ConfiguracaoDeSeguranca {
        +regras(HttpSecurity http) SecurityFilterChain
        +embaralhador() PasswordEncoder
    }
    class EmpresaController {
        +lista()
        +cadastrar(String nome, String cnpj)
    }
    class PainelController {
        +painel()
    }
    class RecepcaoController
    class RegraCadastrada

    Usuario "*" --> "1" Escritorio
    RegraCadastrada "*" --> "1" Escritorio
    Empresa "*" --> "1" Escritorio
    Notificacao "*" --> "1" Empresa
    ContaController ..> Contas : cria a conta
    Contas ..> Usuario : busca e grava
    Contas ..> Escritorio : grava
    Contas ..> UsuarioLogado : devolve
    ConfiguracaoDeSeguranca ..> Contas : pergunta quem é
    EmpresaController ..> UsuarioLogado : recebe
    PainelController ..> UsuarioLogado : recebe
    RecepcaoController ..> UsuarioLogado : recebe
    EmpresaController ..> Empresa : guarda
```

`Usuario.senha` guarda só o embaralhado (bcrypt). O `UsuarioLogado` é o que o Spring Security mantém enquanto a sessão está aberta: os três controllers recebem esse objeto e usam o `escritorioId` dele em toda busca. A notificação não tem escritório próprio: chega nele pela empresa.

Quando o arquivo passa da trava do servidor (15 MB), o pedido é barrado antes do controller e a pessoa vê a página de erro (`error.html`) com a mensagem do limite.

## 4. Sequências

### 4.1 Criar a conta e entrar

```mermaid
sequenceDiagram
    actor Pessoa
    participant Seguranca as Segurança (Spring Security)
    participant Conta
    participant Banco
    participant Painel

    Pessoa->>Seguranca: abre qualquer tela sem sessão
    Seguranca-->>Pessoa: leva para a tela de entrar

    alt ainda não tem conta
        Pessoa->>Conta: nome do escritório, e-mail e a senha duas vezes
        alt dado inválido ou e-mail já usado
            Conta-->>Pessoa: mostra o erro no campo que falhou
        else tudo certo
            Conta->>Banco: grava o escritório e o usuário juntos, com a senha embaralhada
            Conta->>Seguranca: abre a sessão da conta nova
            Seguranca-->>Pessoa: sessão aberta, vai para o Painel
        end
    else já tem conta
        Pessoa->>Seguranca: e-mail e senha
        Seguranca->>Conta: quem é a pessoa deste e-mail?
        Conta->>Banco: busca o usuário
        Conta-->>Seguranca: e-mail, senha embaralhada e escritório
        alt senha não confere ou conta não existe
            Seguranca-->>Pessoa: volta para a tela de entrar, com o aviso
        else senha confere
            Seguranca-->>Pessoa: sessão aberta, vai para o Painel
        end
    end

    Pessoa->>Painel: abre a lista
    Painel->>Banco: busca só as notificações do escritório de quem entrou
    Painel-->>Pessoa: precisa de gente, conferidas e resolvidas
```

Escritório novo ainda não tem empresa: o Painel aponta para a tela Empresas, que é o primeiro passo antes de enviar um PDF.

### 4.2 Do PDF enviado até o prazo

No desenho, "Recepção" é a tela da notificação: o pedido do prazo é atendido pelo `PrazoController`.

```mermaid
sequenceDiagram
    actor Contador
    participant Recepcao as Recepção
    participant Leitor
    participant Banco
    participant Analista
    participant IA
    participant Verificador
    participant Calculadora

    Note over Contador,Recepcao: Com a sessão aberta. A empresa escolhida tem que ser do escritório de quem entrou
    Contador->>Recepcao: envia o PDF e escolhe a empresa
    alt não é PDF, é grande demais ou não abre
        Recepcao-->>Contador: recusa com a mensagem
    else PDF aceito
        Recepcao->>Leitor: ler(pdf)
        Leitor-->>Recepcao: texto + precisaDeRevisao
        Recepcao->>Analista: analisar(texto)
        Analista->>IA: tipo e dados, com o trecho literal de cada um
        IA-->>Analista: JSON com tipo, confiança e dados
        Analista-->>Recepcao: tipo + dados + precisaDeRevisao
        Recepcao->>Verificador: conferir(texto, dados)
        Verificador-->>Recepcao: motivos de cada dado que não bate
        Recepcao->>Banco: guarda o PDF, o texto, o tipo, os dados e os motivos
        Recepcao-->>Contador: mostra a notificação (tipo e dados, ou por que precisa de gente)
    end

    Note over Contador,Calculadora: O prazo é pedido por uma pessoa, na tela da notificação

    Contador->>Recepcao: informa o dia da ciência e escolhe uma regra do escritório
    alt ciência inválida ou regra que não é do escritório
        Recepcao-->>Contador: mostra o erro, nada é gravado
    else tudo certo
        Recepcao->>Calculadora: calcular(regra, ciência), com os feriados nacionais
        alt as duas contas deram datas diferentes
            Calculadora-->>Recepcao: sem data, com os passos e o motivo
        else contas iguais
            Calculadora-->>Recepcao: data limite e a conta passo a passo, com o artigo
        end
        Recepcao->>Banco: grava a ciência, a data, a conta, quem pediu e quando
        Recepcao-->>Contador: mostra a data limite com a conta ao lado do documento
    end
```

## 5. Estados da notificação

```mermaid
stateDiagram-v2
    [*] --> Recebida: PDF aceito
    Recebida --> PrecisaDeGente: PDF sem texto (escaneado)
    Recebida --> Lida: texto tirado do PDF
    Lida --> PrecisaDeGente: IA em dúvida ou fora do ar, dado não confere
    Lida --> Conferida: tipo e dados conferidos com o texto
    Conferida --> ComPrazo: pessoa informa a ciência e a regra, o código calcula
    PrecisaDeGente --> ComPrazo: pessoa confere o documento, informa a ciência e a regra
    PrecisaDeGente --> Resolvida: pessoa fecha o caso com o desfecho
    Conferida --> Resolvida: pessoa fecha o caso com o desfecho
    ComPrazo --> AvisadaAoCliente: link enviado
    AvisadaAoCliente --> Resolvida: caso fechado com o desfecho
    ComPrazo --> Resolvida: caso fechado com o desfecho
    Resolvida --> [*]
```

Hoje o código chega a **Conferida** ou **Precisa de gente** (pelo aviso `precisaDeRevisao`), das duas a **Com prazo**, e de qualquer uma a **Resolvida**. **Avisada ao cliente** é o plano do Marco 3.

O estado não é uma coluna do banco: sai dos campos da notificação (`precisaDeRevisao`, `dataLimite`, `resolvidaEm`).

## 6. O banco

```mermaid
erDiagram
    escritorio ||--o{ usuario : "tem"
    escritorio ||--o{ empresa : "atende"
    escritorio ||--o{ regra_de_prazo : "conferiu"
    empresa ||--o{ notificacao : "recebe"
    notificacao ||--o{ dado_extraido : "tem"

    escritorio {
        bigint id PK
        varchar nome
    }
    usuario {
        bigint id PK
        bigint escritorio_id FK
        varchar email UK
        varchar senha "só o embaralhado (bcrypt)"
        timestamptz criado_em
    }
    empresa {
        bigint id PK
        bigint escritorio_id FK
        varchar cnpj "único dentro do escritório"
        varchar nome
    }
    notificacao {
        bigint id PK
        bigint empresa_id FK
        bytea arquivo
        text texto
        varchar tipo
        date data_limite
        boolean precisa_de_revisao
        text motivo_da_revisao
        timestamptz recebida_em
        timestamptz resolvida_em
        varchar desfecho
        varchar observacao
        date ciencia
        text conta_do_prazo
        timestamptz prazo_calculado_em
        varchar prazo_calculado_por
    }
    regra_de_prazo {
        bigint id PK
        bigint escritorio_id FK
        varchar nome "único dentro do escritório"
        int dias "de 1 a 365"
        varchar contagem
        boolean suspende_no_recesso
        varchar artigo
        date conferida_em
    }
    dado_extraido {
        bigint notificacao_id FK
        int ordem
        text campo
        text valor
        text trecho
    }
```

| Arquivo | O que faz |
|---|---|
| `V1__empresa_e_notificacao.sql` | Cria `empresa` (CNPJ único, 14 posições) e `notificacao` (texto lido, data limite, aviso de revisão, quando chegou) |
| `V2__notificacao_guarda_o_pdf.sql` | A notificação passa a guardar o PDF original |
| `V3__notificacao_guarda_a_analise.sql` | A notificação passa a guardar o tipo e os motivos da revisão; cria `dado_extraido` (campo, valor, trecho e a ordem de cada dado) |
| `V4__notificacao_resolvida.sql` | A notificação pode ser resolvida: quando, o desfecho e a observação (os três vazios ou os três preenchidos) |
| `V5__desfecho_so_aceita_os_quatro.sql` | O banco só aceita os quatro desfechos que existem no Java |
| `V6__escritorio_e_usuario.sql` | Cria `escritorio` e `usuario` (e-mail único, senha embaralhada); cada empresa passa a ser de um escritório, e o CNPJ deixa de ser único no sistema inteiro para ser único dentro do escritório |
| `V7__regra_de_prazo.sql` | Cria `regra_de_prazo`: as regras que cada escritório conferiu na norma, com artigo e data da conferência |
| `V8__notificacao_guarda_a_conta_do_prazo.sql` | A notificação passa a guardar a ciência, a conta passo a passo, quem pediu e quando (os quatro vazios ou os quatro preenchidos) |
| `db/exemplo/R__empresas_de_exemplo.sql` | Ficou vazio: com o login, cada escritório cadastra as próprias empresas. O arquivo continua porque o Flyway já registrou o nome dele |

Arquivo que entrou na `main` não se edita: mudou, cria o próximo número.

## 7. Como a qualidade é conferida

- Cada peça tem os próprios testes JUnit; o `./mvnw verify` roda todos antes de qualquer PR.
- Toda mudança entra por PR com `Resolve #N`, e passa por uma revisão antes do merge.
- Os testes usam o H2 em memória com as mesmas migrações do Flyway.
- O isolamento entre escritórios tem teste próprio: dois escritórios, e um tenta ver, resolver, listar e enviar no lugar do outro.
- O login passou por teste de invasão antes de entrar na `main`, sem achado crítico ou alto. O que ficou para antes de ir ao ar está na issue #144.
- Só dado inventado: regra de prazo de exemplo, empresas de exemplo, PDFs de exemplo.
