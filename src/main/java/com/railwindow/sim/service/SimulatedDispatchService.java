package com.railwindow.sim.service;

import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

import com.railwindow.sim.domain.DispatchReceipt;
import com.railwindow.sim.domain.ReceiptKind;
import com.railwindow.sim.domain.WorkPlan;
import com.railwindow.sim.repo.DispatchReceiptRepository;
import org.springframework.stereotype.Service;

/**
 * <b>本地模拟</b>调度台：生成形如 SIM-RCP-… 的受理回执并落库。
 * 不与任何真实调度/联锁系统通信，回执内容仅用于演练留痕。
 */
@Service
public class SimulatedDispatchService {

    private static final DateTimeFormatter PREFIX_FMT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final DispatchReceiptRepository receiptRepository;
    private final Clock clock;
    private final AtomicLong seq = new AtomicLong(0);

    public SimulatedDispatchService(DispatchReceiptRepository receiptRepository, Clock clock) {
        this.receiptRepository = receiptRepository;
        this.clock = clock;
    }

    public DispatchReceipt issue(WorkPlan plan, ReceiptKind kind, String status, String payload) {
        Instant now = clock.instant();
        String receiptNo = "SIM-RCP-" + PREFIX_FMT.format(now.atZone(java.time.ZoneOffset.UTC))
                + "-" + String.format("%04d", seq.incrementAndGet());
        DispatchReceipt receipt = new DispatchReceipt(receiptNo, plan.getId(), kind, status, payload, now);
        return receiptRepository.save(receipt);
    }

    public static String schedulePayload(WorkPlan plan, String note) {
        return "[SIMULATION] 调度模拟受理：计划 " + plan.getPlanNo()
                + "，作业[" + plan.getWorkType() + "]，区段" + plan.sectionCodeSet()
                + "，窗口 " + plan.getWindowStart() + " ~ " + plan.getWindowEnd()
                + "，预计结束 " + plan.getPlannedFinish()
                + (note == null || note.isBlank() ? "" : "，备注：" + note)
                + "。本回执由本地模拟器生成，不代表真实行车许可。";
    }

    public static String startPayload(WorkPlan plan) {
        return "[SIMULATION] 开工许可模拟回执：计划 " + plan.getPlanNo()
                + " 封锁生效，保护区段" + plan.sectionCodeSet()
                + "。本回执由本地模拟器生成，不驱动任何真实设备。";
    }

    public static String closeoutPayload(WorkPlan plan) {
        return "[SIMULATION] 销记模拟回执：计划 " + plan.getPlanNo()
                + " 已逐项复核确认、人员撤离，模拟解除封锁、恢复行车。"
                + "本回执由本地模拟器生成，不驱动任何真实设备。";
    }
}
