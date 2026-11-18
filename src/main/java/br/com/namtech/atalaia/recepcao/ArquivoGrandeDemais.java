package br.com.namtech.atalaia.recepcao;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

import br.com.namtech.atalaia.notificacao.EmpresaRepository;

//Arquivo acima da trava do servidor (15 MB) é barrado antes de chegar no RecepcaoController.
//Sem esta classe a pessoa veria uma pagina em branco; com ela, volta para o formulario com a mensagem.
//ControllerAdvice: o Spring chama esta classe quando o erro acontece em qualquer controller, ou antes dele
@ControllerAdvice
public class ArquivoGrandeDemais {

    private final EmpresaRepository empresas;

    public ArquivoGrandeDemais(EmpresaRepository empresas) {
        this.empresas = empresas;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ModelAndView avisar() {
        return new ModelAndView("enviar")
                .addObject("empresas", empresas.findAllByOrderByNome())
                .addObject("erro", RecepcaoController.GRANDE_DEMAIS);
    }
}
