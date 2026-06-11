package com.worktrace.database;

import com.worktrace.model.ProjectInfo;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ProjectInfo 持久化仓库。
 * 负责 project_info 表的全部读写操作。
 *
 * 功能清单：
 *   - 插入 / 忽略重复插入
 *   - 查询全部项目
 *   - 按根路径精确查找
 *   - 按名称模糊搜索
 *   - 判断某路径是否属于已知项目
 *   - 更新项目信息
 *   - 删除项目
 */
public class ProjectRepository {

    private final Connection conn;

    public ProjectRepository() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    // ==================== 写入 ====================

    /**
     * 插入项目信息。如果 root_path 已存在则忽略(不报错)。
     * 返回自增 ID，已存在时返回 -1。
     */
    public long insert(ProjectInfo project) throws SQLException {
        String sql = """
            INSERT OR IGNORE INTO project_info (project_name, root_path)
            VALUES (?, ?)
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, project.getProjectName());
            ps.setString(2, project.getRootPath());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    /**
     * 插入或更新项目信息。
     * root_path 相同时更新 project_name。
     */
    public long upsert(ProjectInfo project) throws SQLException {
        String sql = """
            INSERT INTO project_info (project_name, root_path)
            VALUES (?, ?)
            ON CONFLICT(root_path) DO UPDATE SET project_name = excluded.project_name
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, project.getProjectName());
            ps.setString(2, project.getRootPath());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    /**
     * 更新项目信息(按 ID)。
     */
    public boolean update(ProjectInfo project) throws SQLException {
        String sql = """
            UPDATE project_info SET project_name = ?, root_path = ?
            WHERE id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, project.getProjectName());
            ps.setString(2, project.getRootPath());
            ps.setLong(3, project.getId());
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * 删除项目(按 ID)。
     */
    public boolean deleteById(long id) throws SQLException {
        String sql = "DELETE FROM project_info WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        }
    }

    // ==================== 查询 ====================

    /**
     * 查询全部已注册项目。
     */
    public List<ProjectInfo> findAll() throws SQLException {
        String sql = """
            SELECT id, project_name, root_path
            FROM project_info
            ORDER BY project_name ASC
            """;
        return executeQuery(sql);
    }

    /**
     * 分页查询项目。
     */
    public PageResult<ProjectInfo> findAllPaged(int page, int pageSize) throws SQLException {
        String countSql = "SELECT COUNT(*) FROM project_info";
        String dataSql  = """
            SELECT id, project_name, root_path
            FROM project_info
            ORDER BY project_name ASC
            LIMIT ? OFFSET ?
            """;
        long total = executeCount(countSql);
        List<ProjectInfo> data = executeQuery(dataSql, pageSize, (page - 1) * pageSize);
        return new PageResult<>(data, total, page, pageSize);
    }

    /**
     * 按 ID 查找。
     */
    public Optional<ProjectInfo> findById(long id) throws SQLException {
        String sql = """
            SELECT id, project_name, root_path
            FROM project_info
            WHERE id = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    /**
     * 按根路径精确查找。
     */
    public Optional<ProjectInfo> findByRootPath(String rootPath) throws SQLException {
        String sql = """
            SELECT id, project_name, root_path
            FROM project_info
            WHERE root_path = ?
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, rootPath);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    /**
     * 按项目名称模糊搜索。
     */
    public List<ProjectInfo> findByNameLike(String keyword) throws SQLException {
        String sql = """
            SELECT id, project_name, root_path
            FROM project_info
            WHERE project_name LIKE ?
            ORDER BY project_name ASC
            """;
        return executeQuery(sql, "%" + keyword + "%");
    }

    /**
     * 判断指定文件路径是否属于某个已注册项目。
     * 匹配逻辑：filePath 以某个项目的 root_path 为前缀。
     * 返回匹配到的项目(最长前缀匹配，即最精确的项目)。
     */
    public Optional<ProjectInfo> findProjectForPath(String filePath) throws SQLException {
        String sql = """
            SELECT id, project_name, root_path
            FROM project_info
            WHERE ? LIKE root_path || '%'
            ORDER BY LENGTH(root_path) DESC
            LIMIT 1
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filePath);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    // ==================== 统计 ====================

    /**
     * 项目统计数据。
     */
    public record ProjectStats(
        long projectId,
        String projectName,
        String rootPath,
        long activityCount,
        long totalMinutes,
        long filesModified,
        String lastActiveTime
    ) {}

    /**
     * 查询所有项目的统计信息。
     * 通过 file_event.path LIKE project_info.root_path || '%' 关联。
     *
     * @param date 统计日期(当天)
     */
    public List<ProjectStats> getProjectStats(LocalDate date) throws SQLException {
        String sql = """
            SELECT
                p.id AS project_id,
                p.project_name,
                p.root_path,
                COUNT(DISTINCT ab.id) AS activity_count,
                COALESCE(SUM(MAX(1, ROUND((julianday(ab.end_time) - julianday(ab.start_time)) * 1440))), 0) AS total_minutes,
                (SELECT COUNT(DISTINCT fe.path)
                 FROM file_event fe
                 WHERE fe.path LIKE p.root_path || '%'
                   AND fe.event_time >= ? AND fe.event_time < ?
                ) AS files_modified,
                (SELECT MAX(fe2.event_time)
                 FROM file_event fe2
                 WHERE fe2.path LIKE p.root_path || '%'
                ) AS last_active
            FROM project_info p
            LEFT JOIN activity_block ab
                ON ab.start_time >= ? AND ab.start_time < ?
            GROUP BY p.id, p.project_name, p.root_path
            ORDER BY total_minutes DESC
            """;
        String from = date.atStartOfDay().toString();
        String to   = date.plusDays(1).atStartOfDay().toString();

        List<ProjectStats> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, from);
            ps.setString(2, to);
            ps.setString(3, from);
            ps.setString(4, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new ProjectStats(
                        rs.getLong("project_id"),
                        rs.getString("project_name"),
                        rs.getString("root_path"),
                        rs.getLong("activity_count"),
                        rs.getLong("total_minutes"),
                        rs.getLong("files_modified"),
                        rs.getString("last_active")
                    ));
                }
            }
        }
        return result;
    }

    // ==================== 内部工具 ====================

    private long executeCount(String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        }
    }

    private List<ProjectInfo> executeQuery(String sql, Object... params) throws SQLException {
        List<ProjectInfo> result = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setParams(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        }
        return result;
    }

    private void setParams(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    private ProjectInfo mapRow(ResultSet rs) throws SQLException {
        ProjectInfo p = new ProjectInfo();
        p.setId(rs.getLong("id"));
        p.setProjectName(rs.getString("project_name"));
        p.setRootPath(rs.getString("root_path"));
        return p;
    }
}
