-- Audio assets v2: content-addressed, pipeline-generated audio.
-- The v1 rows were placeholder metadata without real audio files; the audio
-- pipeline (tools/audio_pipeline.py) is the owner of this table from now on.

DELETE FROM audio_asset;

ALTER TABLE audio_asset
    DROP COLUMN asset_key,
    DROP COLUMN url,
    DROP COLUMN mime_type,
    DROP COLUMN created_at,
    ADD COLUMN sha256     VARCHAR(64) NOT NULL,
    ADD COLUMN opus_path  VARCHAR(500) NOT NULL,
    ADD COLUMN mp3_path   VARCHAR(500) NOT NULL,
    ADD COLUMN bytes      BIGINT NOT NULL DEFAULT 0;

ALTER TABLE audio_asset
    ADD CONSTRAINT uq_audio_asset_sha256 UNIQUE (sha256);
