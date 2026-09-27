package com.efinanceiro.servico;

import com.efinanceiro.dominio.Cartao;
import com.efinanceiro.dominio.TipoTransacao;
import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.dto.requisicao.RequisicaoCartao;
import com.efinanceiro.dto.resposta.RespostaCartao;
import com.efinanceiro.repositorio.RepositorioCartao;
import com.efinanceiro.repositorio.RepositorioTransacao;
import com.efinanceiro.repositorio.projecao.GastoPorCartao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class ServicoCartao {

    private final RepositorioCartao repositorioCartao;
    private final RepositorioTransacao repositorioTransacao;
    private final BuscadorRecursosDoUsuario buscadorRecursosDoUsuario;
    private final Clock relogio;

    public ServicoCartao(RepositorioCartao repositorioCartao,
                          RepositorioTransacao repositorioTransacao,
                          BuscadorRecursosDoUsuario buscadorRecursosDoUsuario,
                          Clock relogio) {
        this.repositorioCartao = repositorioCartao;
        this.repositorioTransacao = repositorioTransacao;
        this.buscadorRecursosDoUsuario = buscadorRecursosDoUsuario;
        this.relogio = relogio;
    }

    /**
     * Lista os cartões do usuário autenticado, cada um com o gasto do mês atual calculado. O gasto
     * de todos os cartões sai de uma única consulta agregada no banco.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @return Lista de cartões com o respectivo gasto do mês
     */
    @Transactional(readOnly = true)
    public List<RespostaCartao> listarCartoes(String emailUsuario) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        LocalDate hoje = LocalDate.now(relogio);

        Map<Long, BigDecimal> gastoPorCartao = repositorioTransacao
                .somarPorCartao(usuario.getId(), TipoTransacao.SAIDA, hoje.withDayOfMonth(1), hoje)
                .stream()
                .collect(Collectors.toMap(GastoPorCartao::cartaoId, GastoPorCartao::total));

        return repositorioCartao.findByUsuarioId(usuario.getId()).stream()
                .map(cartao -> paraResposta(cartao, gastoPorCartao.getOrDefault(cartao.getId(), BigDecimal.ZERO)))
                .toList();
    }

    /**
     * Cria um novo cartão para o usuário autenticado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param requisicao Dados do cartão (nome e cores)
     * @return Cartão criado
     */
    public RespostaCartao criarCartao(String emailUsuario, RequisicaoCartao requisicao) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);

        Cartao cartao = new Cartao();
        cartao.setUsuario(usuario);
        cartao.setNome(requisicao.nome());
        cartao.setCorFundo(requisicao.corFundo());
        cartao.setCorTexto(requisicao.corTexto());

        repositorioCartao.save(cartao);
        return paraResposta(cartao, BigDecimal.ZERO);
    }

    /**
     * Atualiza nome e cores de um cartão do usuário autenticado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id do cartão a atualizar
     * @param requisicao Novos dados do cartão
     * @return Cartão atualizado
     */
    public RespostaCartao atualizarCartao(String emailUsuario, Long id, RequisicaoCartao requisicao) {
        Cartao cartao = buscarCartaoDoUsuario(emailUsuario, id);

        cartao.setNome(requisicao.nome());
        cartao.setCorFundo(requisicao.corFundo());
        cartao.setCorTexto(requisicao.corTexto());

        repositorioCartao.save(cartao);
        return paraResposta(cartao, calcularGastoNoMes(cartao.getId()));
    }

    /**
     * Exclui um cartão do usuário autenticado. As transações do cartão continuam, sem cartão
     * (a FK no banco é ON DELETE SET NULL).
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id do cartão a excluir
     */
    public void excluirCartao(String emailUsuario, Long id) {
        Cartao cartao = buscarCartaoDoUsuario(emailUsuario, id);
        repositorioCartao.delete(cartao);
    }

    private Cartao buscarCartaoDoUsuario(String emailUsuario, Long id) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        return buscadorRecursosDoUsuario.buscarCartao(id, usuario.getId());
    }

    private RespostaCartao paraResposta(Cartao cartao, BigDecimal gastoNoMes) {
        return new RespostaCartao(cartao.getId(), cartao.getNome(), cartao.getCorFundo(), cartao.getCorTexto(), gastoNoMes);
    }

    private BigDecimal calcularGastoNoMes(Long cartaoId) {
        LocalDate hoje = LocalDate.now(relogio);

        // Parcelas futuras dentro do próprio mês corrente ainda não "aconteceram" —
        // o intervalo vai só até hoje, mesmo que o mês ainda não tenha terminado.
        return repositorioTransacao.somarDoCartao(cartaoId, TipoTransacao.SAIDA, hoje.withDayOfMonth(1), hoje);
    }
}
