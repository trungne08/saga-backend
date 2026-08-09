package com.saga.be.repository;

import com.saga.be.dto.response.AdminUserReadResponse;
import com.saga.be.entity.enums.AccountStatus;
import com.saga.be.security.ApplicationRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * A database-paged union over the three independent local profile tables.
 * It never reads Cognito subject identifiers and never materializes all profiles in memory.
 */
@Repository
public class AdminUserReadRepository {

    private static final String UNION = """
            select id, 'ADMIN' as application_role, full_name, email, null as account_status, null as student_code from admin
            union all
            select id, 'LECTURER' as application_role, full_name, email, account_status, null as student_code from lecturer
            union all
            select id, 'STUDENT' as application_role, full_name, email, account_status, student_code from student
            """;

    @PersistenceContext
    private EntityManager entityManager;

    public Page<AdminUserReadResponse> findAll(
            String keyword,
            ApplicationRole role,
            AccountStatus accountStatus,
            Pageable pageable
    ) {
        String where = whereClause(keyword, role, accountStatus);
        String orderBy = " order by lower(full_name) asc, id asc";
        Query contentQuery = entityManager.createNativeQuery(
                "select * from (" + UNION + ") profiles" + where + orderBy
        );
        bind(contentQuery, keyword, role, accountStatus);
        contentQuery.setFirstResult((int) pageable.getOffset());
        contentQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = contentQuery.getResultList();
        List<AdminUserReadResponse> content = rows.stream().map(this::toResponse).toList();

        Query countQuery = entityManager.createNativeQuery(
                "select count(*) from (" + UNION + ") profiles" + where
        );
        bind(countQuery, keyword, role, accountStatus);
        long total = ((Number) countQuery.getSingleResult()).longValue();
        return new PageImpl<>(content, pageable, total);
    }

    private String whereClause(String keyword, ApplicationRole role, AccountStatus accountStatus) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        if (keyword != null && !keyword.isBlank()) {
            where.append(" and (lower(full_name) like :keyword or lower(email) like :keyword or lower(student_code) like :keyword)");
        }
        if (role != null) {
            where.append(" and application_role = :role");
        }
        if (accountStatus != null) {
            where.append(" and account_status = :accountStatus");
        }
        return where.toString();
    }

    private void bind(Query query, String keyword, ApplicationRole role, AccountStatus accountStatus) {
        if (keyword != null && !keyword.isBlank()) {
            query.setParameter("keyword", "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%");
        }
        if (role != null) {
            query.setParameter("role", role.name());
        }
        if (accountStatus != null) {
            query.setParameter("accountStatus", accountStatus.name());
        }
    }

    private AdminUserReadResponse toResponse(Object[] row) {
        return new AdminUserReadResponse(
                UUID.fromString(row[0].toString()),
                ApplicationRole.valueOf(row[1].toString()),
                (String) row[2],
                (String) row[3],
                row[4] == null ? null : AccountStatus.valueOf(row[4].toString()),
                (String) row[5]
        );
    }
}
