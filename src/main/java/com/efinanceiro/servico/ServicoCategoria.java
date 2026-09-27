package com.efinanceiro.servico;

import com.efinanceiro.dominio.Categoria;
import com.efinanceiro.dominio.OpcoesCategoria;
import com.efinanceiro.dominio.TipoTransacao;
import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.dto.requisicao.RequisicaoAtualizacaoCategoria;
import com.efinanceiro.dto.requisicao.RequisicaoCategoria;
import com.efinanceiro.dto.resposta.RespostaCategoria;
import com.efinanceiro.dto.resposta.RespostaExclusaoCategoria;
import com.efinanceiro.dto.resposta.RespostaOpcoesCategoria;
import com.efinanceiro.excecao.DadosInvalidosException;
import com.efinanceiro.excecao.RecursoDuplicadoException;
import com.efinanceiro.excecao.RecursoNaoEncontradoException;
import com.efinanceiro.excecao.RegraDeNegocioException;
import com.efinanceiro.repositorio.RepositorioCategoria;
import com.efinanceiro.repositorio.RepositorioRecorrencia;
import com.efinanceiro.repositorio.RepositorioTransacao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class ServicoCategoria {

    private static final int LIMITE_PERSONALIZADAS = 50;

    // Fixas primeiro (na ordem de criação), depois as personalizadas por nome
    private static final Comparator<Categoria> ORDEM = Comparator
            .comparing((Categoria categoria) -> categoria.isFixa() ? 0 : 1)
            .thenComparing(categoria -> categoria.isFixa() ? "" : categoria.getNome().toLowerCase(Locale.ROOT))
            .thenComparing(Categoria::getId);

    private final RepositorioCategoria repositorioCategoria;
    private final RepositorioTransacao repositorioTransacao;
    private final RepositorioRecorrencia repositorioRecorrencia;
    private final BuscadorRecursosDoUsuario buscadorRecursosDoUsuario;

    public ServicoCategoria(RepositorioCategoria repositorioCategoria,
                             RepositorioTransacao repositorioTransacao,
                             RepositorioRecorrencia repositorioRecorrencia,
                             BuscadorRecursosDoUsuario buscadorRecursosDoUsuario) {
        this.repositorioCategoria = repositorioCategoria;
        this.repositorioTransacao = repositorioTransacao;
        this.repositorioRecorrencia = repositorioRecorrencia;
        this.buscadorRecursosDoUsuario = buscadorRecursosDoUsuario;
    }

    /**
     * Lista as categorias que o usuário pode usar: as fixas do sistema e as personalizadas dele.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @return Fixas primeiro, depois as personalizadas em ordem alfabética
     */
    @Transactional(readOnly = true)
    public List<RespostaCategoria> listarCategorias(String emailUsuario) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);

        return repositorioCategoria.listarVisiveisAoUsuario(usuario.getId()).stream()
                .sorted(ORDEM)
                .map(this::paraResposta)
                .toList();
    }

    /**
     * Ícones e tons que uma categoria pode usar.
     *
     * @return Listas de ícones (nomes Lucide) e tons do design system
     */
    @Transactional(readOnly = true)
    public RespostaOpcoesCategoria listarOpcoes() {
        return new RespostaOpcoesCategoria(OpcoesCategoria.ICONES, OpcoesCategoria.TONS);
    }

    /**
     * Cria uma categoria personalizada pro usuário autenticado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param requisicao Nome, tipo, ícone e tom
     * @return Categoria criada
     */
    public RespostaCategoria criarCategoria(String emailUsuario, RequisicaoCategoria requisicao) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);

        if (repositorioCategoria.countByUsuarioId(usuario.getId()) >= LIMITE_PERSONALIZADAS) {
            throw new RegraDeNegocioException("Limite de " + LIMITE_PERSONALIZADAS + " categorias personalizadas atingido");
        }

        String nome = requisicao.nome().trim();
        validarIconeETom(requisicao.icone(), requisicao.tom());
        validarNomeLivre(nome, usuario.getId(), 0L);

        Categoria categoria = new Categoria();
        categoria.setUsuario(usuario);
        categoria.setNome(nome);
        categoria.setTipo(requisicao.tipo());
        categoria.setIcone(requisicao.icone());
        categoria.setTom(requisicao.tom());

        repositorioCategoria.save(categoria);
        return paraResposta(categoria);
    }

    /**
     * Atualiza nome, ícone e tom de uma categoria personalizada do usuário (o tipo não muda).
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da categoria
     * @param requisicao Novo nome, ícone e tom
     * @return Categoria atualizada
     */
    public RespostaCategoria atualizarCategoria(String emailUsuario, Long id, RequisicaoAtualizacaoCategoria requisicao) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        Categoria categoria = buscarPersonalizadaDoUsuario(id, usuario.getId());

        String nome = requisicao.nome().trim();
        validarIconeETom(requisicao.icone(), requisicao.tom());
        validarNomeLivre(nome, usuario.getId(), categoria.getId());

        categoria.setNome(nome);
        categoria.setIcone(requisicao.icone());
        categoria.setTom(requisicao.tom());

        repositorioCategoria.save(categoria);
        return paraResposta(categoria);
    }

    /**
     * Exclui uma categoria personalizada do usuário. As transações e recorrências dela passam pra
     * categoria fixa genérica do mesmo tipo (Outro pra saída, Renda pra entrada) — nada se perde.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param id Id da categoria
     * @return Quantidade de transações + recorrências movidas
     */
    public RespostaExclusaoCategoria excluirCategoria(String emailUsuario, Long id) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        Categoria categoria = buscarPersonalizadaDoUsuario(id, usuario.getId());

        String codigoDestino = categoria.getTipo() == TipoTransacao.ENTRADA ? "RENDA" : "OUTRO";
        Categoria destino = repositorioCategoria.findByCodigo(codigoDestino)
                .orElseThrow(() -> new IllegalStateException("Categoria fixa " + codigoDestino + " não existe no banco"));

        long movidas = repositorioTransacao.moverCategoria(categoria, destino)
                + repositorioRecorrencia.moverCategoria(categoria, destino);

        repositorioCategoria.delete(categoria);
        return new RespostaExclusaoCategoria(movidas);
    }

    private Categoria buscarPersonalizadaDoUsuario(Long id, Long usuarioId) {
        return repositorioCategoria.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada"));
    }

    private void validarIconeETom(String icone, String tom) {
        if (!OpcoesCategoria.ICONES.contains(icone)) {
            throw new DadosInvalidosException("Ícone inválido");
        }

        if (!OpcoesCategoria.TONS.contains(tom)) {
            throw new DadosInvalidosException("Cor inválida");
        }
    }

    private void validarNomeLivre(String nome, Long usuarioId, Long idIgnorado) {
        if (repositorioCategoria.existeNomeVisivel(nome, usuarioId, idIgnorado)) {
            throw new RecursoDuplicadoException("Já existe uma categoria com esse nome");
        }
    }

    private RespostaCategoria paraResposta(Categoria categoria) {
        return new RespostaCategoria(categoria.getId(), categoria.getNome(), categoria.getTipo(),
                categoria.getIcone(), categoria.getTom(), categoria.isFixa());
    }
}
