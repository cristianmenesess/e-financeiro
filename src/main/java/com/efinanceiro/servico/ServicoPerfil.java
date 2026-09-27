package com.efinanceiro.servico;

import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.dto.requisicao.RequisicaoAtualizacaoPerfil;
import com.efinanceiro.dto.requisicao.RequisicaoTrocaSenha;
import com.efinanceiro.dto.resposta.RespostaPerfil;
import com.efinanceiro.excecao.CredenciaisInvalidasException;
import com.efinanceiro.excecao.DadosInvalidosException;
import com.efinanceiro.excecao.EmailJaCadastradoException;
import com.efinanceiro.excecao.ServicoExternoIndisponivelException;
import com.efinanceiro.repositorio.RepositorioCartao;
import com.efinanceiro.repositorio.RepositorioCategoria;
import com.efinanceiro.repositorio.RepositorioConta;
import com.efinanceiro.repositorio.RepositorioRecorrencia;
import com.efinanceiro.repositorio.RepositorioTransacao;
import com.efinanceiro.repositorio.RepositorioUsuario;
import com.efinanceiro.seguranca.ServicoJwt;
import com.efinanceiro.servico.ServicoLimiteRequisicoes.Regra;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

@Slf4j
@Service
@Transactional
public class ServicoPerfil {

    private static final long TAMANHO_MAXIMO_FOTO = 5L * 1024 * 1024;

    private final RepositorioUsuario repositorioUsuario;
    private final RepositorioTransacao repositorioTransacao;
    private final RepositorioRecorrencia repositorioRecorrencia;
    private final RepositorioCartao repositorioCartao;
    private final RepositorioConta repositorioConta;
    private final RepositorioCategoria repositorioCategoria;
    private final BuscadorRecursosDoUsuario buscadorRecursosDoUsuario;
    private final PasswordEncoder codificadorDeSenha;
    private final ServicoJwt servicoJwt;
    private final ServicoLimiteRequisicoes servicoLimiteRequisicoes;
    private final ServicoFotoPerfil servicoFotoPerfil;
    private final Clock relogio;

    public ServicoPerfil(RepositorioUsuario repositorioUsuario,
                          RepositorioTransacao repositorioTransacao,
                          RepositorioRecorrencia repositorioRecorrencia,
                          RepositorioCartao repositorioCartao,
                          RepositorioConta repositorioConta,
                          RepositorioCategoria repositorioCategoria,
                          BuscadorRecursosDoUsuario buscadorRecursosDoUsuario,
                          PasswordEncoder codificadorDeSenha,
                          ServicoJwt servicoJwt,
                          ServicoLimiteRequisicoes servicoLimiteRequisicoes,
                          ServicoFotoPerfil servicoFotoPerfil,
                          Clock relogio) {
        this.repositorioUsuario = repositorioUsuario;
        this.repositorioTransacao = repositorioTransacao;
        this.repositorioRecorrencia = repositorioRecorrencia;
        this.repositorioCartao = repositorioCartao;
        this.repositorioConta = repositorioConta;
        this.repositorioCategoria = repositorioCategoria;
        this.buscadorRecursosDoUsuario = buscadorRecursosDoUsuario;
        this.codificadorDeSenha = codificadorDeSenha;
        this.servicoJwt = servicoJwt;
        this.servicoLimiteRequisicoes = servicoLimiteRequisicoes;
        this.servicoFotoPerfil = servicoFotoPerfil;
        this.relogio = relogio;
    }

    /**
     * Busca os dados do perfil do usuário logado.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @return Nome, e-mail e foto (sem token)
     */
    @Transactional(readOnly = true)
    public RespostaPerfil buscarPerfil(String emailUsuario) {
        return paraResposta(buscadorRecursosDoUsuario.buscarUsuario(emailUsuario), null);
    }

    /**
     * Atualiza nome e e-mail. Trocar o e-mail exige a senha atual, não pode usar o e-mail de outro
     * usuário e gera um token novo (o e-mail vai dentro do JWT; os tokens antigos deixam de valer,
     * inclusive os de antes dessa troca — sem isso, um token emitido pra esse e-mail antes dele ser
     * liberado por outra conta continuaria sendo aceito).
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param requisicao Novo nome, novo e-mail e (se o e-mail mudar) a senha atual
     * @return Perfil atualizado, com token novo só se o e-mail mudou
     */
    public RespostaPerfil atualizarPerfil(String emailUsuario, RequisicaoAtualizacaoPerfil requisicao) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        String novoEmail = NormalizadorEmail.normalizar(requisicao.email());
        String tokenNovo = null;

        if (!novoEmail.equals(usuario.getEmail())) {
            if (requisicao.senhaAtual() == null || requisicao.senhaAtual().isBlank()) {
                throw new DadosInvalidosException("Informe a senha atual para trocar o e-mail");
            }

            conferirSenhaAtual(usuario, requisicao.senhaAtual());

            boolean emUsoPorOutro = repositorioUsuario.findFirstByEmailIgnoreCaseOrderByIdAsc(novoEmail)
                    .filter(outro -> !outro.getId().equals(usuario.getId()))
                    .isPresent();

            if (emUsoPorOutro) {
                throw new EmailJaCadastradoException("Já existe um usuário cadastrado com esse e-mail");
            }

            usuario.setEmail(novoEmail);
            // Derruba qualquer token emitido antes da troca: sem isso, um token antigo desse
            // e-mail (de outra conta que o usou antes, ou de uma troca anterior) voltaria a valer
            usuario.setSenhaAlteradaEm(Instant.now(relogio));
            tokenNovo = servicoJwt.gerarToken(novoEmail);
        }

        usuario.setNome(requisicao.nome().trim());
        repositorioUsuario.save(usuario);

        return paraResposta(usuario, tokenNovo);
    }

    /**
     * Troca a senha do usuário logado. Todos os tokens emitidos antes deixam de valer (outros
     * aparelhos são deslogados) e a sessão atual recebe um token novo.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param requisicao Senha atual e nova senha
     * @return Perfil com o token novo
     */
    public RespostaPerfil trocarSenha(String emailUsuario, RequisicaoTrocaSenha requisicao) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        conferirSenhaAtual(usuario, requisicao.senhaAtual());

        usuario.setSenhaHash(codificadorDeSenha.encode(requisicao.novaSenha()));
        usuario.setSenhaAlteradaEm(Instant.now(relogio));
        repositorioUsuario.save(usuario);

        return paraResposta(usuario, servicoJwt.gerarToken(usuario.getEmail()));
    }

    /**
     * Troca a foto de perfil. O tipo é conferido pelos bytes iniciais do arquivo (não pelo
     * Content-Type, que o cliente pode forjar). Se o Cloudinary falhar, a foto anterior continua.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param arquivo Arquivo enviado no campo "foto"
     * @return Perfil com a URL da foto nova
     */
    public RespostaPerfil atualizarFoto(String emailUsuario, MultipartFile arquivo) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        byte[] conteudo = validarFoto(arquivo);

        usuario.setFotoUrl(servicoFotoPerfil.enviar(usuario.getId(), conteudo));
        repositorioUsuario.save(usuario);

        return paraResposta(usuario, null);
    }

    /**
     * Remove a foto de perfil (volta a exibir as iniciais). A foto só é apagada no Cloudinary
     * depois que o banco confirmar a remoção da URL — mesma ordem da exclusão de cadastro, pra não
     * perder a foto à toa se o banco falhar.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @return Perfil sem foto
     */
    public RespostaPerfil removerFoto(String emailUsuario) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);

        if (usuario.getFotoUrl() != null) {
            usuario.setFotoUrl(null);
            repositorioUsuario.save(usuario);
            apagarFotoDepoisDoCommit(usuario.getId());
        }

        return paraResposta(usuario, null);
    }

    /**
     * Exclui o cadastro do usuário e todos os dados dele (transações, recorrências, categorias
     * personalizadas, cartões, contas e foto), mediante a senha atual. A foto só é apagada no
     * Cloudinary depois que o banco confirmou a exclusão: se o banco falhar, a foto não se perde à toa.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param senha Senha atual, pra confirmar a exclusão
     */
    public void excluirCadastro(String emailUsuario, String senha) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        conferirSenhaAtual(usuario, senha);

        Long usuarioId = usuario.getId();
        boolean tinhaFoto = usuario.getFotoUrl() != null;

        // Ordem importa por causa das FKs: quem aponta pra alguém é apagado antes
        repositorioTransacao.deleteByUsuarioId(usuarioId);
        repositorioRecorrencia.deleteByUsuarioId(usuarioId);
        repositorioCategoria.deleteByUsuarioId(usuarioId);
        repositorioCartao.deleteByUsuarioId(usuarioId);
        repositorioConta.deleteByUsuarioId(usuarioId);
        repositorioUsuario.apagarPorId(usuarioId);

        if (tinhaFoto) {
            apagarFotoDepoisDoCommit(usuarioId);
        }
    }

    private void conferirSenhaAtual(Usuario usuario, String senha) {
        servicoLimiteRequisicoes.consumir(Regra.SENHA_ATUAL_POR_USUARIO, usuario.getId().toString());

        if (!codificadorDeSenha.matches(senha, usuario.getSenhaHash())) {
            throw new CredenciaisInvalidasException("Senha incorreta");
        }
    }

    private byte[] validarFoto(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new DadosInvalidosException("Selecione uma foto");
        }

        if (arquivo.getSize() > TAMANHO_MAXIMO_FOTO) {
            throw new DadosInvalidosException("A foto deve ter no máximo 5 MB");
        }

        byte[] conteudo;

        try {
            conteudo = arquivo.getBytes();
        } catch (IOException e) {
            throw new DadosInvalidosException("Não foi possível ler a foto enviada");
        }

        if (!ehImagemAceita(conteudo)) {
            throw new DadosInvalidosException("A foto deve ser JPG, PNG ou WEBP");
        }

        return conteudo;
    }

    // Assinatura dos primeiros bytes de cada formato aceito
    private boolean ehImagemAceita(byte[] b) {
        boolean jpeg = b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
        boolean png = b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
        boolean webp = b.length > 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';

        return jpeg || png || webp;
    }

    private void apagarFotoDepoisDoCommit(Long usuarioId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    servicoFotoPerfil.apagar(usuarioId);
                } catch (ServicoExternoIndisponivelException e) {
                    log.error("Foto do usuário {} não foi apagada no Cloudinary (ficou órfã)", usuarioId);
                }
            }
        });
    }

    private RespostaPerfil paraResposta(Usuario usuario, String token) {
        return new RespostaPerfil(usuario.getNome(), usuario.getEmail(), usuario.getFotoUrl(), token);
    }
}
