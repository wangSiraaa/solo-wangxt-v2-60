package local.train.blockwindow.web;

import local.train.blockwindow.domain.DispatchReceipt;
import local.train.blockwindow.domain.ProtectionRequirement;

import java.time.Instant;
import java.util.List;

public class Responses {

    private Responses() {
    }

    public record PlanView(
            Long id,
            String planNo,
            String title,
            String workType,
            String status,
            List<String> sectionCodes,
            Instant plannedStart,
            Instant plannedEnd,
            boolean crossesMidnightUtc,
            String zoneId,
            Instant startedAt,
            Instant releasedAt,
            List<AssignmentView> assignments,
            List<ProtectionView> protections,
            List<ReceiptView> receipts) {
    }

    public record AssignmentView(String badge, String name, String roleOnPlan,
                                 String qualification,
                                 Instant qualValidUntil,
                                 boolean qualCoversWindow) {
    }

    public record ProtectionView(Long id, String sectionCode, String requirementCode,
                                 String label, boolean confirmed, String confirmedBy,
                                 Instant confirmedAt) {

        public static ProtectionView from(ProtectionRequirement r) {
            return new ProtectionView(r.getId(), r.getSectionCode(), r.getRequirementCode(),
                    r.getLabel(), r.isConfirmed(), r.getConfirmedBy(), r.getConfirmedAt());
        }
    }

    public record ReceiptView(String receiptNo, Instant issuedAt, String dispatchStatus,
                              String displaySummary, boolean simulated, String note) {

        public static ReceiptView from(DispatchReceipt r) {
            return new ReceiptView(r.getReceiptNo(), r.getIssuedAt(), r.getDispatchStatus(),
                    r.getDisplaySummary(), r.isSimulated(), r.getNote());
        }
    }

    public record StartResultView(boolean started, boolean replayed, String planNo,
                                  String status, Instant now, ReceiptView dispatchReceipt,
                                  List<MissingCondition> missingConditions,
                                  String message) {
    }

    public record ReleaseResultView(boolean released, boolean replayed, String planNo,
                                    String status, Instant now, ReceiptView dispatchReceipt,
                                    List<MissingCondition> missingConditions,
                                    String message) {
    }
}
