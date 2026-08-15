package com.saga.be.repository;

import com.saga.be.entity.ActiveSemesterSetting;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ActiveSemesterSettingRepository extends JpaRepository<ActiveSemesterSetting, Byte> {
    boolean existsBySemesterId(UUID semesterId);
}
