package com.spring.jwt.EmployeeFarmerSurvey;

import com.spring.jwt.Enums.FormStatus;
import com.spring.jwt.Enums.PhotoType;
import com.spring.jwt.FarmerLabReport.FarmerLabReportRepository;
import com.spring.jwt.FarmerSelfieEmployeeFarmerSurvey.FarmerSelfieEmployeeFarmerSurveyRepository;
import com.spring.jwt.entity.EmployeeFarmerSurvey;
import com.spring.jwt.entity.FarmerSelfieEmployeeFarmerSurvey;
import com.spring.jwt.entity.User;
import com.spring.jwt.exception.ResourceNotFoundException;
import com.spring.jwt.exception.UserAlreadyExistException;
import com.spring.jwt.exception.UserNotFoundExceptions;
import com.spring.jwt.repository.UserRepository;
import com.spring.jwt.utils.SecurityUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;


@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeFarmerSurveyServiceImpl implements EmployeeFarmerSurveyService {

    private final EmployeeFarmerSurveyRepository employeeFarmerSurveyRepository;
    private final UserRepository userRepository;
    private final EmployeeFarmerSurveyMapper surveyMapper;
    private final SecurityUtil securityUtil;
    private final FarmerSelfieEmployeeFarmerSurveyRepository selfieRepository;
    private final FarmerLabReportRepository farmerLabReportRepository;

    @Override
    @Transactional
    public EmployeeFarmerSurveyDTO createSurvey(EmployeeFarmerSurveyRegDTO dto) {

        Long userId = securityUtil.getCurrentUserId();
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundExceptions("User not found with ID: " + userId));
        if (employeeFarmerSurveyRepository.existsByFarmerMobile(dto.getFarmerMobile())) {
            throw new UserAlreadyExistException("Farmer mobile number already exists: " + dto.getFarmerMobile());
        }
        String formNumber = generateFormNumber();
        EmployeeFarmerSurvey survey = surveyMapper.toEntityReg(dto, user);
        survey.setFormStatus(FormStatus.INACTIVE);
        survey.setFormNumber(formNumber);
        survey.setUser(user);
        EmployeeFarmerSurvey savedSurvey = employeeFarmerSurveyRepository.save(survey);
        return surveyMapper.toDto(savedSurvey);
    }




//    @Override
//    public EmployeeFarmerSurveyDTO getSurveyById(Long surveyId) {
//
//        EmployeeFarmerSurvey survey = employeeFarmerSurveyRepository.findById(surveyId).orElseThrow(() -> new ResourceNotFoundException("Survey not found with ID: " + surveyId));
//        EmployeeFarmerSurveyDTO dto = surveyMapper.toDto(survey);
//        FarmerSelfieDTO selfieDTO = new FarmerSelfieDTO();
//        selfieRepository.findBySurvey_SurveyId(surveyId).ifPresentOrElse(
//                        selfie -> {
//                            selfieDTO.setImageUrl(selfie.getImageUrl());
//                            selfieDTO.setTakenAt(selfie.getTakenAt());
//                            selfieDTO.setMessage("Selfie found");
//                            },
//                        () -> {
//                            selfieDTO.setMessage("Farmer selfie not uploaded yet");
//                        }
//                );
//        dto.setFarmerSelfie(selfieDTO);
//        return dto;
//    }

    @Override
    public EmployeeFarmerSurveyDTO getSurveyById(Long surveyId) {

        EmployeeFarmerSurvey survey = employeeFarmerSurveyRepository.findById(surveyId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Survey not found with ID: " + surveyId));

        EmployeeFarmerSurveyDTO dto = surveyMapper.toDto(survey);

        FarmerSelfieDTO selfieDTO = new FarmerSelfieDTO();

        List<FarmerSelfieEmployeeFarmerSurvey> photos =
                selfieRepository.findBySurvey_SurveyId(surveyId);

        if (photos.isEmpty()) {
            selfieDTO.setMessage("Farmer selfie and signature not uploaded yet");
        } else {

            for (FarmerSelfieEmployeeFarmerSurvey photo : photos) {

                if (photo.getPhotoType() == PhotoType.SELFIE) {
                    selfieDTO.setImageUrl(photo.getImageUrl());
                    selfieDTO.setTakenAt(photo.getTakenAt());
                }

                if (photo.getPhotoType() == PhotoType.SIGNATURE) {
                    selfieDTO.setImageUrlS(photo.getImageUrl());
                }
            }

            selfieDTO.setMessage("Farmer photos found");
        }

        dto.setFarmerSelfie(selfieDTO);
        return dto;
    }



    @Override
    @Transactional
    public Page<EmployeeFarmerSurveyDTO> getAllSurveys(Pageable pageable) {

        Page<EmployeeFarmerSurvey> page = employeeFarmerSurveyRepository.findAll(pageable);
        if (pageable.getPageNumber() >= page.getTotalPages()
                && page.getTotalPages() > 0) {
            throw new UserNotFoundExceptions("Page not found. Requested page: " + pageable.getPageNumber());
        }
        return page.map(surveyMapper::toDto);
    }



    @Override
    @Transactional
    public EmployeeFarmerSurveyDTO updateSurvey(Long surveyId,EmployeeFarmerSurveyDTO dto) {
        Long loggedInUserId = securityUtil.getCurrentUserId();
        EmployeeFarmerSurvey existingSurvey = employeeFarmerSurveyRepository.findById(surveyId).orElseThrow(() -> new UserNotFoundExceptions("Survey not found with ID: " + surveyId));
        if (!existingSurvey.getUser().getUserId().equals(loggedInUserId)) {
            throw new UserNotFoundExceptions("You are not authorized to update this survey");
        }
        User user = null;
        if (dto.getUserId() != null) {
            user = userRepository.findById(dto.getUserId()).orElseThrow(() -> new UserNotFoundExceptions("User not found with ID: " + dto.getUserId()));
        }
        surveyMapper.patchEntity(existingSurvey, dto, user);
        EmployeeFarmerSurvey updatedSurvey = employeeFarmerSurveyRepository.save(existingSurvey);
        return surveyMapper.toDto(updatedSurvey);
    }

    @Override
    public void deleteSurvey(Long surveyId) {

        EmployeeFarmerSurvey survey = employeeFarmerSurveyRepository.findById(surveyId).orElseThrow(() -> new UserNotFoundExceptions("Survey not found with ID: " + surveyId));
        selfieRepository.deleteBySurveyId(surveyId);
        farmerLabReportRepository.deleteBySurveyId(surveyId);
        employeeFarmerSurveyRepository.delete(survey);
        log.info("Survey deleted successfully with ID: {}", surveyId);
    }

    @Override
    @Transactional
    public Page<EmployeeFarmerSurveyDTO> getByUserIdSurveys(Long userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundExceptions("User not found with ID: " + userId);}
        Page<EmployeeFarmerSurvey> page = employeeFarmerSurveyRepository.findByUser_UserId(userId, pageable);
        if (pageable.getPageNumber() >= page.getTotalPages()
                && page.getTotalPages() > 0) {
            throw new UserNotFoundExceptions("Page not found. Requested page: " + pageable.getPageNumber());
        }
        return page.map(surveyMapper::toDto);
    }

    @Override
    @Transactional
    public Page<EmployeeFarmerSurveyDTO> getMySurveys(Pageable pageable) {
        Long userId = securityUtil.getCurrentUserId();
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundExceptions("User not found with ID: " + userId);
        }
        Page<EmployeeFarmerSurvey> page = employeeFarmerSurveyRepository.findByUser_UserId(userId, pageable);
        if (pageable.getPageNumber() >= page.getTotalPages()
                && page.getTotalPages() > 0) {
            throw new UserNotFoundExceptions(
                    "Page not found. Requested page: " + pageable.getPageNumber()
            );
        }
        return page.map(surveyMapper::toDto);
    }

    @Override
    public SurveyStatusCountDTO getAllSurveyStatusCount() {

        long completed =
                employeeFarmerSurveyRepository.countByFormStatus(FormStatus.ACTIVE);

        long pending =
                employeeFarmerSurveyRepository.countByFormStatus(FormStatus.INACTIVE);

        return new SurveyStatusCountDTO(pending, completed);
    }


    @Override
    public SurveyStatusCountDTO getSurveyStatusCountByLoggedInUser() {

        Long userId = securityUtil.getCurrentUserId();

        long completed =
                employeeFarmerSurveyRepository.countByFormStatusAndUser_UserId(
                        FormStatus.ACTIVE, userId);

        long pending =
                employeeFarmerSurveyRepository.countByFormStatusAndUser_UserId(
                        FormStatus.INACTIVE, userId);

        return new SurveyStatusCountDTO(pending, completed);
    }
    @Override
    public Page<EmployeeFarmerSurveyDTO> getAllSurveysByStatus(
            FormStatus status, Pageable pageable) {

        Page<EmployeeFarmerSurvey> page =
                employeeFarmerSurveyRepository.findByFormStatus(status, pageable);

        if (page.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No surveys found with status: " + status);
        }

        return page.map(surveyMapper::toDto);
    }

    @Override
    public Page<EmployeeFarmerSurveyDTO> getMySurveysByStatus(
            FormStatus status, Pageable pageable) {

        Long userId = securityUtil.getCurrentUserId();

        Page<EmployeeFarmerSurvey> page =
                employeeFarmerSurveyRepository.findByFormStatusAndUser_UserId(
                        status, userId, pageable);

        if (page.isEmpty()) {
            throw new ResourceNotFoundException(
                    "No surveys found for userId "
                            + userId + " with status: " + status);
        }

        return page.map(surveyMapper::toDto);
    }

    private String generateFormNumber() {

        String yearMonth = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        Optional<EmployeeFarmerSurvey> lastSurvey = employeeFarmerSurveyRepository.findTopByFormNumberStartingWithOrderByFormNumberDesc(yearMonth);
        int nextSequence = 1;
        if (lastSurvey.isPresent()) {
            String lastFormNumber = lastSurvey.get().getFormNumber();
            String lastSeq = lastFormNumber.substring(6);
            nextSequence = Integer.parseInt(lastSeq) + 1;
        }
        return yearMonth + String.format("%04d", nextSequence);
    }

}
