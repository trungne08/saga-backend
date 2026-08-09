package com.saga.be.service;

import com.saga.be.dto.response.ActiveSemesterSettingResponse;
import com.saga.be.entity.ActiveSemesterSetting;
import com.saga.be.entity.Semester;
import com.saga.be.repository.ActiveSemesterSettingRepository;
import com.saga.be.repository.SemesterRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdminActiveSemesterService {

    private final ActiveSemesterSettingRepository activeSemesterSettingRepository;
    private final SemesterRepository semesterRepository;

    @Transactional(readOnly = true)
    public ActiveSemesterSettingResponse current() {
        return activeSemesterSettingRepository.findById(ActiveSemesterSetting.SINGLETON_ID)
                .map(this::response)
                .orElseGet(this::emptyResponse);
    }

    @Transactional
    public ActiveSemesterSettingResponse set(UUID semesterId) {
        Semester semester = semesterRepository.findByIdAndDeletedAtIsNull(semesterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Semester not found"));
        ActiveSemesterSetting setting = activeSemesterSettingRepository
                .findById(ActiveSemesterSetting.SINGLETON_ID)
                .orElseGet(() -> new ActiveSemesterSetting(ActiveSemesterSetting.SINGLETON_ID, null));
        setting.setSemester(semester);
        return response(activeSemesterSettingRepository.save(setting));
    }

    private ActiveSemesterSettingResponse response(ActiveSemesterSetting setting) {
        Semester semester = setting.getSemester();
        return semester == null ? emptyResponse() : new ActiveSemesterSettingResponse(
                semester.getId(), semester.getCode(), semester.getName(), semester.getStartDate(), semester.getEndDate()
        );
    }

    private ActiveSemesterSettingResponse emptyResponse() {
        return new ActiveSemesterSettingResponse(null, null, null, null, null);
    }
}
