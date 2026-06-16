package com.trivia501.repository;

import com.trivia501.model.NamedEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NamedEntityRepository extends JpaRepository<NamedEntity, UUID> {

    /**
     * Accent-insensitive substring search scoped to a specific entity type,
     * using the GIN trigram index on {@code normalized_name}.
     * <p>
     * {@code normalized_name} is pre-stripped of accents in Java (via
     * {@code Normalizer.NFD}) before being stored, so the column is already
     * clean.  We apply {@code unaccent(lower(:query))} only to the user-supplied
     * search term so that typing "Agüero" or "aguero" both match "sergio aguero".
     * <p>
     * Results are ranked so that names beginning with the query come first,
     * with alphabetical ordering as a tiebreaker.
     * <p>
     * Example: {@code searchByType("footballer", "aguero", 10)} returns
     * "Sergio Agüero" even though the query contains no accent.
     *
     * @param entityType the pool to search within (e.g. "footballer", "city")
     * @param query      the raw, possibly accent-bearing search term
     * @param limit      maximum number of results to return
     */
    @Query(value = """
            SELECT id, entity_type, display_name, normalized_name, hint, created_at
            FROM   entities
            WHERE  entity_type = :entityType
            AND    normalized_name LIKE '%' || unaccent(lower(:query)) || '%'
            ORDER BY
                CASE WHEN normalized_name LIKE unaccent(lower(:query)) || '%'
                     THEN 0 ELSE 1 END,
                normalized_name
            LIMIT  :limit
            """, nativeQuery = true)
    List<NamedEntity> searchByType(
            @Param("entityType") String entityType,
            @Param("query") String query,
            @Param("limit") int limit
    );

    /** All entities for a given type, sorted alphabetically — used to seed the client-side cache. */
    List<NamedEntity> findAllByEntityTypeOrderByNormalizedName(String entityType);

    /** Exact lookup by type + normalized key — used for upsert deduplication. */
    Optional<NamedEntity> findByEntityTypeAndNormalizedName(String entityType, String normalizedName);

    /** Batch variant of the above — avoids N queries in a materialization loop. */
    List<NamedEntity> findByEntityTypeAndNormalizedNameIn(String entityType, java.util.Set<String> normalizedNames);

    /**
     * Bulk-upserts all rows from the {@code players} source table into the
     * {@code entities} autocomplete table as {@code "footballer"} entities.
     *
     * <p>Uses a single {@code INSERT … ON CONFLICT DO NOTHING} so the operation
     * is atomic, race-condition-free, and scales to tens of thousands of players
     * without issuing one query per row.  The {@code players.normalized_name}
     * column is used directly — it is pre-computed with the same Java
     * {@code Normalizer.NFD} logic as {@link
     * com.trivia501.service.EntitySearchService#stripAccents(String)}, keeping
     * the two normalizations in sync.
     *
     * @return number of rows actually inserted; skipped rows (already in table)
     *         are NOT counted
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO entities (entity_type, display_name, normalized_name, hint)
            SELECT 'footballer', p.name, unaccent(lower(p.name)), p.nationality
            FROM   players p
            ON CONFLICT (entity_type, normalized_name) DO NOTHING
            """, nativeQuery = true)
    int bulkUpsertFootballersFromPlayers();

    /**
     * Count entities grouped by entity_type.
     * Used by the admin debug panel to verify the autocomplete pool is seeded.
     *
     * @return list of [entityType (String), count (Long)] pairs, ordered by count desc
     */
    @Query("SELECT n.entityType, COUNT(n) FROM NamedEntity n GROUP BY n.entityType ORDER BY COUNT(n) DESC")
    List<Object[]> countByEntityType();
}
