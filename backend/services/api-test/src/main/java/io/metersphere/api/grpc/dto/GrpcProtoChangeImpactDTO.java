package io.metersphere.api.grpc.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class GrpcProtoChangeImpactDTO {
    /** BREAKING / WARNING / INFO / NONE */
    private String impactLevel;
    private String impactSummary;
    private List<String> changedServices = new ArrayList<>();
    private List<String> changedMethods = new ArrayList<>();
    private List<GrpcProtoMessageChangeDTO> changedMessages = new ArrayList<>();
    private List<GrpcProtoAffectedDefinitionDTO> affectedDefinitions = new ArrayList<>();
    private List<GrpcProtoAffectedCaseDTO> affectedCases = new ArrayList<>();
}
