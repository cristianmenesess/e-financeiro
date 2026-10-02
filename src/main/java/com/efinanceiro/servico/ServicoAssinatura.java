package com.efinanceiro.servico;

import com.efinanceiro.dominio.AlcanceEdicao;
import com.efinanceiro.dominio.Assinatura;
import com.efinanceiro.dominio.Cartao;
import com.efinanceiro.dominio.Categoria;
import com.efinanceiro.dominio.TipoTransacao;
import com.efinanceiro.dominio.Transacao;
import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.dto.requisicao.RequisicaoAssinatura;
import com.efinanceiro.dto.resposta.RespostaAssinatura;
import com.efinanceiro.excecao.DadosInvalidosException;
import com.efinanceiro.excecao.RecursoNaoEncontradoException;
import com.efinanceiro.excecao.RegraDeNegocioException;
import com.efinanceiro.repositorio.RepositorioAssinatura;
import com.efinanceiro.repositorio.RepositorioTransacao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Assinaturas: cobranças mensais ou anuais sem data de fim. As cobranças são transações lançadas
 * até {@value #HORIZONTE_MESES} meses à frente de hoje e completadas aos poucos (no login e numa
 * tarefa agendada). Cancelar apaga só as cobranças que ainda não aconteceram.
 */
@Service
@Transactional
public class ServicoAssinatura {

    static final int HORIZONTE_MESES = 12;
    private static final int ANOS_MAXIMOS_NO_PASSADO = 10;

    private final RepositorioAssinatura repositorioAssinatura;
    private final RepositorioTransacao repositorioTransacao;
    private final BuscadorRecursosDoUsuario buscadorRecursosDoUsuario;
    private final Clock relogio;

    public ServicoAssinatura(RepositorioAssinatura repositorioAssinatura,
                             RepositorioTransacao repositorioTransacao,
                             BuscadorRecursosDoUsuario buscadorRecursosDoUsuario,
                             Clock relogio) {
        this.repositorioAssinatura = repositorioAssinatura;
        this.repositorioTransacao = repositorioTransacao;
        this.buscadorRecursosDoUsuario = buscadorRecursosDoUsuario;
        this.relogio = relogio;
    }

    /**
     * Lista as assinaturas do usuário autenticado: ativas primeiro, depois as canceladas.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @return Assinaturas do usuário, com a próxima cobrança das ativas
     */
    @Transactional(readOnly = true)
    public List<RespostaAssinatura> listarAssinaturas(String emailUsuario) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);

        return repositorioAssinatura.findByUsuarioId(usuario.getId()).stream()
                .sorted(Comparator.comparing((Assinatura assinatura) -> assinatura.getCanceladaEm() != null)
                        .thenComparing(Assinatura::getDescricao, String.CASE_INSENSITIVE_ORDER))
                .map(this::paraResposta)
                .toList();
    }

    /**
     * Cria uma assinatura e já lança as cobranças, da data de início até o horizonte. Com cartão,
     * cada cobrança é uma compra na data dela e cai no vencimento da fatura correspondente.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param requisicao Dados da assinatura (sem data de início, começa hoje)
     * @return Assinatura criada
     */
    public RespostaAssinatura criarAssinatura(String emailUsuario, RequisicaoAssinatura requisicao) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);

        Assinatura assinatura = new Assinatura();
        assinatura.setUsuario(usuario);
        preencherAssinatura(assinatura, requisicao, usuario);
        repositorioAssinatura.save(assinatura);

        lancarCobrancas(assinatura);
        return paraResposta(assinatura);
    }

    /**
     * Edita uma assinatura ativa. Com alcance FUTURAS (padrão), as cobranças até hoje ficam como
     * estão e as próximas são refeitas com os dados novos; com TODAS, todas as cobranças são
     * refeitas a partir da data de início.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da assinatura
     * @param requisicao Novos dados e o alcance da edição
     * @return Assinatura atualizada
     */
    public RespostaAssinatura atualizarAssinatura(String emailUsuario, Long id, RequisicaoAssinatura requisicao) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        Assinatura assinatura = buscarAssinaturaDoUsuario(id, usuario.getId());

        if (assinatura.getCanceladaEm() != null) {
            throw new RegraDeNegocioException("Assinatura cancelada não pode ser editada");
        }

        LocalDate hoje = LocalDate.now(relogio);

        if (requisicao.alcance() == AlcanceEdicao.TODAS) {
            repositorioTransacao.deleteByAssinaturaId(assinatura.getId());
            assinatura.setGeradaAte(null);
        } else {
            repositorioTransacao.apagarCobrancasDepoisDe(assinatura.getId(), hoje);

            if (assinatura.getGeradaAte() != null && assinatura.getGeradaAte().isAfter(hoje)) {
                assinatura.setGeradaAte(hoje);
            }
        }

        preencherAssinatura(assinatura, requisicao, usuario);
        repositorioAssinatura.save(assinatura);

        lancarCobrancas(assinatura);
        return paraResposta(assinatura);
    }

    /**
     * Cancela uma assinatura: apaga as cobranças depois de hoje e para de lançar novas. As
     * cobranças até hoje continuam, e a assinatura fica na lista como cancelada.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da assinatura
     * @return Assinatura cancelada
     */
    public RespostaAssinatura cancelarAssinatura(String emailUsuario, Long id) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        Assinatura assinatura = buscarAssinaturaDoUsuario(id, usuario.getId());

        if (assinatura.getCanceladaEm() != null) {
            throw new RegraDeNegocioException("Esta assinatura já está cancelada");
        }

        LocalDate hoje = LocalDate.now(relogio);
        repositorioTransacao.apagarCobrancasDepoisDe(assinatura.getId(), hoje);
        assinatura.setCanceladaEm(hoje);
        repositorioAssinatura.save(assinatura);

        return paraResposta(assinatura);
    }

    /**
     * Exclui uma assinatura com todas as cobranças, inclusive as passadas.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da assinatura
     */
    public void excluirAssinatura(String emailUsuario, Long id) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        Assinatura assinatura = buscarAssinaturaDoUsuario(id, usuario.getId());

        repositorioTransacao.deleteByAssinaturaId(assinatura.getId());
        repositorioAssinatura.delete(assinatura);
    }

    /**
     * Lança as cobranças que faltam até o horizonte nas assinaturas ativas de um usuário.
     *
     * @param usuario Dono das assinaturas
     */
    public void lancarProximasCobrancas(Usuario usuario) {
        repositorioAssinatura.findByUsuarioIdAndCanceladaEmIsNull(usuario.getId()).forEach(this::lancarCobrancas);
    }

    /**
     * Lança as cobranças que faltam até o horizonte nas assinaturas ativas de todos os usuários.
     */
    public void lancarProximasCobrancasDeTodos() {
        repositorioAssinatura.findByCanceladaEmIsNull().forEach(this::lancarCobrancas);
    }

    private void preencherAssinatura(Assinatura assinatura, RequisicaoAssinatura requisicao, Usuario usuario) {
        Categoria categoria = buscadorRecursosDoUsuario.buscarCategoria(requisicao.categoriaId(), usuario.getId());

        if (categoria.getTipo() != TipoTransacao.SAIDA) {
            throw new DadosInvalidosException("A categoria da assinatura deve ser de saída");
        }

        LocalDate hoje = LocalDate.now(relogio);
        LocalDate dataInicio = requisicao.dataInicio() != null ? requisicao.dataInicio()
                : assinatura.getDataInicio() != null ? assinatura.getDataInicio() : hoje;

        if (dataInicio.isBefore(hoje.minusYears(ANOS_MAXIMOS_NO_PASSADO))) {
            throw new DadosInvalidosException("A data de início deve ser de no máximo " + ANOS_MAXIMOS_NO_PASSADO + " anos atrás");
        }

        assinatura.setDescricao(requisicao.descricao());
        assinatura.setValor(requisicao.valor());
        assinatura.setPeriodicidade(requisicao.periodicidade());
        assinatura.setCategoria(categoria);
        assinatura.setConta(buscadorRecursosDoUsuario.buscarConta(requisicao.contaId(), usuario.getId()));
        assinatura.setCartao(buscadorRecursosDoUsuario.buscarCartaoOpcional(requisicao.cartaoId(), usuario.getId()));
        assinatura.setDataInicio(dataInicio);
    }

    private void lancarCobrancas(Assinatura assinatura) {
        LocalDate limite = LocalDate.now(relogio).plusMonths(HORIZONTE_MESES);
        List<Transacao> novas = new ArrayList<>();
        LocalDate ultima = assinatura.getGeradaAte();

        for (long indice = 0; !assinatura.dataDaCobranca(indice).isAfter(limite); indice++) {
            LocalDate data = assinatura.dataDaCobranca(indice);

            if (assinatura.getGeradaAte() == null || data.isAfter(assinatura.getGeradaAte())) {
                novas.add(novaCobranca(assinatura, data));
                ultima = data;
            }
        }

        if (!novas.isEmpty()) {
            repositorioTransacao.saveAll(novas);
            assinatura.setGeradaAte(ultima);
            repositorioAssinatura.save(assinatura);
        }
    }

    private Transacao novaCobranca(Assinatura assinatura, LocalDate data) {
        Transacao transacao = new Transacao();
        transacao.setUsuario(assinatura.getUsuario());
        transacao.setDescricao(assinatura.getDescricao());
        transacao.setValor(assinatura.getValor());
        transacao.setTipo(TipoTransacao.SAIDA);
        transacao.setCategoria(assinatura.getCategoria());
        transacao.setConta(assinatura.getConta());
        transacao.setCartao(assinatura.getCartao());
        transacao.setAssinatura(assinatura);
        CalculadoraFatura.aplicarData(transacao, data);
        return transacao;
    }

    private LocalDate proximaCobranca(Assinatura assinatura) {
        if (assinatura.getCanceladaEm() != null) {
            return null;
        }

        LocalDate hoje = LocalDate.now(relogio);
        long indice = 0;

        while (!assinatura.dataDaCobranca(indice).isAfter(hoje)) {
            indice++;
        }

        return assinatura.dataDaCobranca(indice);
    }

    private Assinatura buscarAssinaturaDoUsuario(Long id, Long usuarioId) {
        return repositorioAssinatura.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Assinatura não encontrada"));
    }

    private RespostaAssinatura paraResposta(Assinatura assinatura) {
        Cartao cartao = assinatura.getCartao();

        return new RespostaAssinatura(
                assinatura.getId(),
                assinatura.getDescricao(),
                assinatura.getValor(),
                assinatura.getPeriodicidade(),
                assinatura.getCategoria().getId(),
                assinatura.getCategoria().getNome(),
                assinatura.getConta().getId(),
                assinatura.getConta().getNome(),
                cartao != null ? cartao.getId() : null,
                cartao != null ? cartao.getNome() : null,
                assinatura.getDataInicio(),
                proximaCobranca(assinatura),
                assinatura.getCanceladaEm()
        );
    }
}
