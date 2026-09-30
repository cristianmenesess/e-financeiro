package com.efinanceiro.servico;

import com.efinanceiro.dto.resposta.ErroImportacao;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lê um CSV no modelo de planilha de movimentações: detecta separador (; ou ,) e codificação
 * (UTF-8 ou Windows-1252), mapeia o cabeçalho pras colunas do modelo e devolve as linhas de dados.
 */
@Component
public class LeitorPlanilhaCsv {

    public static final int LIMITE_LINHAS = 2000;
    public static final int LIMITE_COLUNAS = 50;

    private record Registro(int linha, List<String> campos) {
    }

    /** Registros separados e, se o arquivo estiver malformado, o erro que interrompeu a separação. */
    private record Separacao(List<Registro> registros, ErroImportacao erro) {
    }

    /**
     * Lê o conteúdo de um arquivo CSV.
     *
     * @param conteudo Bytes do arquivo
     * @return Linhas de dados e erros de estrutura do arquivo
     */
    public PlanilhaLida ler(byte[] conteudo) {
        String texto = decodificar(conteudo);
        Separacao separacao = separarRegistros(texto, detectarSeparador(texto));
        List<Registro> registros = separacao.registros();
        List<ErroImportacao> erros = new ArrayList<>();

        if (separacao.erro() != null) {
            erros.add(separacao.erro());
            return new PlanilhaLida(List.of(), erros);
        }

        if (registros.isEmpty() || estaVazio(registros.get(0))) {
            erros.add(new ErroImportacao(0, null, "A planilha está vazia"));
            return new PlanilhaLida(List.of(), erros);
        }

        Map<Integer, String> colunasPorPosicao = lerCabecalho(registros.get(0), erros);

        if (!erros.isEmpty()) {
            return new PlanilhaLida(List.of(), erros);
        }

        List<Registro> dados = registros.subList(1, registros.size()).stream()
                .filter(registro -> !estaVazio(registro))
                .toList();

        if (dados.isEmpty()) {
            erros.add(new ErroImportacao(0, null, "A planilha não tem nenhuma movimentação"));
            return new PlanilhaLida(List.of(), erros);
        }

        if (dados.size() > LIMITE_LINHAS) {
            erros.add(new ErroImportacao(0, null, "A planilha tem mais de 2.000 linhas"));
            return new PlanilhaLida(List.of(), erros);
        }

        List<LinhaPlanilha> linhas = dados.stream()
                .map(registro -> paraLinha(registro, colunasPorPosicao))
                .toList();

        return new PlanilhaLida(linhas, erros);
    }

    private Map<Integer, String> lerCabecalho(Registro cabecalho, List<ErroImportacao> erros) {
        Map<Integer, String> colunasPorPosicao = new HashMap<>();

        for (int i = 0; i < cabecalho.campos().size(); i++) {
            String coluna = ColunasPlanilha.identificar(cabecalho.campos().get(i));

            if (coluna == null) {
                continue;
            }

            if (colunasPorPosicao.containsValue(coluna)) {
                erros.add(new ErroImportacao(1, coluna, "Coluna repetida no cabeçalho"));
            } else {
                colunasPorPosicao.put(i, coluna);
            }
        }

        ColunasPlanilha.OBRIGATORIAS.stream()
                .filter(coluna -> !colunasPorPosicao.containsValue(coluna))
                .forEach(coluna -> erros.add(new ErroImportacao(1, coluna, "Coluna obrigatória ausente no cabeçalho")));

        return colunasPorPosicao;
    }

    private LinhaPlanilha paraLinha(Registro registro, Map<Integer, String> colunasPorPosicao) {
        Map<String, String> campos = new HashMap<>();

        colunasPorPosicao.forEach((posicao, coluna) -> {
            String valor = posicao < registro.campos().size() ? registro.campos().get(posicao) : "";
            campos.put(coluna, removerProtecaoDeFormula(valor.trim()));
        });

        return new LinhaPlanilha(registro.linha(), campos);
    }

    // A exportação coloca um apóstrofo antes de =, +, - e @ pra planilha não interpretar como fórmula
    private String removerProtecaoDeFormula(String valor) {
        if (valor.length() > 1 && valor.charAt(0) == '\'' && "=+-@\t\r".indexOf(valor.charAt(1)) >= 0) {
            return valor.substring(1);
        }

        return valor;
    }

    private boolean estaVazio(Registro registro) {
        return registro.campos().stream().allMatch(campo -> campo.isBlank());
    }

    private String decodificar(byte[] conteudo) {
        int inicio = conteudo.length >= 3 && (conteudo[0] & 0xFF) == 0xEF && (conteudo[1] & 0xFF) == 0xBB && (conteudo[2] & 0xFF) == 0xBF ? 3 : 0;
        ByteBuffer bytes = ByteBuffer.wrap(conteudo, inicio, conteudo.length - inicio);

        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(bytes)
                    .toString();
        } catch (CharacterCodingException e) {
            // Excel no Windows costuma salvar CSV em Windows-1252
            return new String(conteudo, inicio, conteudo.length - inicio, Charset.forName("windows-1252"));
        }
    }

    private char detectarSeparador(String texto) {
        int pontoEVirgula = 0;
        int virgula = 0;
        boolean entreAspas = false;

        for (int i = 0; i < texto.length() && texto.charAt(i) != '\n'; i++) {
            char caractere = texto.charAt(i);

            if (caractere == '"') {
                entreAspas = !entreAspas;
            } else if (!entreAspas && caractere == ';') {
                pontoEVirgula++;
            } else if (!entreAspas && caractere == ',') {
                virgula++;
            }
        }

        return pontoEVirgula >= virgula && pontoEVirgula > 0 ? ';' : ',';
    }

    private Separacao separarRegistros(String texto, char separador) {
        List<Registro> registros = new ArrayList<>();
        List<String> campos = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean entreAspas = false;
        int linhaAtual = 1;
        int linhaInicio = 1;
        int linhaAspas = 1;

        for (int i = 0; i < texto.length(); i++) {
            char caractere = texto.charAt(i);

            if (entreAspas) {
                if (caractere == '"' && i + 1 < texto.length() && texto.charAt(i + 1) == '"') {
                    campo.append('"');
                    i++;
                } else if (caractere == '"') {
                    entreAspas = false;
                } else {
                    if (caractere == '\n') {
                        linhaAtual++;
                    }

                    if (caractere != '\r') {
                        campo.append(caractere);
                    }
                }
            } else if (caractere == '"' && campo.isEmpty()) {
                entreAspas = true;
                linhaAspas = linhaAtual;
            } else if (caractere == separador) {
                campos.add(campo.toString());
                campo.setLength(0);

                if (campos.size() > LIMITE_COLUNAS) {
                    return comColunasDemais(registros, linhaInicio);
                }
            } else if (caractere == '\n') {
                campos.add(campo.toString());
                campo.setLength(0);

                if (campos.size() > LIMITE_COLUNAS) {
                    return comColunasDemais(registros, linhaInicio);
                }

                registros.add(new Registro(linhaInicio, campos));
                campos = new ArrayList<>();
                linhaAtual++;
                linhaInicio = linhaAtual;
            } else if (caractere != '\r') {
                campo.append(caractere);
            }
        }

        if (entreAspas) {
            return new Separacao(registros, new ErroImportacao(linhaAspas, null, "Aspas não fechadas a partir desta linha"));
        }

        if (!campo.isEmpty() || !campos.isEmpty()) {
            campos.add(campo.toString());

            if (campos.size() > LIMITE_COLUNAS) {
                return comColunasDemais(registros, linhaInicio);
            }

            registros.add(new Registro(linhaInicio, campos));
        }

        return new Separacao(registros, null);
    }

    private Separacao comColunasDemais(List<Registro> registros, int linha) {
        return new Separacao(registros, new ErroImportacao(linha, null, "Linha com colunas demais (máximo " + LIMITE_COLUNAS + ")"));
    }
}
