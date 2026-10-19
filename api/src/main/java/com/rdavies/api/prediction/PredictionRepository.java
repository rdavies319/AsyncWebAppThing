package com.rdavies.api.prediction;

import org.springframework.data.repository.CrudRepository;
import java.util.List;
import java.util.Optional;

public interface PredictionRepository extends CrudRepository<Prediction, Long> {
    List<Prediction> findByRoundId(Long roundId);

    boolean existsByRoundIdAndUserId(Long roundId, Long userId);

    Optional<Prediction> findByRoundIdAndUserId(Long roundId, Long userId);
}
