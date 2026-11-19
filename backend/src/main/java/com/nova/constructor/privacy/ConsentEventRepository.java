package com.nova.constructor.privacy;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentEventRepository extends JpaRepository<ConsentEvent, Long> {

    List<ConsentEvent> findByUserIdOrderByOccurredAtAscIdAsc(UUID userId);

    void deleteByUserId(UUID userId);
}
