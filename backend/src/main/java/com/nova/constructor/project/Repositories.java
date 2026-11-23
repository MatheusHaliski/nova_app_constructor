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

        @Query("select coalesce(sum(v.sizeBytes), 0) from ProjectVersion v, Project p where v.projectId = p.id and v.number = p.currentVersion and p.ownerId = :ownerId")
        long storageUsedBy(@Param("ownerId") UUID ownerId);
    }
}
