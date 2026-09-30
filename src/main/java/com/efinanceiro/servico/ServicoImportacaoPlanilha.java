package com.efinanceiro.servico;

import com.efinanceiro.dominio.Cartao;
import com.efinanceiro.dominio.Categoria;
import com.efinanceiro.dominio.Conta;
import com.efinanceiro.dominio.TipoTransacao;
import com.efinanceiro.dominio.Transacao;
import com.efinanceiro.dominio.Usuario;
import com.efinanceiro.dto.resposta.ErroImportacao;
import com.efinanceiro.dto.resposta.LinhaPrevia;
import com.efinanceiro.dto.resposta.RespostaImportacao;
import com.efinanceiro.dto.resposta.RespostaPreviaImportacao;
import com.efinanceiro.excecao.ImportacaoInvalidaException;
import com.efinanceiro.repositorio.RepositorioCartao;
import com.efinanceiro.repositorio.RepositorioCategoria;
import com.efinanceiro.repositorio.RepositorioConta;
import com.efinanceiro.repositorio.RepositorioTransacao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Importa movimentações de uma planilha CSV no modelo do sistema. A prévia e a importação fazem a
 * mesma análise; a importação só grava se não houver nenhum erro.
 */
@Service
@Transactional
public class ServicoImportacaoPlanilha {

    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("d/M/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final int LIMITE_CATEGORIAS = 50;
    private static final int LIMITE_LANCAMENTOS = 10_000;
    private static final int TAMANHO_TRECHO = 50;
    private static final String COR_FUNDO_PADRAO = "#EEF0F2";
    private static final String COR_TEXTO_PADRAO = "#3C4043";

    private final LeitorPlanilhaCsv leitorPlanilhaCsv;
    private final BuscadorRecursosDoUsuario buscadorRecursosDoUsuario;
    private final RepositorioCategoria repositorioCategoria;
    private final RepositorioConta repositorioConta;
    private final RepositorioCartao repositorioCartao;
    private final RepositorioTransacao repositorioTransacao;
    private final ServicoRecorrencia servicoRecorrencia;

    public ServicoImportacaoPlanilha(LeitorPlanilhaCsv leitorPlanilhaCsv,
                                     BuscadorRecursosDoUsuario buscadorRecursosDoUsuario,
                                     RepositorioCategoria repositorioCategoria,
                                     RepositorioConta repositorioConta,
                                     RepositorioCartao repositorioCartao,
                                     RepositorioTransacao repositorioTransacao,
                                     ServicoRecorrencia servicoRecorrencia) {
        this.leitorPlanilhaCsv = leitorPlanilhaCsv;
        this.buscadorRecursosDoUsuario = buscadorRecursosDoUsuario;
        this.repositorioCategoria = repositorioCategoria;
        this.repositorioConta = repositorioConta;
        this.repositorioCartao = repositorioCartao;
        this.repositorioTransacao = repositorioTransacao;
        this.servicoRecorrencia = servicoRecorrencia;
    }

    /** Linha que passou na validação de formato, com os nomes já resolvidos. */
    private record LinhaValidada(int numero, LocalDate data, String descricao, TipoTransacao tipo, BigDecimal valor,
                                 String categoria, String conta, String cartao, Integer parcelaAtual, Integer totalParcelas) {

        boolean parcelada() {
            return totalParcelas != null;
        }

        LocalDate dataInicio() {
            return data.minusMonths(parcelaAtual - 1L);
        }
    }

    /** Resultado da análise: linhas válidas, erros e o que precisa ser criado. */
    private record Analise(Usuario usuario, Conta contaPadrao, List<LinhaValidada> linhas, List<ErroImportacao> erros,
                           Map<String, Categoria> categorias, Map<String, Conta> contas, Map<String, Cartao> cartoes,
                           Map<String, TipoTransacao> novasCategorias, Map<String, String> nomesNovasCategorias, Map<String, String> novasContas,
                           Map<String, String> novosCartoes, Set<Integer> duplicadas,
                           Map<String, List<LinhaValidada>> grupos) {
    }

    /**
     * Analisa a planilha sem gravar nada.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param arquivo Bytes do CSV
     * @param contaPadraoId Conta usada nas linhas sem a coluna conta
     * @return Linhas interpretadas, erros, o que será criado e as contagens
     */
    @Transactional(readOnly = true)
    public RespostaPreviaImportacao gerarPrevia(String emailUsuario, byte[] arquivo, Long contaPadraoId) {
        Analise analise = analisar(emailUsuario, arquivo, contaPadraoId);

        List<LinhaPrevia> linhas = analise.linhas().stream()
                .map(linha -> new LinhaPrevia(linha.numero(), linha.data(), linha.descricao(), linha.tipo(), linha.valor(),
                        linha.categoria(), linha.conta() != null ? linha.conta() : analise.contaPadrao().getNome(), linha.cartao(),
                        linha.parcelaAtual(), linha.totalParcelas(), analise.duplicadas().contains(linha.numero())))
                .toList();

        int avulsos = (int) analise.linhas().stream().filter(linha -> !linha.parcelada()).count();

        return new RespostaPreviaImportacao(linhas, analise.erros(),
                List.copyOf(analise.nomesNovasCategorias().values()),
                List.copyOf(analise.novasContas().values()), List.copyOf(analise.novosCartoes().values()),
                avulsos, analise.grupos().size());
    }

    /**
     * Importa a planilha: tudo ou nada. Possíveis duplicados são pulados, a não ser que o número da
     * linha esteja entre os incluídos.
     *
     * @param emailUsuario E-mail do usuário autenticado
     * @param arquivo Bytes do CSV
     * @param contaPadraoId Conta usada nas linhas sem a coluna conta
     * @param duplicadasIncluidas Números das linhas marcadas como duplicado que devem ser importadas mesmo assim
     * @return Quantidades importadas, criadas e puladas
     */
    public RespostaImportacao importar(String emailUsuario, byte[] arquivo, Long contaPadraoId, Set<Integer> duplicadasIncluidas) {
        Analise analise = analisar(emailUsuario, arquivo, contaPadraoId);

        if (!analise.erros().isEmpty()) {
            throw new ImportacaoInvalidaException(analise.erros());
        }

        List<LinhaValidada> avulsas = new ArrayList<>();
        List<List<LinhaValidada>> grupos = new ArrayList<>();
        int pulados = 0;

        for (LinhaValidada linha : analise.linhas()) {
            if (!linha.parcelada()) {
                if (analise.duplicadas().contains(linha.numero()) && !duplicadasIncluidas.contains(linha.numero())) {
                    pulados++;
                } else {
                    avulsas.add(linha);
                }
            }
        }

        for (List<LinhaValidada> grupo : analise.grupos().values()) {
            boolean duplicado = grupo.stream().anyMatch(linha -> analise.duplicadas().contains(linha.numero()));
            boolean incluido = grupo.stream().anyMatch(linha -> duplicadasIncluidas.contains(linha.numero()));

            if (duplicado && !incluido) {
                pulados++;
            } else {
                grupos.add(grupo);
            }
        }

        List<LinhaValidada> usadas = new ArrayList<>(avulsas);
        grupos.forEach(grupo -> usadas.add(grupo.get(0)));

        int categoriasCriadas = criarCategoriasUsadas(analise, usadas);
        int contasCriadas = criarContasUsadas(analise, usadas);
        int cartoesCriados = criarCartoesUsados(analise, usadas);

        List<Transacao> transacoes = avulsas.stream().map(linha -> paraTransacao(analise, linha)).toList();
        repositorioTransacao.saveAll(transacoes);

        int parcelas = 0;

        for (List<LinhaValidada> grupo : grupos) {
            LinhaValidada primeira = grupo.get(0);
            servicoRecorrencia.gerarRecorrencia(analise.usuario(), contaDaLinha(analise, primeira), cartaoDaLinha(analise, primeira),
                    categoriaDaLinha(analise, primeira), primeira.descricao(), primeira.valor(), primeira.tipo(),
                    primeira.totalParcelas(), primeira.dataInicio());
            parcelas += primeira.totalParcelas();
        }

        return new RespostaImportacao(avulsas.size(), grupos.size(), parcelas, categoriasCriadas, contasCriadas, cartoesCriados, pulados);
    }

    private Analise analisar(String emailUsuario, byte[] arquivo, Long contaPadraoId) {
        Usuario usuario = buscadorRecursosDoUsuario.buscarUsuario(emailUsuario);
        Conta contaPadrao = buscadorRecursosDoUsuario.buscarConta(contaPadraoId, usuario.getId());

        PlanilhaLida planilha = leitorPlanilhaCsv.ler(arquivo);
        List<ErroImportacao> erros = new ArrayList<>(planilha.erros());
        List<LinhaValidada> linhas = new ArrayList<>();

        planilha.linhas().forEach(linha -> {
            LinhaValidada validada = validarLinha(linha, erros);

            if (validada != null) {
                linhas.add(validada);
            }
        });

        Map<String, Categoria> categorias = repositorioCategoria.listarVisiveisAoUsuario(usuario.getId()).stream()
                .collect(Collectors.toMap(categoria -> ColunasPlanilha.normalizar(categoria.getNome()), categoria -> categoria, (a, b) -> a, HashMap::new));
        Map<String, Conta> contas = repositorioConta.findByUsuarioId(usuario.getId()).stream()
                .collect(Collectors.toMap(conta -> ColunasPlanilha.normalizar(conta.getNome()), conta -> conta, (a, b) -> a, HashMap::new));
        Map<String, Cartao> cartoes = repositorioCartao.findByUsuarioId(usuario.getId()).stream()
                .collect(Collectors.toMap(cartao -> ColunasPlanilha.normalizar(cartao.getNome()), cartao -> cartao, (a, b) -> a, HashMap::new));

        Map<String, TipoTransacao> novasCategorias = new LinkedHashMap<>();
        Map<String, String> nomesNovasCategorias = new LinkedHashMap<>();
        Map<String, String> novasContas = new LinkedHashMap<>();
        Map<String, String> novosCartoes = new LinkedHashMap<>();
        List<LinhaValidada> resolvidas = new ArrayList<>();
        Map<TipoTransacao, String> categoriasPadrao = Map.of(
                TipoTransacao.ENTRADA, nomeCategoriaFixa("RENDA"),
                TipoTransacao.SAIDA, nomeCategoriaFixa("OUTRO"));

        for (LinhaValidada linha : linhas) {
            String categoria = resolverCategoria(linha, categoriasPadrao, categorias, novasCategorias, nomesNovasCategorias, erros);

            if (categoria == null) {
                continue;
            }

            if (linha.conta() != null && !contas.containsKey(ColunasPlanilha.normalizar(linha.conta()))) {
                novasContas.putIfAbsent(ColunasPlanilha.normalizar(linha.conta()), linha.conta());
            }

            if (linha.cartao() != null && !cartoes.containsKey(ColunasPlanilha.normalizar(linha.cartao()))) {
                novosCartoes.putIfAbsent(ColunasPlanilha.normalizar(linha.cartao()), linha.cartao());
            }

            resolvidas.add(new LinhaValidada(linha.numero(), linha.data(), linha.descricao(), linha.tipo(), linha.valor(), categoria,
                    linha.conta(), linha.cartao(), linha.parcelaAtual(), linha.totalParcelas()));
        }

        long personalizadas = repositorioCategoria.countByUsuarioId(usuario.getId());

        if (personalizadas + novasCategorias.size() > LIMITE_CATEGORIAS) {
            erros.add(new ErroImportacao(0, ColunasPlanilha.CATEGORIA, "A importação criaria " + novasCategorias.size()
                    + " categorias e passaria do limite de " + LIMITE_CATEGORIAS + " categorias personalizadas"));
        }

        Map<String, List<LinhaValidada>> grupos = new LinkedHashMap<>();
        resolvidas.stream().filter(LinhaValidada::parcelada)
                .forEach(linha -> grupos.computeIfAbsent(chaveGrupo(linha, contaPadrao), chave -> new ArrayList<>()).add(linha));

        long lancamentosGerados = resolvidas.stream().filter(linha -> !linha.parcelada()).count()
                + grupos.values().stream().mapToLong(grupo -> grupo.get(0).totalParcelas()).sum();

        if (lancamentosGerados > LIMITE_LANCAMENTOS) {
            erros.add(new ErroImportacao(0, null, "A importação geraria " + lancamentosGerados + " lançamentos (limite de 10.000)"));
        }

        erros.sort(Comparator.comparingInt(ErroImportacao::linha));

        Set<Integer> duplicadas = detectarDuplicadas(usuario, contaPadrao, resolvidas, contas);

        // Grupo de parcelas: se uma linha é possível duplicado, o grupo inteiro é
        grupos.values().stream()
                .filter(grupo -> grupo.stream().anyMatch(linha -> duplicadas.contains(linha.numero())))
                .forEach(grupo -> grupo.forEach(linha -> duplicadas.add(linha.numero())));

        return new Analise(usuario, contaPadrao, resolvidas, erros, categorias, contas, cartoes,
                novasCategorias, nomesNovasCategorias, novasContas, novosCartoes, duplicadas, grupos);
    }

    private LinhaValidada validarLinha(LinhaPlanilha linha, List<ErroImportacao> erros) {
        int numero = linha.numero();
        int errosAntes = erros.size();

        LocalDate data = lerData(linha.campo(ColunasPlanilha.DATA), numero, erros);
        String descricao = lerTexto(linha.campo(ColunasPlanilha.DESCRICAO), ColunasPlanilha.DESCRICAO, 160, true, numero, erros);
        TipoTransacao tipo = lerTipo(linha.campo(ColunasPlanilha.TIPO), numero, erros);
        BigDecimal valor = lerValor(linha.campo(ColunasPlanilha.VALOR), numero, erros);
        String categoria = lerTexto(linha.campo(ColunasPlanilha.CATEGORIA), ColunasPlanilha.CATEGORIA, 40, false, numero, erros);
        String conta = lerTexto(linha.campo(ColunasPlanilha.CONTA), ColunasPlanilha.CONTA, 120, false, numero, erros);
        String cartao = lerTexto(linha.campo(ColunasPlanilha.CARTAO), ColunasPlanilha.CARTAO, 120, false, numero, erros);
        Integer[] parcelas = lerParcelas(linha, numero, erros);

        if (erros.size() > errosAntes) {
            return null;
        }

        return new LinhaValidada(numero, data, descricao, tipo, valor, categoria, conta, cartao, parcelas[0], parcelas[1]);
    }

    private LocalDate lerData(String texto, int numero, List<ErroImportacao> erros) {
        if (texto.isEmpty()) {
            erros.add(new ErroImportacao(numero, ColunasPlanilha.DATA, "A data é obrigatória"));
            return null;
        }

        try {
            return LocalDate.parse(texto, FORMATO_DATA);
        } catch (DateTimeParseException e) {
            erros.add(new ErroImportacao(numero, ColunasPlanilha.DATA, "'" + trecho(texto) + "' não é uma data válida (use dd/mm/aaaa)"));
            return null;
        }
    }

    private String lerTexto(String texto, String coluna, int limite, boolean obrigatorio, int numero, List<ErroImportacao> erros) {
        if (texto.isEmpty()) {
            if (obrigatorio) {
                erros.add(new ErroImportacao(numero, coluna, "A " + coluna + " é obrigatória"));
            }

            return null;
        }

        if (texto.length() > limite) {
            erros.add(new ErroImportacao(numero, coluna, "Máximo de " + limite + " caracteres"));
            return null;
        }

        return texto;
    }

    private TipoTransacao lerTipo(String texto, int numero, List<ErroImportacao> erros) {
        String tipo = ColunasPlanilha.normalizar(texto);

        if (tipo.equals("entrada")) {
            return TipoTransacao.ENTRADA;
        }

        if (tipo.equals("saida")) {
            return TipoTransacao.SAIDA;
        }

        erros.add(new ErroImportacao(numero, ColunasPlanilha.TIPO, texto.isEmpty()
                ? "O tipo é obrigatório (use Entrada ou Saída)"
                : "'" + trecho(texto) + "' não é um tipo válido (use Entrada ou Saída)"));
        return null;
    }

    private BigDecimal lerValor(String texto, int numero, List<ErroImportacao> erros) {
        String bruto = texto.replace("R$", "").replace(" ", "").replace("\u00A0", "");

        if (bruto.isEmpty()) {
            erros.add(new ErroImportacao(numero, ColunasPlanilha.VALOR, "O valor é obrigatório"));
            return null;
        }

        String normalizado;

        if (bruto.contains(",")) {
            normalizado = bruto.replace(".", "").replace(",", ".");            // 1.234,56
        } else if (bruto.matches("-?\\d{1,3}(\\.\\d{3})+")) {
            normalizado = bruto.replace(".", "");                             // 1.234 = mil duzentos e trinta e quatro
        } else {
            normalizado = bruto;                                              // 1234.56
        }

        if (!normalizado.matches("-?\\d+(\\.\\d+)?")) {
            erros.add(new ErroImportacao(numero, ColunasPlanilha.VALOR, "'" + trecho(texto) + "' não é um valor válido"));
            return null;
        }

        BigDecimal valor = new BigDecimal(normalizado);

        if (valor.signum() < 0) {
            erros.add(new ErroImportacao(numero, ColunasPlanilha.VALOR, "Use valor positivo; entrada ou saída vem da coluna tipo"));
        } else if (valor.signum() == 0) {
            erros.add(new ErroImportacao(numero, ColunasPlanilha.VALOR, "O valor deve ser maior que zero"));
        } else if (valor.stripTrailingZeros().scale() > 2) {
            erros.add(new ErroImportacao(numero, ColunasPlanilha.VALOR, "O valor deve ter no máximo 2 casas decimais"));
        } else if (valor.precision() - valor.scale() > 10) {
            erros.add(new ErroImportacao(numero, ColunasPlanilha.VALOR, "O valor deve ter no máximo 10 dígitos inteiros"));
        } else {
            return valor.setScale(2, RoundingMode.UNNECESSARY);
        }

        return null;
    }

    private Integer[] lerParcelas(LinhaPlanilha linha, int numero, List<ErroImportacao> erros) {
        String atualTexto = linha.campo(ColunasPlanilha.PARCELA_ATUAL);
        String totalTexto = linha.campo(ColunasPlanilha.TOTAL_PARCELAS);

        if (atualTexto.isEmpty() && totalTexto.isEmpty()) {
            return new Integer[]{null, null};
        }

        if (totalTexto.isEmpty()) {
            erros.add(new ErroImportacao(numero, ColunasPlanilha.TOTAL_PARCELAS, "Informe também o total de parcelas"));
            return new Integer[]{null, null};
        }

        if (atualTexto.isEmpty()) {
            erros.add(new ErroImportacao(numero, ColunasPlanilha.PARCELA_ATUAL, "Informe também a parcela atual"));
            return new Integer[]{null, null};
        }

        Integer atual = lerInteiro(atualTexto, ColunasPlanilha.PARCELA_ATUAL, numero, erros);
        Integer total = lerInteiro(totalTexto, ColunasPlanilha.TOTAL_PARCELAS, numero, erros);

        if (atual == null || total == null) {
            return new Integer[]{null, null};
        }

        if (total > 360) {
            erros.add(new ErroImportacao(numero, ColunasPlanilha.TOTAL_PARCELAS, "O total de parcelas deve ser no máximo 360"));
        } else if (atual > total) {
            erros.add(new ErroImportacao(numero, ColunasPlanilha.PARCELA_ATUAL,
                    "A parcela atual (" + atual + ") é maior que o total de parcelas (" + total + ")"));
        }

        return new Integer[]{atual, total};
    }

    private Integer lerInteiro(String texto, String coluna, int numero, List<ErroImportacao> erros) {
        try {
            int valor = Integer.parseInt(texto);

            if (valor >= 1) {
                return valor;
            }
        } catch (NumberFormatException e) {
            // cai no erro abaixo
        }

        erros.add(new ErroImportacao(numero, coluna, "'" + trecho(texto) + "' não é um número inteiro maior que zero"));
        return null;
    }

    // Limita o texto do usuário repetido nas mensagens de erro
    private String trecho(String texto) {
        return texto.length() > TAMANHO_TRECHO ? texto.substring(0, TAMANHO_TRECHO) + "…" : texto;
    }

    private String nomeCategoriaFixa(String codigo) {
        return repositorioCategoria.findByCodigo(codigo).map(Categoria::getNome)
                .orElseThrow(() -> new IllegalStateException("Categoria fixa " + codigo + " não existe no banco"));
    }

    private String resolverCategoria(LinhaValidada linha, Map<TipoTransacao, String> categoriasPadrao, Map<String, Categoria> categorias,
                                     Map<String, TipoTransacao> novas, Map<String, String> nomesNovas, List<ErroImportacao> erros) {
        if (linha.categoria() == null) {
            return categoriasPadrao.get(linha.tipo());
        }

        String chave = ColunasPlanilha.normalizar(linha.categoria());
        Categoria existente = categorias.get(chave);
        TipoTransacao tipoConhecido = existente != null ? existente.getTipo() : novas.get(chave);

        if (tipoConhecido != null && tipoConhecido != linha.tipo()) {
            erros.add(new ErroImportacao(linha.numero(), ColunasPlanilha.CATEGORIA, "A categoria '" + trecho(linha.categoria()) + "' é de "
                    + descreverTipo(tipoConhecido) + ", mas a linha é de " + descreverTipo(linha.tipo())));
            return null;
        }

        if (existente != null) {
            return existente.getNome();
        }

        novas.putIfAbsent(chave, linha.tipo());
        nomesNovas.putIfAbsent(chave, linha.categoria());
        return nomesNovas.get(chave);
    }

    private String descreverTipo(TipoTransacao tipo) {
        return tipo == TipoTransacao.ENTRADA ? "entrada" : "saída";
    }

    private String chaveGrupo(LinhaValidada linha, Conta contaPadrao) {
        return String.join("|",
                ColunasPlanilha.normalizar(linha.descricao()),
                linha.tipo().name(),
                linha.valor().toPlainString(),
                linha.totalParcelas().toString(),
                linha.conta() != null ? ColunasPlanilha.normalizar(linha.conta()) : "#" + contaPadrao.getId(),
                ColunasPlanilha.normalizar(linha.categoria()),
                linha.cartao() != null ? ColunasPlanilha.normalizar(linha.cartao()) : "",
                linha.dataInicio().toString());
    }

    private Set<Integer> detectarDuplicadas(Usuario usuario, Conta contaPadrao, List<LinhaValidada> linhas, Map<String, Conta> contas) {
        Set<Integer> duplicadas = new HashSet<>();

        if (linhas.isEmpty()) {
            return duplicadas;
        }

        LocalDate inicio = linhas.stream().map(LinhaValidada::data).min(LocalDate::compareTo).orElseThrow();
        LocalDate fim = linhas.stream().map(LinhaValidada::data).max(LocalDate::compareTo).orElseThrow();

        Set<String> existentes = repositorioTransacao.findByUsuarioIdAndDataTransacaoBetween(usuario.getId(), inicio, fim).stream()
                .map(transacao -> chaveDuplicado(transacao.getDataTransacao(), transacao.getDescricao(), transacao.getValor(), transacao.getConta().getId()))
                .collect(Collectors.toSet());

        for (LinhaValidada linha : linhas) {
            Conta conta = linha.conta() == null ? contaPadrao : contas.get(ColunasPlanilha.normalizar(linha.conta()));

            // Conta nova nunca tem lançamento repetido
            if (conta != null && existentes.contains(chaveDuplicado(linha.data(), linha.descricao(), linha.valor(), conta.getId()))) {
                duplicadas.add(linha.numero());
            }
        }

        return duplicadas;
    }

    private String chaveDuplicado(LocalDate data, String descricao, BigDecimal valor, Long contaId) {
        return data + "|" + ColunasPlanilha.normalizar(descricao) + "|" + valor.setScale(2, RoundingMode.HALF_UP).toPlainString() + "|" + contaId;
    }

    private int criarCategoriasUsadas(Analise analise, List<LinhaValidada> usadas) {
        int criadas = 0;

        for (LinhaValidada linha : usadas) {
            String chave = ColunasPlanilha.normalizar(linha.categoria());

            if (!analise.categorias().containsKey(chave) && analise.novasCategorias().containsKey(chave)) {
                Categoria categoria = new Categoria();
                categoria.setUsuario(analise.usuario());
                categoria.setNome(linha.categoria());
                categoria.setTipo(analise.novasCategorias().get(chave));
                categoria.setIcone("ellipsis");
                categoria.setTom("neutral");
                repositorioCategoria.save(categoria);
                analise.categorias().put(chave, categoria);
                criadas++;
            }
        }

        return criadas;
    }

    private int criarContasUsadas(Analise analise, List<LinhaValidada> usadas) {
        int criadas = 0;

        for (LinhaValidada linha : usadas) {
            if (linha.conta() != null && !analise.contas().containsKey(ColunasPlanilha.normalizar(linha.conta()))) {
                Conta conta = new Conta();
                conta.setUsuario(analise.usuario());
                conta.setNome(linha.conta());
                conta.setCorFundo(COR_FUNDO_PADRAO);
                conta.setCorTexto(COR_TEXTO_PADRAO);
                repositorioConta.save(conta);
                analise.contas().put(ColunasPlanilha.normalizar(linha.conta()), conta);
                criadas++;
            }
        }

        return criadas;
    }

    private int criarCartoesUsados(Analise analise, List<LinhaValidada> usadas) {
        int criados = 0;

        for (LinhaValidada linha : usadas) {
            if (linha.cartao() != null && !analise.cartoes().containsKey(ColunasPlanilha.normalizar(linha.cartao()))) {
                Cartao cartao = new Cartao();
                cartao.setUsuario(analise.usuario());
                cartao.setNome(linha.cartao());
                cartao.setCorFundo(COR_FUNDO_PADRAO);
                cartao.setCorTexto(COR_TEXTO_PADRAO);
                repositorioCartao.save(cartao);
                analise.cartoes().put(ColunasPlanilha.normalizar(linha.cartao()), cartao);
                criados++;
            }
        }

        return criados;
    }

    private Transacao paraTransacao(Analise analise, LinhaValidada linha) {
        Transacao transacao = new Transacao();
        transacao.setUsuario(analise.usuario());
        transacao.setDescricao(linha.descricao());
        transacao.setValor(linha.valor());
        transacao.setTipo(linha.tipo());
        transacao.setDataTransacao(linha.data());
        transacao.setConta(contaDaLinha(analise, linha));
        transacao.setCartao(cartaoDaLinha(analise, linha));
        transacao.setCategoria(categoriaDaLinha(analise, linha));
        return transacao;
    }

    private Conta contaDaLinha(Analise analise, LinhaValidada linha) {
        return linha.conta() == null ? analise.contaPadrao() : analise.contas().get(ColunasPlanilha.normalizar(linha.conta()));
    }

    private Cartao cartaoDaLinha(Analise analise, LinhaValidada linha) {
        return linha.cartao() == null ? null : analise.cartoes().get(ColunasPlanilha.normalizar(linha.cartao()));
    }

    private Categoria categoriaDaLinha(Analise analise, LinhaValidada linha) {
        return analise.categorias().get(ColunasPlanilha.normalizar(linha.categoria()));
    }
}
