# Como a gente trabalha neste repositório

Combinado curto pra duas pessoas não pisarem no trabalho uma da outra.

## Toda mudança segue o mesmo caminho

1. Pega uma Issue no Kanban e arrasta pra "Fazendo". Se ninguém pegou ainda, é sua.
2. Cria um ramo a partir da `main` atualizada: `git checkout main && git pull && git checkout -b tipo/nome-curto`. O tipo é `feat`, `fix`, `docs`, `test` ou `ci`.
3. Faz a mudança com o teste dela.
4. Roda `./mvnw verify` na sua máquina. Só abre o PR se passar.
5. Abre o PR com `Resolve #N` na descrição e escreve o que rodou.
6. A outra pessoa lê antes do merge, sempre que der. Depois do merge, apaga o ramo.

Ninguém envia direto pra `main`, nem pra arrumar uma vírgula. A proteção automática ainda não está ligada, então por enquanto isso depende da gente.

## Banco de dados

- Mudança no banco é um arquivo SQL novo em `src/main/resources/db/migration`, com número na sequência.
- Arquivo que já entrou na `main` não se edita. Errou? Faz outro arquivo corrigindo.
- Cada um usa o Postgres da própria máquina. Banco não se compartilha, o que se compartilha são os arquivos SQL.

## Dados

O repositório é público. Aqui só entra dado inventado: nada de CNPJ, nome ou documento de empresa de verdade, nem em teste, nem em print, nem em mensagem de commit.

Senha, chave de API e token nunca entram no código. Vão em variável de ambiente.

## Mensagem de commit

Começa com o tipo e diz o que mudou, em português: `feat: envio de PDF cria um caso`.
