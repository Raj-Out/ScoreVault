package com.scorevault.rubric;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.scorevault.common.ApiException;
import com.scorevault.common.AuditService;

@Service
public class RubricService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public RubricService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public List<CategoryView> categories() {
        return jdbc.query("SELECT category_id, code, name FROM event_categories ORDER BY category_id",
                (rs, i) -> new CategoryView(rs.getInt("category_id"), rs.getString("code"), rs.getString("name")));
    }

    /** Feature 1: creates a rubric template and all its criteria in ONE transaction. */
    @Transactional
    public RubricView create(RubricRequest request, int userId, String ip) {
        BigDecimal total = BigDecimal.ZERO;
        for (CriterionRequest c : request.criteria()) {
            total = total.add(c.weightage());
        }
        if (total.compareTo(HUNDRED) != 0) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Criteria weights must total exactly 100 (currently " + total.stripTrailingZeros().toPlainString() + ").");
        }

        Integer categoryCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM event_categories WHERE category_id = ?", Integer.class, request.categoryId());
        if (categoryCount == null || categoryCount == 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unknown event category.");
        }

        final String name = request.name().trim();
        final String description = request.description() == null ? null : request.description().trim();

        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO rubric_templates (category_id, name, description, created_by) VALUES (?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, request.categoryId());
            ps.setString(2, name);
            if (description == null) {
                ps.setNull(3, Types.VARCHAR);
            } else {
                ps.setString(3, description);
            }
            ps.setInt(4, userId);
            return ps;
        }, keys);
        Number key = keys.getKey();
        if (key == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The rubric could not be saved.");
        }
        int templateId = key.intValue();

        for (CriterionRequest c : request.criteria()) {
            jdbc.update(
                    "INSERT INTO criteria (template_id, name, description, max_score, weightage_percent) VALUES (?, ?, ?, ?, ?)",
                    new Object[] {templateId, c.name().trim(),
                            c.description() == null ? null : c.description().trim(), c.maxScore(), c.weightage()},
                    new int[] {Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.DECIMAL, Types.DECIMAL});
        }

        audit.log(userId, "RUBRIC_CREATED", "rubric_templates", String.valueOf(templateId), name, ip);
        return list().stream().filter(r -> r.id() == templateId).findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "The rubric could not be loaded."));
    }

    public List<RubricView> list() {
        Map<Integer, List<RubricView.CriterionView>> criteriaByTemplate = new LinkedHashMap<>();
        jdbc.query("SELECT criterion_id, template_id, name, description, max_score, weightage_percent "
                        + "FROM criteria ORDER BY template_id, criterion_id",
                rs -> {
                    criteriaByTemplate.computeIfAbsent(rs.getInt("template_id"), k -> new ArrayList<>())
                            .add(new RubricView.CriterionView(
                                    rs.getInt("criterion_id"),
                                    rs.getString("name"),
                                    rs.getString("description"),
                                    rs.getBigDecimal("max_score"),
                                    rs.getBigDecimal("weightage_percent")));
                });

        return jdbc.query(
                "SELECT t.template_id, t.category_id, c.name AS category_name, t.name, t.description, "
                        + "u.full_name AS created_by_name, t.created_at "
                        + "FROM rubric_templates t "
                        + "JOIN event_categories c ON c.category_id = t.category_id "
                        + "JOIN users u ON u.user_id = t.created_by "
                        + "ORDER BY t.template_id",
                (rs, i) -> {
                    int id = rs.getInt("template_id");
                    List<RubricView.CriterionView> criteria = criteriaByTemplate.getOrDefault(id, List.of());
                    BigDecimal total = BigDecimal.ZERO;
                    for (RubricView.CriterionView cv : criteria) {
                        total = total.add(cv.weightage());
                    }
                    return new RubricView(id, rs.getInt("category_id"), rs.getString("category_name"),
                            rs.getString("name"), rs.getString("description"), rs.getString("created_by_name"),
                            rs.getTimestamp("created_at").toInstant().toString(), total, criteria);
                });
    }
}
