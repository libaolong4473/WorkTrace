package com.worktrace.service.impl;

import com.worktrace.database.ActivityRepository;
import com.worktrace.database.WorkSessionRepository;
import com.worktrace.model.ActivityBlock;
import com.worktrace.model.WorkSession;
import com.worktrace.service.WorkSessionService;
import com.worktrace.timeline.WorkSessionGenerator;
import com.worktrace.util.LogUtil;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * WorkSessionService 实现。
 *
 * 工作流程：
 *   1. 从 ActivityRepository 查询当日 ActivityBlock
 *   2. 使用 WorkSessionGenerator 聚合为 WorkSession
 *   3. 持久化到 work_session 表（先删后插，保证幂等）
 *   4. 返回聚合结果
 */
public class WorkSessionServiceImpl implements WorkSessionService {

    private final ActivityRepository activityRepo;
    private final WorkSessionRepository sessionRepo;
    private final WorkSessionGenerator generator;

    public WorkSessionServiceImpl(ActivityRepository activityRepo, WorkSessionRepository sessionRepo) {
        this.activityRepo = activityRepo;
        this.sessionRepo  = sessionRepo;
        this.generator    = new WorkSessionGenerator();
    }

    @Override
    public List<WorkSession> getDailySessions(LocalDate date) {
        try {
            // 1. 查询当日 ActivityBlock
            List<ActivityBlock> blocks = activityRepo.findByDate(date);
            if (blocks.isEmpty()) {
                return List.of();
            }

            // 2. 聚合为 WorkSession
            List<WorkSession> sessions = generator.generate(blocks);

            // 3. 持久化（先删后插，保证幂等）
            sessionRepo.deleteByDate(date);
            if (!sessions.isEmpty()) {
                sessionRepo.batchInsert(sessions);
            }

            LogUtil.info("工作会话: " + date + " → " + sessions.size() + " 个会话");
            return sessions;
        } catch (Exception e) {
            LogUtil.error("查询工作会话失败: " + e.getMessage());
            return List.of();
        }
    }

    @Override
    public int getSessionCount(LocalDate date) {
        return getDailySessions(date).size();
    }

    @Override
    public long getTotalWorkMinutes(LocalDate date) {
        return getDailySessions(date).stream()
            .mapToLong(WorkSession::getDurationMinutes)
            .sum();
    }

    @Override
    public List<WorkSession> getSessionsByDateRange(LocalDate from, LocalDate to) {
        try {
            // 逐日聚合，合并结果
            List<WorkSession> allSessions = new ArrayList<>();
            LocalDate current = from;
            while (!current.isAfter(to)) {
                allSessions.addAll(getDailySessions(current));
                current = current.plusDays(1);
            }
            return allSessions;
        } catch (Exception e) {
            LogUtil.error("查询日期范围工作会话失败: " + e.getMessage());
            return List.of();
        }
    }
}
