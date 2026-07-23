package com.saga.be.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.stereotype.Component;

@Component
public class CognitoOidcAuthoritiesMapper implements GrantedAuthoritiesMapper {

    private final CognitoRoleResolver roleResolver;

    public CognitoOidcAuthoritiesMapper(CognitoRoleResolver roleResolver) {
        this.roleResolver = roleResolver;
    }

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(
            Collection<? extends GrantedAuthority> authorities
    ) {
        Set<GrantedAuthority> mapped = new LinkedHashSet<>();
        Object groupsClaim = null;

        for (GrantedAuthority authority : authorities) {
            if (!authority.getAuthority().startsWith("ROLE_")) {
                mapped.add(authority);
            }
            if (authority instanceof OidcUserAuthority oidcAuthority) {
                groupsClaim = oidcAuthority.getAttributes().get("cognito:groups");
            }
        }

        roleResolver.resolve(groupsClaim)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .ifPresent(mapped::add);
        return mapped;
    }
}
