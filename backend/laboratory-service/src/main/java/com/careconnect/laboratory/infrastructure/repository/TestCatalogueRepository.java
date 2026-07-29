package com.careconnect.laboratory.infrastructure.repository;

import com.careconnect.laboratory.domain.TestCatalogue;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestCatalogueRepository extends JpaRepository<TestCatalogue, UUID> {

    // Controller maps this straight to a DTO (CatalogueResponse::from), which
    // reads .analytes — eager-fetch it so that doesn't need a transaction.
    @EntityGraph(attributePaths = "analytes")
    @Query("""
            select t from TestCatalogue t
            where t.active = true
              and (:q is null or :q = ''
                   or lower(t.name) like lower(concat('%', :q, '%'))
                   or lower(t.code) like lower(concat('%', :q, '%')))
            order by t.department, t.name
            """)
    List<TestCatalogue> search(@Param("q") String query);
}
