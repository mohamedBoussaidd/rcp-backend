package com.remipreparateur.club.pack.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Clé composite de {@link ClubModule} (club_id + module_code). */
public class ClubModuleId implements Serializable {

    private UUID clubId;
    private String moduleCode;

    public ClubModuleId() {
    }

    public ClubModuleId(UUID clubId, String moduleCode) {
        this.clubId = clubId;
        this.moduleCode = moduleCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClubModuleId that)) return false;
        return Objects.equals(clubId, that.clubId) && Objects.equals(moduleCode, that.moduleCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clubId, moduleCode);
    }
}
