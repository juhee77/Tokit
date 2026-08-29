package com.tokit.domain.community.controller;

import com.tokit.support.TestAuthPrincipalResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.community.entity.Comment;
import com.tokit.domain.community.entity.Post;
import com.tokit.domain.community.service.CommunityService;
import com.tokit.domain.user.entity.User;
import com.tokit.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CommunityControllerTest {

    private MockMvc mockMvc;

    @InjectMocks
    private CommunityController communityController;

    @Mock
    private CommunityService communityService;

    private ObjectMapper objectMapper = new ObjectMapper();
    private User author;
    private Asset testAsset;
    private Post testPost;
    private Comment testComment;

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.standaloneSetup(communityController)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver(),
                        new TestAuthPrincipalResolver(1L, "community.user@tokit.com"))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        author = User.builder()
                .name("Post Author")
                .email("author@tokit.com")
                .walletAddress("0xAUTHOR_ADDRESS_01")
                .build();
        setField(author, "id", 1L);

        testAsset = Asset.builder()
                .name("Yeouido STO")
                .symbol("YEOUIDO-STO")
                .totalSupply(new BigDecimal("100000"))
                .issuePrice(new BigDecimal("10000"))
                .status("상장완료")
                .build();
        setField(testAsset, "id", 10L);

        testPost = Post.builder()
                .user(author)
                .asset(testAsset)
                .title("여의도 STO 분기 배당 분석")
                .content("이번 분기 배당 수익률이 상승했습니다.")
                .build();
        setField(testPost, "id", 100L);

        testComment = Comment.builder()
                .post(testPost)
                .user(author)
                .content("유익한 정보 감사드립니다.")
                .build();
        setField(testComment, "id", 500L);
    }

    @Test
    @DisplayName("POST /api/posts: X-Idempotency-Key와 함께 게시글 작성 시 성공 및 HTTP 200을 반환한다.")
    void createPost_Success() throws Exception {
        // Given
        CommunityController.CreatePostRequest request = new CommunityController.CreatePostRequest(
                "여의도 STO 분기 배당 분석", "이번 분기 배당 수익률이 상승했습니다.", 10L
        );

        when(communityService.createPost(any(), any(), any(), any())).thenReturn(testPost);

        // When & Then
        mockMvc.perform(post("/api/posts")
                        .header("X-Idempotency-Key", "uuid-v4-post-key-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.title").value("여의도 STO 분기 배당 분석"));
    }

    @Test
    @DisplayName("GET /api/posts: 페이징된 게시글 목록 조회가 성공하고 HTTP 200을 반환한다.")
    void getPosts_Success() throws Exception {
        // Given
        when(communityService.getPosts(any(), any())).thenReturn(new PageImpl<>(List.of(testPost), PageRequest.of(0, 20), 1));

        // When & Then
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content[0].id").value(100));
    }

    @Test
    @DisplayName("GET /api/posts/{id}: 게시글 상세 조회가 성공하고 HTTP 200을 반환한다.")
    void getPost_Success() throws Exception {
        // Given
        when(communityService.getPost(100L)).thenReturn(testPost);

        // When & Then
        mockMvc.perform(get("/api/posts/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(100));
    }

    @Test
    @DisplayName("DELETE /api/posts/{id}: 작성자 본인 확인 후 게시글 삭제 시 성공 및 HTTP 200을 반환한다.")
    void deletePost_Success() throws Exception {
        // Given
        doNothing().when(communityService).deletePost(100L, 1L);

        // When & Then
        mockMvc.perform(delete("/api/posts/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200));
    }

    @Test
    @DisplayName("POST /api/posts/{id}/comments: X-Idempotency-Key와 함께 댓글 작성 시 성공 및 HTTP 200을 반환한다.")
    void createComment_Success() throws Exception {
        // Given
        CommunityController.CreateCommentRequest request = new CommunityController.CreateCommentRequest(
                "유익한 정보 감사드립니다."
        );

        when(communityService.createComment(any(), any(), any())).thenReturn(testComment);

        // When & Then
        mockMvc.perform(post("/api/posts/100/comments")
                        .header("X-Idempotency-Key", "uuid-v4-comment-key-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.id").value(500));
    }
}
