package com.tokit.domain.community.dto;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.community.controller.CommunityController.*;
import com.tokit.domain.community.entity.Comment;
import com.tokit.domain.community.entity.Post;
import com.tokit.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommunityDTORecordTest {

    @Test
    @DisplayName("CreatePostRequest DTO 레코드 검증: 게시글 생성 요청 파라미터가 정상 저장된다.")
    void createPostRequest_InstantiationAndAccessors() {
        // Given & When
        CreatePostRequest request = new CreatePostRequest("송도 STO 배당전망", "배당 수익률 관련 질문입니다.", 1L, 2L);

        // Then
        assertThat(request.title()).isEqualTo("송도 STO 배당전망");
        assertThat(request.content()).isEqualTo("배당 수익률 관련 질문입니다.");
        assertThat(request.userId()).isEqualTo(1L);
        assertThat(request.assetId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("CreateCommentRequest DTO 레코드 검증: 댓글 생성 요청 내용과 사용자 ID가 정상 저장된다.")
    void createCommentRequest_InstantiationAndAccessors() {
        // Given & When
        CreateCommentRequest request = new CreateCommentRequest("동의합니다!", 5L);

        // Then
        assertThat(request.content()).isEqualTo("동의합니다!");
        assertThat(request.userId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("CommentResponse 정적 팩토리 메서드 검증: Comment 엔티티로부터 DTO로 정상 매핑된다.")
    void commentResponse_FromFactoryMethod() {
        // Given
        User author = User.builder().name("Juhee").build();
        setField(author, "id", 10L);

        LocalDateTime now = LocalDateTime.now();
        Comment comment = Comment.builder()
                .user(author)
                .content("좋은 의견입니다.")
                .build();
        setField(comment, "id", 100L);
        setField(comment, "createdAt", now);

        // When
        CommentResponse response = CommentResponse.from(comment);

        // Then
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.content()).isEqualTo("좋은 의견입니다.");
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.userName()).isEqualTo("Juhee");
        assertThat(response.createdAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("PostResponse 정적 팩토리 메서드 검증: Post 엔티티 및 연관 자산/댓글 수가 DTO로 정상 매핑된다.")
    void postResponse_FromFactoryMethod() {
        // Given
        User author = User.builder().name("Juhee").build();
        setField(author, "id", 10L);

        Asset asset = Asset.builder().name("Songdo STO").build();
        setField(asset, "id", 50L);

        LocalDateTime now = LocalDateTime.now();
        Post post = Post.builder()
                .user(author)
                .asset(asset)
                .title("토론")
                .content("본문")
                .comments(List.of())
                .build();
        setField(post, "id", 500L);
        setField(post, "createdAt", now);

        // When
        PostResponse response = PostResponse.from(post);

        // Then
        assertThat(response.id()).isEqualTo(500L);
        assertThat(response.title()).isEqualTo("토론");
        assertThat(response.content()).isEqualTo("본문");
        assertThat(response.userId()).isEqualTo(10L);
        assertThat(response.userName()).isEqualTo("Juhee");
        assertThat(response.assetId()).isEqualTo(50L);
        assertThat(response.assetName()).isEqualTo("Songdo STO");
        assertThat(response.commentsCount()).isEqualTo(0);
        assertThat(response.createdAt()).isEqualTo(now);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
