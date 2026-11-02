package com.commerce.lookup;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public final class LookupRepositories {
    private LookupRepositories() {}

    public interface LovGroupRepository extends JpaRepository<LovGroup, String> {}

    public interface ListOfValueRepository extends JpaRepository<ListOfValue, Long> {
        List<ListOfValue> findByGroupCodeOrderBySortOrderAscDisplayLabelAsc(String groupCode);

        List<ListOfValue> findByGroupCodeAndActiveTrueOrderBySortOrderAscDisplayLabelAsc(String groupCode);

        Optional<ListOfValue> findByGroupCodeAndValueCode(String groupCode, String valueCode);

        long countByGroupCode(String groupCode);

        @Query("select count(v) from ProductAttributeValue v where v.id.attributeCode in "
                + "(select a.code from AttributeDefinition a where a.lovGroupCode = :group) and v.valueCode = :value")
        long countUsage(@Param("group") String group, @Param("value") String value);
    }

    public interface AttributeDefinitionRepository extends JpaRepository<AttributeDefinition, Long> {
        List<AttributeDefinition> findByActiveTrueOrderBySortOrderAsc();

        List<AttributeDefinition> findAllByOrderBySortOrderAsc();

        Optional<AttributeDefinition> findByCode(String code);
    }
}
