package com.spring.jwt.EmployeeFarmerSurvey;

import com.spring.jwt.entity.EmployeeFarmerSurvey;
import com.spring.jwt.entity.User;
import org.springframework.stereotype.Component;

@Component
public class EmployeeFarmerSurveyMapper {

    public EmployeeFarmerSurvey toEntity(EmployeeFarmerSurveyDTO dto, User user) {

        EmployeeFarmerSurvey survey = new EmployeeFarmerSurvey();

        survey.setFormNumber(dto.getFormNumber());
        survey.setFarmerName(dto.getFarmerName().trim().toLowerCase());
        survey.setFarmerMobile(dto.getFarmerMobile());
        survey.setLandArea(dto.getLandArea());
        survey.setAddress(dto.getAddress());
        survey.setTaluka(dto.getTaluka());
        survey.setDistrict(dto.getDistrict());
        survey.setFarmInformation(dto.getFarmInformation());
        survey.setCropDetails(dto.getCropDetails());
        survey.setLivestockDetails(dto.getLivestockDetails());
        survey.setProductionEquipment(dto.getProductionEquipment());
        survey.setSampleCollected(dto.getSampleCollected());
        survey.setVillage(dto.getVillage());
        survey.setUser(user);

        return survey;
    }

    public EmployeeFarmerSurvey toEntityReg(EmployeeFarmerSurveyRegDTO dto, User user) {

        EmployeeFarmerSurvey survey = new EmployeeFarmerSurvey();

        survey.setFarmerName(dto.getFarmerName().trim().toLowerCase());
        survey.setFarmerMobile(dto.getFarmerMobile());
        survey.setLandArea(dto.getLandArea());
        survey.setAddress(dto.getAddress());
        survey.setTaluka(dto.getTaluka());
        survey.setDistrict(dto.getDistrict());
        survey.setFarmInformation(dto.getFarmInformation());
        survey.setCropDetails(dto.getCropDetails());
        survey.setLivestockDetails(dto.getLivestockDetails());
        survey.setProductionEquipment(dto.getProductionEquipment());
        survey.setSampleCollected(dto.getSampleCollected());
        survey.setVillage(dto.getVillage());
        survey.setUser(user);

        return survey;
    }

    public EmployeeFarmerSurveyDTO toDto(EmployeeFarmerSurvey survey) {

        EmployeeFarmerSurveyDTO dto = new EmployeeFarmerSurveyDTO();

        dto.setSurveyId(survey.getSurveyId());
        dto.setFormNumber(survey.getFormNumber());
        dto.setFarmerName(survey.getFarmerName());
        dto.setFarmerMobile(survey.getFarmerMobile());
        dto.setLandArea(survey.getLandArea());
        dto.setAddress(survey.getAddress());
        dto.setTaluka(survey.getTaluka());
        dto.setDistrict(survey.getDistrict());
        dto.setFarmInformation(survey.getFarmInformation());
        dto.setCropDetails(survey.getCropDetails());
        dto.setLivestockDetails(survey.getLivestockDetails());
        dto.setProductionEquipment(survey.getProductionEquipment());
        dto.setSampleCollected(survey.getSampleCollected());
        dto.setFormStatus(survey.getFormStatus());
        dto.setVillage(survey.getVillage());
        dto.setCreatedAt(survey.getCreatedAt());

        if (survey.getUser() != null) {
            dto.setUserId(survey.getUser().getUserId());
        }

        return dto;
    }

    public void patchEntity(EmployeeFarmerSurvey survey,
                            EmployeeFarmerSurveyDTO dto,
                            User user) {

        if (dto.getFarmerName() != null) {
            survey.setFarmerName(dto.getFarmerName().trim().toLowerCase());
        }
        if (dto.getFarmerMobile() != null) {
            survey.setFarmerMobile(dto.getFarmerMobile());
        }
        if (dto.getLandArea() != null) {
            survey.setLandArea(dto.getLandArea());
        }
        if (dto.getAddress() != null) {
            survey.setAddress(dto.getAddress());
        }
        if (dto.getTaluka() != null) {
            survey.setTaluka(dto.getTaluka());
        }
        if (dto.getDistrict() != null) {
            survey.setDistrict(dto.getDistrict());
        }
        if (dto.getFarmInformation() != null) {
            survey.setFarmInformation(dto.getFarmInformation());
        }
        if (dto.getCropDetails() != null) {
            survey.setCropDetails(dto.getCropDetails());
        }
        if (dto.getLivestockDetails() != null) {
            survey.setLivestockDetails(dto.getLivestockDetails());
        }
        if (dto.getProductionEquipment() != null) {
            survey.setProductionEquipment(dto.getProductionEquipment());
        }
        if (dto.getSampleCollected() != null) {
            survey.setSampleCollected(dto.getSampleCollected());
        }
        if (dto.getVillage() != null) {
            survey.setVillage(dto.getVillage());
        }

        if (user != null) {
            survey.setUser(user);
        }
    }
}
