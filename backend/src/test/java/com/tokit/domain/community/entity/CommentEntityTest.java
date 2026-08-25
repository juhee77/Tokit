package com.tokit.domain.community.entity;

import com.tokit.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommentEntityTest {

    @Test
    @DisplayName("Comment 엔티티 생성: 토론 게시글 댓글 작성자, 원본 게시글 및 댓글 본문 내용이 정확히 유지된다.")
    void builder_StoresCommentDetailsCorrectly() {
        // Given
        User commenter = User.builder().name("InvestorA").email("investorA@tokit.com").build();
        Post post = Post.builder().title("배당금 전망 토론").content("본문 내용").build();

        // When
        Comment comment = Comment.builder()
                .post(post)
                .user(commenter)
                .content("좋은 분석 감사합니다! 청약 신청 완료했습니다.")
                .build();

        // Then
        assertThat(comment.getPost()).isEqualTo(post);
        assertThat(comment.getUser()).isEqualTo(commenter);
        assertThat(comment.getContent()).isEqualTo("좋은 분석 감사합니다! 청약 신청 완료했습니다.");
    }
}
