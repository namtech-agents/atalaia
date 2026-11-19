package br.com.namtech.atalaia;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

//Apoio dos testes: contar, buscar e apagar sem o filtro do escritório.
//Os repositórios do sistema não têm mais findAll, findById, count nem delete (só buscas que levam o escritório):
//assim o código do sistema não consegue, nem por engano, ver o dado de outro escritório. Os testes precisam
//olhar o banco inteiro para conferir, e fazem isso por aqui. Esta classe fica em src/test: o sistema não a enxerga.
//Transactional: dentro de um teste com transação, usa a dele; num teste sem (ResolverAoMesmoTempoTest), abre uma
@Component
@Transactional
public class BancoDeTeste {

    //A "mesa de trabalho" do JPA, a mesma que os repositórios usam
    @PersistenceContext
    private EntityManager mesa;

    //Quantas linhas há da classe dada, de todos os escritórios
    public long contar(Class<?> tipo) {
        return mesa.createQuery("select count(x) from " + tipo.getSimpleName() + " x", Long.class).getSingleResult();
    }

    //Todas as linhas da classe dada, de todos os escritórios
    public <T> List<T> todos(Class<T> tipo) {
        return mesa.createQuery("select x from " + tipo.getSimpleName() + " x", tipo).getResultList();
    }

    //A última gravada (o maior id)
    public <T> T ultima(Class<T> tipo) {
        return mesa.createQuery("select x from " + tipo.getSimpleName() + " x order by x.id desc", tipo)
                .setMaxResults(1).getSingleResult();
    }

    public <T> T buscar(Class<T> tipo, Long id) {
        return mesa.find(tipo, id);
    }

    //Manda para o banco agora o que está só na mesa (o banco confere as regras dele nesse momento)
    public void gravar() {
        mesa.flush();
    }

    //Apaga a linha, se ela existir
    public void apagar(Class<?> tipo, Long id) {
        Object achado = mesa.find(tipo, id);
        if (achado != null) {
            mesa.remove(achado);
        }
    }
}
