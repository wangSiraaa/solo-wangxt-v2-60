package local.train.blockwindow.repo;

import jakarta.persistence.LockModeType;
import local.train.blockwindow.domain.BlockWindow;
import local.train.blockwindow.domain.WindowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BlockWindowRepository extends JpaRepository<BlockWindow, Long> {

    Optional<BlockWindow> findByPlanNo(String planNo);

    boolean existsByPlanNo(String planNo);

    /**
     * 时间重叠的未销记计划候选（半开区间，首尾相接不算重叠）。
     * 仅用于缩小候选集合；是否构成冲突必须再走互斥作业矩阵 + 区段求交，
     * <b>不能</b>因为区段编号相同就直接判冲突。状态在实体中以字符串存储，故绑定状态名。
     */
    @Query("""
            SELECT w FROM BlockWindow w
            WHERE w.status IN (:activeStatusNames)
              AND w.plannedStart < :end
              AND :start < w.plannedEnd
            """)
    List<BlockWindow> findActiveOverlapping(@Param("start") Instant start,
                                            @Param("end") Instant end,
                                            @Param("activeStatusNames") List<String> activeStatusNames);

    /**
     * 开工闸门专用：对时间重叠的未销记窗口加行级写锁（SELECT ... FOR UPDATE）。
     * 两个计划并发竞争同一区段时在此串行化：先提交者转为 IN_PROGRESS 并持锁，
     * 后到者等到锁释放后再按互斥矩阵评估，必然看到先到者已在作业。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT w FROM BlockWindow w
            WHERE w.status IN (:activeStatusNames)
              AND w.plannedStart < :end
              AND :start < w.plannedEnd
            """)
    List<BlockWindow> lockActiveOverlapping(@Param("start") Instant start,
                                            @Param("end") Instant end,
                                            @Param("activeStatusNames") List<String> activeStatusNames);

    List<BlockWindow> findByStatusIn(List<WindowStatus> statuses);
}
