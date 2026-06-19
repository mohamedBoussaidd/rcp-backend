package com.remipreparateur.auth.rbac;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Permission accordée à un rôle (1 ligne = 1 permission). La permission est stockée sous son
 * code ({@code seances:write}) — qui est aussi la chaîne d'autorité Spring Security.
 */
@Entity
@Table(name = "role_permission")
@IdClass(RolePermission.Id.class)
@Getter
@Setter
public class RolePermission {

    @jakarta.persistence.Id
    @Column(name = "role_id")
    private UUID roleId;

    @jakarta.persistence.Id
    @Column(name = "permission", length = 50)
    private String permission;

    /** Clé composite (role_id, permission). */
    public static class Id implements Serializable {
        private UUID roleId;
        private String permission;

        public Id() {}

        public Id(UUID roleId, String permission) {
            this.roleId = roleId;
            this.permission = permission;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Id id)) return false;
            return Objects.equals(roleId, id.roleId) && Objects.equals(permission, id.permission);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roleId, permission);
        }
    }
}
