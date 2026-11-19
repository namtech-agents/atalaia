# Arquitetura e diagramas · Atalaia (Marco 2, 18/11/2026)

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
        enviar["Enviar o PDF de uma notificação"]
        ver["Ver a notificação: original, texto lido e prazo"]
        conferir["Conferir e confirmar o prazo"]
        fila["Ver o que precisa de alguém hoje"]
        resolver["Abrir um caso e marcar resolvido"]
        areas["Definir quem cuida de cada área"]
        ciencia["Confirmar que recebeu o aviso"]
        extrair["Extrair os dados do documento"]
        resumo["Escrever o resumo simples"]
    end

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
    class enviar,ver pronto
    class conferir,fila,resolver,areas,ciencia,extrair,resumo planejado
```

Azul: já dá para fazer. Tracejado: planejado.

## 2. Arquitetura: as peças e quem chama quem

Um sistema só (monólito) em Spring Boot. As telas são páginas HTML montadas no servidor (Thymeleaf), sem aplicativo separado.

```mermaid
flowchart TB
    navegador["Navegador do escritório"]

    subgraph app["Atalaia (Spring Boot, Java 21)"]
        painel["Painel<br/>lista: precisa de gente, conferidas, resolvidas"]
        recepcao["Recepção<br/>recebe o PDF e recusa o que não serve"]
        leitor["Leitor<br/>tira o texto do PDF (PDFBox)"]
        analista["Analista<br/>classifica e extrai os dados com o trecho de cada um"]
        verificador["Verificador<br/>confere cada trecho no texto do PDF"]
        calculadora["Calculadora de prazo<br/>conta de dois jeitos, mostra os passos"]
        avisos["Avisos (planejado)<br/>e-mail e Telegram"]
        revisao["Fila 'precisa de gente'<br/>(primeiro grupo do Painel)"]
    end

    banco[("PostgreSQL<br/>tabelas criadas pelo Flyway")]
    ia["Serviço de IA (Gemini)"]

    navegador -->|abre a lista| painel
    painel --> banco
    navegador -->|envia o PDF| recepcao
    recepcao --> leitor
    recepcao --> analista
    analista --> ia
    recepcao --> verificador
    verificador -.-> calculadora
    calculadora -.-> avisos
    recepcao --> banco
    leitor -->|PDF sem texto| revisao
    analista -->|IA em dúvida ou fora do ar| revisao
    verificador -->|dado não bate| revisao
    calculadora -.->|contas diferentes ou regra não conferida| revisao

    classDef planejado fill:#ffffff,stroke:#aab3bf,stroke-dasharray:4 3,color:#47505e
    class avisos planejado
```

Seta cheia: ligação que já funciona. Seta tracejada: a peça existe (ou está planejada), mas a ligação ainda não foi feita.

Decisões:

- **A IA nunca calcula prazo.** O prazo sai da Calculadora, que é só `java.time`.
- **Toda peça devolve um aviso `precisaDeRevisao`.** É o mesmo desenho no Leitor, no Verificador e na Calculadora. Se uma falhar, o caso fica sem prazo e vai para uma pessoa (RNF01).
- **Toda resposta sai com cabeçalhos de segurança** (filtro `CabecalhosDeSeguranca`): a página só carrega o que é do próprio Atalaia e não pode ser mostrada dentro de outro site.
- **O banco é criado pelo Flyway**, por arquivos SQL numerados. O Hibernate só confere se as classes batem com as tabelas.
- **A Recepção rege o caminho.** Ela chama o Leitor, o Analista e o Verificador, um depois do outro, e junta os motivos de revisão. Com um motivo que seja, a notificação vai para uma pessoa.
- **A IA fica atrás de uma interface** (`ModeloDeIA`). Hoje quem responde é o Gemini; os testes usam uma IA de mentira, sem internet. Sem a chave `GEMINI_API_KEY`, o sistema sobe igual e manda tudo para revisão.
- **O que a IA diz é conferido por código.** Cada dado precisa aparecer no texto do PDF (Verificador), e um "informativo" que fala em prazo, intimação, citação ou multa vai para revisão.
- **Peças conversando por eventos** (uma publica "documento lido", a outra escuta): fica para quando a análise sair do pedido da tela. Hoje a pessoa espera a resposta da IA com a tela aberta.

## 3. Classes

O que está no código hoje. Os pacotes são `leitor`, `verificador`, `prazo`, `notificacao` e `recepcao`: cada peça mora no pacote com o nome dela.

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
        }

        class RecepcaoController {
            +formulario()
            +enviar(Long empresaId, MultipartFile arquivo)
            +notificacao(Long id)
            +pdf(Long id)
        }

    LeitorPdf ..> ResultadoLeitura : devolve
    Verificador ..> DadoExtraido : recebe
    Verificador ..> ResultadoVerificacao : devolve
    CalculadoraDePrazo ..> RegraDePrazo : recebe
    CalculadoraDePrazo ..> PrazoCalculado : devolve
    RegraDePrazo --> TipoDeContagem
    Notificacao "*" --> "1" Empresa
    RecepcaoController ..> LeitorPdf : usa
    RecepcaoController ..> Analista : usa
    RecepcaoController ..> Verificador : usa
    Analista ..> ModeloDeIA : pergunta
    Analista ..> Analise : devolve
    Analise --> TipoDeDocumento
    Gemini ..|> ModeloDeIA
    Notificacao "1" --> "*" DadoExtraido
    RecepcaoController ..> Notificacao : guarda
```

Quando o arquivo passa da trava do servidor (15 MB), o pedido é barrado antes do controller e a pessoa vê a página de erro (`error.html`) com a mensagem do limite.

## 4. Sequência: do PDF enviado até o prazo

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

    Note over Verificador,Calculadora: Daqui para baixo a Calculadora existe, mas a ligação é planejada

    alt algum motivo de revisão
        Recepcao-->>Contador: precisa de gente, com o motivo
    else tudo confere
        Recepcao-->>Calculadora: calcular(regra, ciência)
        alt contas diferentes ou regra não conferida
            Calculadora-->>Contador: precisa de gente, sem data
        else contas iguais
            Calculadora-->>Contador: data limite + conta passo a passo + artigo
        end
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
    Conferida --> PrecisaDeGente: contas diferentes ou regra não conferida
    Conferida --> ComPrazo: prazo calculado (planejado)
    PrecisaDeGente --> ComPrazo: pessoa confere e confirma (planejado)
    PrecisaDeGente --> Resolvida: pessoa fecha o caso com o desfecho
    Conferida --> Resolvida: pessoa fecha o caso com o desfecho
    ComPrazo --> AvisadaAoCliente: link enviado
    AvisadaAoCliente --> Resolvida: caso fechado com o desfecho
    ComPrazo --> Resolvida: caso fechado com o desfecho
    Resolvida --> [*]
```

Hoje o código chega até **Lida** ou **Precisa de gente** (pelo aviso `precisaDeRevisao`). Os outros estados são o plano dos Marcos 3 e 4.

## 6. O banco

| Arquivo | O que faz |
|---|---|
| `V1__empresa_e_notificacao.sql` | Cria `empresa` (CNPJ único, 14 posições) e `notificacao` (texto lido, data limite, aviso de revisão, quando chegou) |
| `V2__notificacao_guarda_o_pdf.sql` | A notificação passa a guardar o PDF original |
| `V3__notificacao_guarda_a_analise.sql` | A notificação passa a guardar o tipo e os motivos da revisão; cria `dado_extraido` (campo, valor, trecho e a ordem de cada dado) |
| `V4__notificacao_resolvida.sql` | A notificação pode ser resolvida: quando, o desfecho e a observação (os três vazios ou os três preenchidos) |
| `db/exemplo/R__empresas_de_exemplo.sql` | Empresas inventadas para a prévia |

Arquivo que entrou na `main` não se edita: mudou, cria o próximo número.

## 7. Como a qualidade é conferida

- Cada peça tem os próprios testes JUnit; o `./mvnw verify` roda todos antes de qualquer PR.
- Toda mudança entra por PR com `Resolve #N`, e passa por uma revisão antes do merge.
- Os testes usam o H2 em memória com as mesmas migrações do Flyway.
- Só dado inventado: regra de prazo de exemplo, empresas de exemplo, PDFs de exemplo.
