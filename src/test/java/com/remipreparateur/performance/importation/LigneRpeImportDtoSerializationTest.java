package com.remipreparateur.performance.importation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remipreparateur.performance.importation.dto.LigneRpeImportDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Garde-fou de sérialisation JSON du champ {@code repondu}.
 *
 * <p>Contexte : le champ s'appelait {@code aRepondu} ; Lombok en générait le getter
 * {@code isARepondu()} que Jackson sérialisait sous « arepondu » ≠ « aRepondu ». Le front lisait
 * {@code undefined}, grisait toutes les lignes et affichait « Aucune ligne à importer » (bug app du
 * 2026-07-22). Renommé {@code repondu} → clé unique « repondu ». Ce test échoue si un renommage
 * futur réintroduit le piège de casse. Le test service ne le voyait pas (pas de passage par JSON).
 */
class LigneRpeImportDtoSerializationTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void repondu_sort_sous_une_cle_json_unique_et_propre() throws Exception {
        LigneRpeImportDto dto = new LigneRpeImportDto();
        dto.setRepondu(true);

        String json = om.writeValueAsString(dto);

        assertThat(json).contains("\"repondu\":true");
        assertThat(json).doesNotContain("arepondu");
        assertThat(json).doesNotContain("aRepondu");
    }

    @Test
    void repondu_fait_un_aller_retour_json_fidele() throws Exception {
        LigneRpeImportDto dto = new LigneRpeImportDto();
        dto.setRepondu(true);

        LigneRpeImportDto relu = om.readValue(om.writeValueAsString(dto), LigneRpeImportDto.class);

        assertThat(relu.isRepondu()).isTrue();
    }
}
