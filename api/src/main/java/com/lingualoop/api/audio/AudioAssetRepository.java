package com.lingualoop.api.audio;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AudioAssetRepository extends JpaRepository<AudioAsset, Long> {

    Optional<AudioAsset> findBySha256(String sha256);
}
