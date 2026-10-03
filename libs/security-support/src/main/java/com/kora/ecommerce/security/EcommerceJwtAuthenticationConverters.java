package com.kora.ecommerce.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

public final class EcommerceJwtAuthenticationConverters {

    private EcommerceJwtAuthenticationConverters() {
    }

    public static JwtAuthenticationConverter keycloakRealmRoles() {
        JwtGrantedAuthoritiesConverter scopeAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();

        authenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new LinkedHashSet<>();
            Collection<GrantedAuthority> scopeAuthorities = scopeAuthoritiesConverter.convert(jwt);
            if (scopeAuthorities != null) {
                authorities.addAll(scopeAuthorities);
            }

            Object realmAccessClaim = jwt.getClaim("realm_access");
            if (realmAccessClaim instanceof Map<?, ?> realmAccess) {
                Object rolesClaim = realmAccess.get("roles");
                if (rolesClaim instanceof Collection<?> roles) {
                    roles.stream()
                            .filter(String.class::isInstance)
                            .map(String.class::cast)
                            .filter(role -> !role.isBlank())
                            .map(EcommerceJwtAuthenticationConverters::roleAuthority)
                            .map(SimpleGrantedAuthority::new)
                            .forEach(authorities::add);
                }
            }

            return authorities;
        });

        return authenticationConverter;
    }

    private static String roleAuthority(String role) {
        if (role.startsWith(SecurityRoles.ROLE_PREFIX)) {
            return role;
        }
        return SecurityRoles.ROLE_PREFIX + role;
    }
}
