package com.efinanceiro.servico;

import com.efinanceiro.dominio.Cartao;
import com.efinanceiro.dominio.Categoria;
import com.efinanceiro.dominio.Conta;
import com.efinanceiro.dominio.Recorrencia;
import com.efinanceiro.dominio.TipoTransacao;
import com.efinanceiro.dominio.Transacao;
import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.dto.requisicao.RequisicaoTransacao;
import com.efinanceiro.dto.resposta.RespostaResumoSaldo;
import com.efinanceiro.dto.resposta.RespostaTransacao;
import com.efinanceiro.excecao.DadosInvalidosException;
import com.efinanceiro.excecao.RecursoNaoEncontradoException;
import com.efinanceiro.repositorio.RepositorioTransacao;
import com.efinanceiro.repositorio.projecao.TotalPorTipo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class ServicoTransacao {

    // Mais recente primeiro; o id desempata transações do mesmo dia, pra paginação não repetir nem pular itens
    private static final Sort ORDENACAO = Sort.by(Sort.Order.desc("dataTransacao"), Sort.Order.desc("id"));

    private final RepositorioTransacao repositorioTransacao;
    private final BuscadorRecursosDoUsuario buscadorRecursosDoUsuario;
    private final Clock relogio;

    public ServicoTransacao(RepositorioTransacao repositorioTransacao,
                             BuscadorRecursosDoUsuario buscadorRecursosDoUsuario,
                             Clock relogio) {
        this.repositorioTransacao = repositorioTransacao;
        this.buscadorRecursosDoUsuario = buscadorRecursosDoUsuario;
        this.relogio = relogio;
    }

    /**
     * Lista as transações do usuário autenticado, filtradas por conta se informado. Sem página e
     * tamanho, devolve todas (comportamento que o front usa hoje pra montar gráficos e filtros de
     * período); com eles, devolve só a página pedida.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param contaId Id da conta para filtrar, ou null para todas as contas
     * @param pagina Número da página (começando em 0), ou null para não paginar
     * @param tamanho Quantidade de itens por página, ou null para não paginar
     * @return Página de transações ordenadas da mais recente pra mais antiga
     */
    @Transactional(readOnly = true)
    public Page<RespostaTransacao> listarTransacoes(String emailUsuario, Long contaId, Integer pagina, Integer tamanho) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        Pageable paginacao = montarPaginacao(pagina, tamanho);

        Page<Transacao> transacoes = contaId == null
                ? repositorioTransacao.findByUsuarioId(usuario.getId(), paginacao)
                : repositorioTransacao.findByUsuarioIdAndContaId(usuario.getId(), contaId, paginacao);

        return transacoes.map(this::paraResposta);
    }

    /**
     * Calcula o resumo financeiro (saldo, entradas e saídas) do usuário autenticado, filtrado por conta se informado.
     * Considera só transações já ocorridas (data <= hoje): parcelas futuras de recorrências ainda não
     * "aconteceram" e não devem descontar o saldo disponível antes da hora. A soma é feita no banco.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param contaId Id da conta para filtrar, ou null para todas as contas
     * @return Resumo com saldo, total de entradas e total de saídas
     */
    @Transactional(readOnly = true)
    public RespostaResumoSaldo buscarResumo(String emailUsuario, Long contaId) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        LocalDate hoje = LocalDate.now(relogio);

        List<TotalPorTipo> totais = contaId == null
                ? repositorioTransacao.somarPorTipoAte(usuario.getId(), hoje)
                : repositorioTransacao.somarPorTipoNaContaAte(usuario.getId(), contaId, hoje);

        BigDecimal totalEntradas = totalDoTipo(totais, TipoTransacao.ENTRADA);
        BigDecimal totalSaidas = totalDoTipo(totais, TipoTransacao.SAIDA);
        BigDecimal saldo = totalEntradas.subtract(totalSaidas);

        return new RespostaResumoSaldo(saldo, totalEntradas, totalSaidas);
    }

    /**
     * Cria uma nova transação para o usuário autenticado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param requisicao Dados da transação
     * @return Transação criada
     */
    public RespostaTransacao criarTransacao(String emailUsuario, RequisicaoTransacao requisicao) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);

        Transacao transacao = new Transacao();
        transacao.setUsuario(usuario);
        preencherTransacao(transacao, requisicao, usuario);

        repositorioTransacao.save(transacao);
        return paraResposta(transacao);
    }

    /**
     * Atualiza uma transação existente do usuário autenticado. Se ela for parcela de uma
     * recorrência, continua vinculada e mantém o número da parcela, mesmo que a data mude.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da transação a atualizar
     * @param requisicao Novos dados da transação
     * @return Transação atualizada
     */
    public RespostaTransacao atualizarTransacao(String emailUsuario, Long id, RequisicaoTransacao requisicao) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        Transacao transacao = buscarTransacaoDoUsuario(usuario.getId(), id);

        preencherTransacao(transacao, requisicao, usuario);

        repositorioTransacao.save(transacao);
        return paraResposta(transacao);
    }

    /**
     * Exclui uma transação do usuário autenticado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da transação a excluir
     */
    public void excluirTransacao(String emailUsuario, Long id) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        Transacao transacao = buscarTransacaoDoUsuario(usuario.getId(), id);
        repositorioTransacao.delete(transacao);
    }

    private Pageable montarPaginacao(Integer pagina, Integer tamanho) {
        if (pagina == null && tamanho == null) {
            return Pageable.unpaged(ORDENACAO);
        }

        return PageRequest.of(pagina != null ? pagina : 0, tamanho != null ? tamanho : 50, ORDENACAO);
    }

    private void preencherTransacao(Transacao transacao, RequisicaoTransacao requisicao, Usuario usuario) {
        transacao.setDescricao(requisicao.descricao());
        transacao.setValor(requisicao.valor());
        transacao.setTipo(requisicao.tipo());
        transacao.setConta(buscadorRecursosDoUsuario.buscarConta(requisicao.contaId(), usuario.getId()));
        transacao.setCategoria(resolverCategoria(requisicao.categoriaId(), requisicao.tipo(), usuario));
        transacao.setDataTransacao(requisicao.dataTransacao() != null ? requisicao.dataTransacao() : LocalDate.now(relogio));
        transacao.setCartao(buscadorRecursosDoUsuario.buscarCartaoOpcional(requisicao.cartaoId(), usuario.getId()));
    }

    private Categoria resolverCategoria(Long categoriaId, TipoTransacao tipo, Usuario usuario) {
        Categoria categoria = buscadorRecursosDoUsuario.buscarCategoria(categoriaId, usuario.getId());

        if (categoria.getTipo() != tipo) {
            throw new DadosInvalidosException("A categoria não é do mesmo tipo da movimentação");
        }

        return categoria;
    }

    private BigDecimal totalDoTipo(List<TotalPorTipo> totais, TipoTransacao tipo) {
        return totais.stream()
                .filter(total -> total.tipo() == tipo)
                .map(TotalPorTipo::total)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private Transacao buscarTransacaoDoUsuario(Long usuarioId, Long id) {
        return repositorioTransacao.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Transação não encontrada"));
    }

    private RespostaTransacao paraResposta(Transacao transacao) {
        Cartao cartao = transacao.getCartao();
        Conta conta = transacao.getConta();
        Recorrencia recorrencia = transacao.getRecorrencia();

        return new RespostaTransacao(
                transacao.getId(),
                transacao.getDescricao(),
                transacao.getValor(),
                transacao.getTipo(),
                conta.getId(),
                conta.getNome(),
                transacao.getCategoria().getId(),
                transacao.getCategoria().getNome(),
                cartao != null ? cartao.getId() : null,
                cartao != null ? cartao.getNome() : null,
                transacao.getDataTransacao(),
                recorrencia != null ? recorrencia.getId() : null,
                recorrencia != null ? transacao.getNumeroParcela() : null,
                recorrencia != null ? recorrencia.getTotalParcelas() : null
        );
    }
}
