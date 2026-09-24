package local.train.blockwindow.service;

import local.train.blockwindow.domain.BlockWindow;
import local.train.blockwindow.domain.DispatchReceipt;
import local.train.blockwindow.repo.DispatchReceiptRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 模拟调度台：只在本地数据库生成一张“调度回执”文本，不外发任何报文、不驱动真实设备。
 */
@Component
public class DispatchSimulator {

    private static final DateTimeFormatter PREFIX_FMT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC);

    private final EntityManager entityManager;
    private final DispatchReceiptRepository receiptRepository;
    private final Clock clock;

    public DispatchSimulator(EntityManager entityManager,
                             DispatchReceiptRepository receiptRepository,
                             Clock clock) {
        this.entityManager = entityManager;
        this.receiptRepository = receiptRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DispatchReceipt issueStartReceipt(BlockWindow window) {
        Instant now = clock.instant();
        String summary = """
                【模拟调度回执 · 非真实行车凭证】
                封锁窗口 %s（%s）已受理开工。
                作业类型：%s；占用区段：%s；
                计划时间(UTC)：%s 至 %s。
                本回执由本地演练服务生成，不发送至任何真实调度台，不得作为现场作业依据。"""
                .formatted(window.getPlanNo(), window.getTitle(), window.getWorkType(),
                        String.join(",", window.getSectionCodes()),
                        window.getPlannedStart(), window.getPlannedEnd());
        return persist(window, "DISPATCHED", summary,
                "模拟受理开工（SIMULATED WORK START ACCEPTANCE）", now);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DispatchReceipt issueReleaseReceipt(BlockWindow window) {
        Instant now = clock.instant();
        String summary = """
                【模拟调度回执 · 非真实行车凭证】
                封锁窗口 %s 销记已受理：复核项全部确认、人员全部撤离。
                区段 %s 模拟恢复（本服务未操作任何真实信号/联锁设备）。
                本回执由本地演练服务生成，不得作为真实开通凭证。"""
                .formatted(window.getPlanNo(), String.join(",", window.getSectionCodes()));
        return persist(window, "CLOSED", summary,
                "模拟销记受理（SIMULATED WORK RELEASE）", now);
    }

    private DispatchReceipt persist(BlockWindow window, String status, String summary, String note,
                                    Instant now) {
        Number seq = (Number) entityManager
                .createNativeQuery("SELECT nextval('dispatch_receipt_no_seq')")
                .getSingleResult();
        String receiptNo = "SIM-DISP-" + PREFIX_FMT.format(now) + "-" + String.format("%05d", seq.longValue());
        DispatchReceipt receipt = new DispatchReceipt(receiptNo, window.getId(),
                now.truncatedTo(ChronoUnit.MILLIS), status, summary, note);
        return receiptRepository.save(receipt);
    }

    public List<DispatchReceipt> receiptsOf(Long windowId) {
        return receiptRepository.findByBlockWindowIdOrderByIssuedAtAsc(windowId);
    }
}
