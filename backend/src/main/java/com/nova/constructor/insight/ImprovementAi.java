package com.nova.constructor.insight;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import java.util.Map;

/** Aplica uma melhoria com IA: recebe a proposta, as opções e os arquivos envolvidos e devolve os arquivos alterados. */
public interface ImprovementAi {

    /** Pedido enviado à IA. {@code draft} é a alteração que o NOVA já sabe fazer, quando existe. */
    record Request(String title, String why, String howTo, Map<String, Object> params, String note, List<String> tree, Map<String, String> files,
                   Map<String, String> draft) {}

    /** Formato que a IA devolve (saída estruturada). */
    record Result(
            @JsonPropertyDescription("Arquivos novos ou alterados, cada um com o conteúdo completo") List<FileOut> files,
            @JsonPropertyDescription("Resumo do que foi feito, em uma ou duas frases, em português do Brasil") String summary) {}

    record FileOut(
            @JsonPropertyDescription("Caminho relativo à raiz do projeto, como aparece na lista de arquivos") String path,
            @JsonPropertyDescription("Conteúdo completo do arquivo depois da alteração") String content,
            @JsonPropertyDescription("Por que este arquivo muda, em uma frase") String reason) {}

    String model();

    /** Vazio quando o modelo recusa ou não devolve o formato esperado. */
    java.util.Optional<Result> apply(Request request);
}
