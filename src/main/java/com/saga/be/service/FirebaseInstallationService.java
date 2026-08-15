package com.saga.be.service;

import com.saga.be.dto.response.FirebaseInstallationResponse;
import com.saga.be.entity.FirebaseInstallation;
import com.saga.be.repository.FirebaseInstallationRepository;
import com.saga.be.security.SagaPrincipal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class FirebaseInstallationService {

    private final FirebaseInstallationRepository installationRepository;

    @Transactional
    public FirebaseInstallationResponse register(SagaPrincipal principal, String rawFid) {
        String fid = normalize(rawFid);
        FirebaseInstallation existing = installationRepository
                .findByFirebaseInstallationId(fid)
                .orElse(null);
        if (existing != null) {
            if (!ownedBy(existing, principal)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Firebase installation is already registered"
                );
            }
            existing.setActive(true);
            existing.setRevokedAt(null);
            existing.setLastRegisteredAt(nowUtc());
            return FirebaseInstallationResponse.from(installationRepository.save(existing));
        }

        FirebaseInstallation installation = FirebaseInstallation.builder()
                .ownerProfileId(principal.localProfileId())
                .ownerRole(principal.applicationRole())
                .firebaseInstallationId(fid)
                .active(true)
                .lastRegisteredAt(nowUtc())
                .build();
        try {
            return FirebaseInstallationResponse.from(
                    installationRepository.saveAndFlush(installation)
            );
        } catch (DataIntegrityViolationException exception) {
            FirebaseInstallation raced = installationRepository
                    .findByFirebaseInstallationId(fid)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Firebase installation registration conflicts with current state"
                    ));
            if (!ownedBy(raced, principal)) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Firebase installation is already registered"
                );
            }
            raced.setActive(true);
            raced.setRevokedAt(null);
            raced.setLastRegisteredAt(nowUtc());
            return FirebaseInstallationResponse.from(installationRepository.save(raced));
        }
    }

    @Transactional
    public FirebaseInstallationResponse unregister(SagaPrincipal principal, UUID installationId) {
        FirebaseInstallation installation = installationRepository
                .findByIdAndOwnerProfileIdAndOwnerRole(
                        installationId,
                        principal.localProfileId(),
                        principal.applicationRole()
                )
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Firebase installation not found"
                ));
        if (installation.isActive()) {
            installation.setActive(false);
            installation.setRevokedAt(nowUtc());
            installationRepository.save(installation);
        }
        return FirebaseInstallationResponse.from(installation);
    }

    private boolean ownedBy(FirebaseInstallation installation, SagaPrincipal principal) {
        return installation.getOwnerProfileId().equals(principal.localProfileId())
                && installation.getOwnerRole() == principal.applicationRole();
    }

    private String normalize(String rawFid) {
        String fid = rawFid == null ? "" : rawFid.trim();
        if (fid.isEmpty() || fid.length() > 255) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Firebase installation ID is invalid"
            );
        }
        return fid;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
