-- PostgreSQL extensions and functions required by the entity-search integration tests.
--
-- pg_trgm  — powers the GIN index used by the LIKE search in NamedEntityRepository.
-- unaccent — used by normalize_entity_name() below for standard combining diacritics.
--
-- Run via Testcontainers @Container.withInitScript before Flyway/Hibernate
-- DDL creates the schema, so they are available when the entities table is created.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- Mirror of the normalize_entity_name function created in V45.
-- Keep in sync with V45__normalize_entity_names.sql and
-- EntitySearchService.stripAccents() (NFD_OPAQUE_REPLACEMENTS).
CREATE OR REPLACE FUNCTION normalize_entity_name(input text) RETURNS text AS $$
BEGIN
    RETURN replace(replace(replace(replace(replace(replace(replace(replace(replace(
               unaccent(lower(input)),
           'ø', 'o'),  'æ', 'ae'), 'ł', 'l'),  'đ', 'd'),
           'œ', 'oe'), 'ð', 'd'),  'þ', 'th'), 'ß', 'ss'), '’', '''');
END;
$$ LANGUAGE plpgsql IMMUTABLE;
