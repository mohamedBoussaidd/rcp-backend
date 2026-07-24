package com.remipreparateur.badge.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Clé composite (club, ton) de {@link BadgeCouleurClub}. */
public class BadgeCouleurClubId implements Serializable {

    private UUID clubId;
    private BadgeTon ton;

    public BadgeCouleurClubId() {
    }

    public BadgeCouleurClubId(UUID clubId, BadgeTon ton) {
        this.clubId = clubId;
        this.ton = ton;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BadgeCouleurClubId that)) return false;
        return Objects.equals(clubId, that.clubId) && ton == that.ton;
    }

    @Override
    public int hashCode() {
        return Objects.hash(clubId, ton);
    }
}
