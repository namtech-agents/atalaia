#!/usr/bin/env python3
"""Gera os títulos em pixel art do README do Atalaia (docs/titulos/*.svg).

A ideia é a régua do dia do Atalaia: o título fica sempre legível e, embaixo dele, uma marca
vermelha (o prazo) anda pela régua em degraus. Os tracinhos que ela já passou ficam azuis:
o prazo correndo, mas sob controle. À esquerda, o ponteiro do relógio de sol (o símbolo).

Por que SVG com CSS: o README do GitHub não roda JavaScript nem carrega fonte de fora. Então a
fonte é desenhada com quadradinhos e o movimento é só opacidade e transform (os mais leves).
A fonte pixel é a mesma do perfil da Galad (somosgalad/.github, titulos.py), copiada aqui de
propósito: são repositórios separados e um não deve depender do outro.

Uso (na raiz do repositório):  python3 docs/titulos/gerar.py
"""
import unicodedata
from html import escape
from pathlib import Path

SAIDA = Path(__file__).parent

# Cores do Atalaia. O azul da marca (#2438A6) some no fundo escuro: por isso o azul-claro.
NANQUIM, REGUA, FOLHA = "#141922", "#2C3444", "#FBFCFC"
AZUL, AZUL_CLARO, VERMELHO = "#2438A6", "#8C9CF2", "#A3271F"

LARGURA, ALTURA = 880, 60
PX = 4            # tamanho de um pixel da fonte, em unidades do SVG
X_TEXTO = 64      # onde a primeira letra começa (antes dela fica o ponteiro)
Y_TEXTO = 16      # topo das letras (sobra espaço para acento em cima e régua embaixo)
CICLO = 8         # segundos de uma volta: a marca anda, para no fim, a régua zera
INICIO = 0.6      # a marca espera um pouco antes de sair
VELOCIDADE = 300  # unidades do SVG por segundo que a marca anda

# Fonte pixel de 5 linhas (largura variável). "#" é pixel aceso.
LETRAS = {
    "A": ".#./#.#/###/#.#/#.#", "B": "##./#.#/##./#.#/##.", "C": ".##/#../#../#../.##",
    "D": "##./#.#/#.#/#.#/##.", "E": "###/#../##./#../###", "F": "###/#../##./#../#..",
    "G": ".##/#../#.#/#.#/.##", "H": "#.#/#.#/###/#.#/#.#", "I": "###/.#./.#./.#./###",
    "J": "..#/..#/..#/#.#/.#.", "K": "#.#/#.#/##./#.#/#.#", "L": "#../#../#../#../###",
    "M": "#...#/##.##/#.#.#/#...#/#...#", "N": "#..#/##.#/#.##/#..#/#..#",
    "O": ".#./#.#/#.#/#.#/.#.", "P": "##./#.#/##./#../#..", "Q": ".#./#.#/#.#/###/.##",
    "R": "##./#.#/##./#.#/#.#", "S": ".##/#../.#./..#/##.", "T": "###/.#./.#./.#./.#.",
    "U": "#.#/#.#/#.#/#.#/###", "V": "#.#/#.#/#.#/#.#/.#.", "W": "#...#/#...#/#.#.#/##.##/#...#",
    "X": "#.#/#.#/.#./#.#/#.#", "Y": "#.#/#.#/.#./.#./.#.", "Z": "###/..#/.#./#../###",
    "0": "###/#.#/#.#/#.#/###", "1": ".#./##./.#./.#./###", "2": "##./..#/.#./#../###",
    "3": "##./..#/.#./..#/##.", "4": "#.#/#.#/###/..#/..#", "5": "###/#../##./..#/##.",
    "6": ".##/#../###/#.#/###", "7": "###/..#/.#./.#./.#.", "8": "###/#.#/###/#.#/###",
    "9": "###/#.#/###/..#/##.", " ": "../../../../..", ",": "../../../.#/#.", ".": "././././#",
    "-": ".../.../###/.../...",
}
# Acentos ficam acima da letra (NFD separa "É" em "E" + acento); o til entra em "NÃO".
ACENTOS = {"́": "..#/.#.", "̂": ".#./#.#", "̃": ".##/##."}
# Cedilha: a letra C com um rabinho embaixo (cabe porque a régua fica 2 pixels abaixo)
LETRAS["Ç"] = LETRAS["C"]

# Ponteiro do relógio de sol: "B" é a lâmina (azul-claro), "S" é a sombra no chão (azul)
PONTEIRO = ["..B....", ".BB....", ".BB....", "BBB....", "BBBSSSS"]


def quadrados(pontos, lado=PX, alto=None):
    """Uma lista de cantos (x, y) vira um caminho SVG de retângulos `lado` x `alto`."""
    alto = alto or lado
    return "".join(f"M{x},{y}h{lado}v{alto}h-{lado}z" for x, y in pontos)


def letras_em_pixels(frase):
    """Devolve os pixels acesos da frase (em unidades da fonte) e a largura total."""
    pixels, cursor, largura_anterior = [], 0, 0
    for sinal in unicodedata.normalize("NFD", frase.upper()):
        if sinal == "̧":   # cedilha: um pixel embaixo do meio do C
            pixels.append((cursor - largura_anterior, 5))
            continue
        if sinal in ACENTOS:    # o acento não abre letra nova: fica em cima da anterior
            linhas, topo, esquerda = ACENTOS[sinal].split("/"), -3, cursor - largura_anterior - 1
        else:
            linhas, topo, esquerda = LETRAS[sinal].split("/"), 0, cursor
            largura_anterior = len(linhas[0])
            cursor += largura_anterior + 1
        for j, linha in enumerate(linhas):
            pixels += [(esquerda + i, topo + j) for i, p in enumerate(linha) if p == "#"]
    return pixels, cursor - 1


def titulo(frase):
    pixels, largura = letras_em_pixels(frase)
    texto = quadrados([(X_TEXTO + i * PX, Y_TEXTO + j * PX) for i, j in pixels])

    # A régua: um tracinho a cada 2 pixels, mais alto a cada 5 (como régua de verdade).
    # Cada tracinho tem uma cópia azul que acende quando a marca passa por ele.
    y_regua = Y_TEXTO + 7 * PX
    fim = X_TEXTO + largura * PX
    cinzas, azuis, regras = [], [], []
    for n, x in enumerate(range(X_TEXTO, fim, 2 * PX)):
        alto = 2 * PX if n % 5 == 0 else PX
        traco = quadrados([(x, y_regua + 2 * PX - alto)], PX, alto)
        cinzas.append(traco)
        azuis.append(f'<path class="feito t{n}" fill="{AZUL_CLARO}" d="{traco}"/>')
        # O atraso de cada tracinho vai numa regra própria (.t0, .t1...): é o instante em que
        # a marca chega nele. Assim marca e régua andam juntas sem JavaScript.
        regras.append(f".t{n}{{animation-delay:{INICIO + (x - X_TEXTO) / VELOCIDADE:.2f}s}}")

    # A marca do prazo: um bloquinho vermelho de 2 x 3 pixels que anda em degraus de 2 pixels
    viagem = fim - X_TEXTO - 2 * PX
    p_ini = INICIO / CICLO * 100
    p_fim = (INICIO + viagem / VELOCIDADE) / CICLO * 100
    marca = quadrados([(0, y_regua - PX)], 2 * PX, 3 * PX)

    raios = [(18 + i * PX, ALTURA // 2 - 3 * PX + j * PX) for j, l in enumerate(PONTEIRO) for i, c in enumerate(l)]
    lamina = quadrados([p for p, c in zip(raios, "".join(PONTEIRO)) if c == "B"])
    sombra = quadrados([p for p, c in zip(raios, "".join(PONTEIRO)) if c == "S"])

    estilo = f"""
.feito{{opacity:0;animation:feito {CICLO}s steps(1,end) infinite}}
@keyframes feito{{0%,86%{{opacity:1}}87%,100%{{opacity:0}}}}
{"".join(regras)}
.marca{{animation:anda {CICLO}s infinite}}
@keyframes anda{{
0%,{p_ini:.2f}%{{transform:translateX({X_TEXTO}px)}}
{p_ini + 0.01:.2f}%{{transform:translateX({X_TEXTO}px);animation-timing-function:steps({max(1, viagem // (2 * PX))},end)}}
{p_fim:.2f}%,100%{{transform:translateX({X_TEXTO + viagem}px)}}}}
/* Quem pediu menos movimento vê a régua parada, com a marca no começo */
@media (prefers-reduced-motion:reduce){{*{{animation:none!important}}.marca{{transform:translateX({X_TEXTO}px)}}}}"""

    return f"""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {LARGURA} {ALTURA}" role="img" aria-label="{escape(frase)}" shape-rendering="crispEdges">
<title>{escape(frase)}</title>
<style>{estilo}
</style>
<rect width="{LARGURA}" height="{ALTURA}" fill="{NANQUIM}"/>
<path fill="{AZUL}" d="{sombra}"/><path fill="{AZUL_CLARO}" d="{lamina}"/>
<path fill="{FOLHA}" d="{texto}"/>
<path fill="{REGUA}" d="{"".join(cinzas)}"/>
{"".join(azuis)}
<path class="marca" fill="{VERMELHO}" d="{marca}"/>
</svg>
"""


# Arquivo → frase (igual ao título da seção no README). Mudou um título? Mude aqui e rode de novo.
TITULOS = {
    "atalaia": "Atalaia",
    "o-problema": "O problema",
    "o-que-faz": "O que o Atalaia faz",
    "o-que-nao-faz": "O que ele não faz",
    "por-que-e-diferente": "Por que é diferente",
    "situacao": "Situação",
    "tecnologias": "Tecnologias",
    "como-rodar": "Como rodar",
    "como-trabalho": "Como trabalho",
    "ferramentas": "Ferramentas usadas",
    "time": "Time",
}

if __name__ == "__main__":
    for arquivo, frase in TITULOS.items():
        (SAIDA / f"{arquivo}.svg").write_text(titulo(frase))
        print("gerado", arquivo)
