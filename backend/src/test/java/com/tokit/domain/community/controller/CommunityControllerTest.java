package com.tokit.domain.community.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tokit.domain.community.entity.Post;
import com.tokit.domain.community.repository.PostRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Transactional
public class CommunityControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private User testUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        String uniqueId = UUID.randomUUID().toString();
        testUser = userRepository.save(User.builder()
                .name("Community Author")
                .email("comm.author-" + uniqueId + "@test.com")
                .walletAddress("0xCommAddress" + uniqueId.substring(0, 10))
                .kycStatus(true)
                .build());
    }

    @Test
    @DisplayName("신규 자유 게시글을 작성하고 성공적으로 반환한다.")
    void createPost_Success() throws Exception {
        // given
        CommunityController.CreatePostRequest request = new CommunityController.CreatePostRequest(
                "테스트 게시글 제목",
                "이것은 테스트 게시글 내용입니다.",
                testUser.getId(),
                null
        );

        String jsonRequest = objectMapper.writeValueAsString(request);
        String idempotencyKey = UUID.randomUUID().toString();

        // when & then
        mockMvc.perform(post("/api/posts")
                        .header("X-Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.title").value("테스트 게시글 제목"))
                .andExpect(jsonPath("$.data.content").value("이것은 테스트 게시글 내용입니다."));
    }

    @Test
    @DisplayName("게시글 페이징 목록 조회가 성공적으로 반환한다.")
    void getPosts_Success() throws Exception {
        // given
        postRepository.save(Post.builder()
                .title("기존 게시글")
                .content("내용")
                .user(testUser)
                .build());

        // when & then
        mockMvc.perform(get("/api/posts")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.data.content").isArray());
    }
}
