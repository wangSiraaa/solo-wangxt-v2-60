package local.train.blockwindow.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public class Requests {

    private Requests() {
    }

    public record CreatePlanRequest(
            @NotBlank String planNo,
            @NotBlank String title,
            @NotBlank String workType,
            @NotEmpty @Size(min = 1) List<@NotBlank String> sectionCodes,
            @NotNull Instant plannedStart,
            @NotNull Instant plannedEnd,
            @NotBlank String zoneId) {
    }

    public record AssignPersonnelRequest(
            @NotBlank String badge,
            @NotBlank String roleOnPlan) {
    }

    public record ProtectionRequirementRequest(
            @NotBlank String sectionCode,
            @NotBlank String requirementCode,
            @NotBlank String label) {
    }

    public record ConfirmProtectionRequest(
            @NotBlank String confirmedBy) {
    }

    public record StartWorkRequest(
            /** 幂等键：开工确认重试时必须带同一把键，服务端重放首次结果。 */
            String idempotencyKey,
            String operatorBadge) {
    }

    /**
     * 销记前复核项：
     * REVIEW     —— 对某条保护条件的撤除/复原复核（refKey = 保护条件码）
     * EVACUATION —— 某名人员撤离确认（refKey = 证件号）
     */
    public record ReleaseCheckRequest(
            @NotBlank String checkType,
            @NotBlank String refKey,
            String sectionCode,
            @NotBlank String label,
            @NotBlank String confirmedBy,
            @NotNull Boolean confirmed) {
    }

    public record ReleaseRequest(
            String idempotencyKey,
            @NotBlank String operatorBadge) {
    }

    public record PersonnelUpsertRequest(
            @NotBlank String badge,
            @NotBlank String name,
            @NotBlank String role,
            @NotBlank String qualification,
            @NotEmpty List<@NotBlank String> qualifiedWork,
            @NotNull Instant qualValidFrom,
            @NotNull Instant qualValidUntil) {
    }
}
