package com.tokit.domain.community.controller;

import com.tokit.domain.community.entity.Comment;
import com.tokit.domain.community.entity.Post;
import com.tokit.domain.community.service.CommunityService;
import com.tokit.global.annotation.Idempotent;
import com.tokit.global.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Tag(name = "12. Community (커뮤니티)", description = "토큰증권(STO) 토론방 및 자유게시판 커뮤니티 API")
@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class CommunityController {

    private final CommunityService communityService;

    // --- Request/Response DTO Records ---
    public record CreatePostRequest(
        @NotBlank(message = "Title is required")
        String title,

        @NotBlank(message = "Content is required")
        String content,

        @NotNull(message = "User ID is required")
        Long userId,

        Long assetId
    ) {}

    public record CreateCommentRequest(
        @NotBlank(message = "Content is required")
        String content,

        @NotNull(message = "User ID is required")
        Long userId
    ) {}

    public record CommentResponse(
        Long id,
        String content,
        Long userId,
        String userName,
        LocalDateTime createdAt
    ) {
        public static CommentResponse from(Comment comment) {
            return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getUser().getId(),
                comment.getUser().getName(),
                comment.getCreatedAt()
            );
        }
    }

    public record PostResponse(
        Long id,
        String title,
        String content,
        Long userId,
        String userName,
        Long assetId,
        String assetName,
        int commentsCount,
        LocalDateTime createdAt
    ) {
        public static PostResponse from(Post post) {
            return new PostResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                post.getUser().getId(),
                post.getUser().getName(),
                post.getAsset() != null ? post.getAsset().getId() : null,
                post.getAsset() != null ? post.getAsset().getName() : null,
                post.getComments().size(),
                post.getCreatedAt()
            );
        }
    }

    // --- REST API Endpoints ---

    @PostMapping
    @Idempotent
    @Operation(summary = "신규 게시글 작성", description = "자유게시글 또는 특정 STO 자산 관련 토론글을 작성합니다. (X-Idempotency-Key 필수)")
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestBody @Valid CreatePostRequest request
    ) {
        Post post = communityService.createPost(request.userId(), request.assetId(), request.title(), request.content());
        return ResponseEntity.ok(ApiResponse.success(PostResponse.from(post)));
    }

    @GetMapping
    @Operation(summary = "게시글 목록 페이징 조회", description = "작성된 게시글 목록을 페이징하여 반환합니다. assetId 필터링을 지원합니다.")
    public ResponseEntity<ApiResponse<Page<PostResponse>>> getPosts(
            @RequestParam(value = "assetId", required = false) Long assetId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        Page<PostResponse> posts = communityService.getPosts(assetId, pageable).map(PostResponse::from);
        return ResponseEntity.ok(ApiResponse.success(posts));
    }

    @GetMapping("/{id}")
    @Operation(summary = "게시글 단건 상세 조회", description = "게시글 ID에 해당하는 상세 내용을 반환합니다.")
    public ResponseEntity<ApiResponse<PostResponse>> getPost(@PathVariable("id") Long id) {
        Post post = communityService.getPost(id);
        return ResponseEntity.ok(ApiResponse.success(PostResponse.from(post)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "게시글 삭제", description = "게시글 작성자 본인 확인 후 게시글을 영구 삭제합니다.")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable("id") Long id,
            @RequestParam("userId") Long userId
    ) {
        communityService.deletePost(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/comments")
    @Idempotent
    @Operation(summary = "게시글 댓글 작성", description = "게시글에 신규 댓글을 작성합니다. (X-Idempotency-Key 필수)")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @PathVariable("id") Long id,
            @RequestBody @Valid CreateCommentRequest request
    ) {
        Comment comment = communityService.createComment(id, request.userId(), request.content());
        return ResponseEntity.ok(ApiResponse.success(CommentResponse.from(comment)));
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "게시글 댓글 목록 조회", description = "게시글 ID에 달린 전체 댓글 목록을 반환합니다.")
    public ResponseEntity<ApiResponse<List<CommentResponse>>> getComments(@PathVariable("id") Long id) {
        List<CommentResponse> comments = communityService.getComments(id).stream()
                .map(CommentResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.success(comments));
    }

    @DeleteMapping("/comments/{commentId}")
    @Operation(summary = "댓글 삭제", description = "댓글 작성자 본인 확인 후 댓글을 영구 삭제합니다.")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable("commentId") Long commentId,
            @RequestParam("userId") Long userId
    ) {
        communityService.deleteComment(commentId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
