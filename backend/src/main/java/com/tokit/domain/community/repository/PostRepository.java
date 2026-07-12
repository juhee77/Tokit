package com.tokit.domain.community.repository;

import com.tokit.domain.community.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRepository extends JpaRepository<Post, Long> {

    @Query("SELECT p FROM Post p LEFT JOIN FETCH p.user LEFT JOIN FETCH p.asset WHERE p.asset.id = :assetId")
    Page<Post> findByAssetId(@Param("assetId") Long assetId, Pageable pageable);

    @Query(value = "SELECT p FROM Post p LEFT JOIN FETCH p.user LEFT JOIN FETCH p.asset",
           countQuery = "SELECT count(p) FROM Post p")
    Page<Post> findAllWithUserAndAsset(Pageable pageable);
}
