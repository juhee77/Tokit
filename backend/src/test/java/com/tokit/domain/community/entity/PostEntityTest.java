package com.tokit.domain.community.entity;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PostEntityTest {

    @Test
    @DisplayName("Post 엔티티 생성: 투자자 커뮤니티 게시글 제목, 내용, 연관 기초자산 및 댓글 리스트가 올바르게 캡슐화된다.")
    void builder_StoresPostDetailsCorrectly() {
        // Given
        User author = User.builder().name("Juhee").email("juhee@tokit.com").build();
        Asset asset = Asset.builder().name("Songdo STO").symbol("SONGDO-STO").build();

        // When
        Post post = Post.builder()
                .user(author)
                .asset(asset)
                .title("송도 STO 3분기 배당금 전망 토론")
                .content("이번 3분기 배당 수익률 예상치는 연 7.2% 수준입니다.")
                .build();

        // Then
        assertThat(post.getUser()).isEqualTo(author);
        assertThat(post.getAsset()).isEqualTo(asset);
        assertThat(post.getTitle()).isEqualTo("송도 STO 3분기 배당금 전망 토론");
        assertThat(post.getContent()).contains("연 7.2%");
        assertThat(post.getComments()).isEmpty();
    }
}
