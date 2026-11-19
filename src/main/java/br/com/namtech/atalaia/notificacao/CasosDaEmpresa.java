package br.com.namtech.atalaia.notificacao;

//Uma linha da contagem por cliente: a empresa e quantos casos ela teve no periodo (NotificacaoRepository.contarPorEmpresa)
public record CasosDaEmpresa(Long empresaId, long casos) {
}
