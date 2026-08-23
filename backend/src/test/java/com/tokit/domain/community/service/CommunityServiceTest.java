package com.tokit.domain.community.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.community.entity.Comment;
import com.tokit.domain.community.entity.Post;
import com.tokit.domain.community.repository.CommentRepository;
import com.tokit.domain.community.repository.PostRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @InjectMocks
    private CommunityService communityService;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AssetRepository assetRepository;

    private User author;
    private User stranger;
    private Asset testAsset;
    private Post testPost;

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        author = User.builder()
                .name("Post Author")
                .email("author@tokit.com")
                .walletAddress("0xAUTHOR_ADDRESS_01")
                .build();
        setField(author, "id", 1L);

        stranger = User.builder()
                .name("Other User")
                .email("stranger@tokit.com")
                .walletAddress("0xSTRANGER_ADDRESS_02")
                .build();
        setField(stranger, "id", 2L);

        testAsset = Asset.builder()
                .name("Gangnam Landmark STO")
                .symbol("GANGNAM-STO")
                .totalSupply(new BigDecimal("100000"))
                .issuePrice(new BigDecimal("10000"))
                .status("상장완료")
                .build();
        setField(testAsset, "id", 10L);

        testPost = Post.builder()
                .user(author)
                .asset(testAsset)
                .title("강남 STO 3분기 배당금 전망 분석")
                .content("이번 분기 임대 수익률이 상승하여 배당율이 6.5%로 예상됩니다.")
                .build();
        setField(testPost, "id", 100L);
    }

    @Test
    @DisplayName("createPost: 투자자가 STO 자산 커뮤니티에 작성한 게실글이 정상적으로 영속화된다.")
    void createPost_Success() {
        // Given
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(assetRepository.findById(10L)).thenReturn(Optional.of(testAsset));
        when(postRepository.save(any(Post.class))).thenReturn(testPost);

        // When
        Post result = communityService.createPost(1L, 10L, "강남 STO 3분기 배당금 전망 분석", "내용");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("강남 STO 3분기 배당금 전망 분석");
        verify(postRepository, times(1)).save(any(Post.class));
    }

    @Test
    @DisplayName("getPosts: 자산ID(10) 조건으로 페이징된 커뮤니티 게시글 목록을 반환한다.")
    void getPosts_PagedSuccess() {
        // Given
        Pageable pageable = PageRequest.of(0, 10);
        Page<Post> page = new PageImpl<>(List.of(testPost));
        when(postRepository.findByAssetId(10L, pageable)).thenReturn(page);

        // When
        Page<Post> result = communityService.getPosts(10L, pageable);

        // Then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTitle()).contains("배당금 전망");
    }

    @Test
    @DisplayName("deletePost: 작성자가 자신의 게시글 삭제 요청 시 정상 처리된다.")
    void deletePost_Author_Success() {
        // Given
        when(postRepository.findById(100L)).thenReturn(Optional.of(testPost));

        // When
        communityService.deletePost(100L, 1L);

        // Then
        verify(postRepository, times(1)).delete(testPost);
    }

    @Test
    @DisplayName("deletePost: 타인이 타인의 게시글 삭제 시도 시 예외가 발생한다.")
    void deletePost_Stranger_ThrowsException() {
        // Given
        when(postRepository.findById(100L)).thenReturn(Optional.of(testPost));

        // When & Then
        assertThatThrownBy(() -> communityService.deletePost(100L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("You are not authorized to delete this post");
    }

    @Test
    @DisplayName("createComment: 게시글에 신규 댓글 등록 시 영속화된다.")
    void createComment_Success() throws Exception {
        // Given
        Comment comment = Comment.builder()
                .post(testPost)
                .user(stranger)
                .content("좋은 배당 정보 감사합니다!")
                .build();
        setField(comment, "id", 500L);

        when(postRepository.findById(100L)).thenReturn(Optional.of(testPost));
        when(userRepository.findById(2L)).thenReturn(Optional.of(stranger));
        when(commentRepository.save(any(Comment.class))).thenReturn(comment);

        // When
        Comment result = communityService.createComment(100L, 2L, "좋은 배당 정보 감사합니다!");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEqualTo("좋은 배당 정보 감사합니다!");
        verify(commentRepository, times(1)).save(any(Comment.class));
    }
}
