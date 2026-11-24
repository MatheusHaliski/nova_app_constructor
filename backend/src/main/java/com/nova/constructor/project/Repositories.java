package com.nova.constructor.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

final class Repositories {

    private Repositories() {}

    interface ProjectRepository extends JpaRepository<Project, UUID> {

        Optional<Project> findByIdAndOwnerId(UUID id, UUID ownerId);

        List<Project> findByOwnerIdAndStatusOrderByUpdatedAtDesc(UUID ownerId, Project.Status status);

        long countByOwnerIdAndStatusNot(UUID ownerId, Project.Status status);

        /** Projetos listados na rede (Comunidade e Público), com busca por texto e por tecnologia do formulário. */
        @Query("select p from Project p where p.status = com.nova.constructor.project.Project.Status.ACTIVE "
                + "and p.visibility in (com.nova.constructor.project.Project.Visibility.COMMUNITY, com.nova.constructor.project.Project.Visibility.PUBLIC) "
                + "and (:q is null or lower(p.name) like :q or lower(p.description) like :q) "
                + "and (:tech is null or p.specJson like :tech) order by p.publishedAt desc")
        List<Project> findListed(@Param("q") String q, @Param("tech") String tech, org.springframework.data.domain.Pageable page);

        long countByForkedFromId(UUID forkedFromId);
    }

    interface VersionRepository extends JpaRepository<ProjectVersion, Long> {

        Optional<ProjectVersion> findByProjectIdAndNumber(UUID projectId, int number);

        List<ProjectVersion> findByProjectIdOrderByNumberDesc(UUID projectId);
    }

    interface VersionFileRepository extends JpaRepository<VersionFile, Long> {

        List<VersionFile> findByVersionIdOrderByPath(Long versionId);

        Optional<VersionFile> findByVersionIdAndPath(Long versionId, String path);
    }

    interface BlobRepository extends JpaRepository<FileBlob, String> {

        /** Remove conteúdos que nenhuma versão referencia mais (após excluir um projeto). */
        @Modifying
        @Query("delete from FileBlob b where not exists (select 1 from VersionFile f where f.blobSha = b.sha256)")
        int deleteOrphans();

        /** Bytes realmente guardados para um dono: cada conteúdo distinto conta uma vez, em todas as versões. */
        @Query("select coalesce(sum(b.sizeBytes), 0) from FileBlob b where exists (select 1 from VersionFile f, ProjectVersion v, Project p "
                + "where f.blobSha = b.sha256 and f.versionId = v.id and v.projectId = p.id and p.ownerId = :ownerId)")
        long storageUsedBy(@Param("ownerId") UUID ownerId);
    }
}
