package com.tokit.domain.community.service;

import com.tokit.domain.asset.entity.Asset;
import com.tokit.domain.asset.repository.AssetRepository;
import com.tokit.domain.community.entity.Comment;
import com.tokit.domain.community.entity.Post;
import com.tokit.domain.community.repository.CommentRepository;
import com.tokit.domain.community.repository.PostRepository;
import com.tokit.domain.user.entity.User;
import com.tokit.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;

    @Transactional
    public Post createPost(Long userId, Long assetId, String title, String content) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Asset asset = null;
        if (assetId != null) {
            asset = assetRepository.findById(assetId)
                    .orElseThrow(() -> new IllegalArgumentException("Asset not found"));
        }

        Post post = Post.builder()
                .user(user)
                .asset(asset)
                .title(title)
                .content(content)
                .build();

        return postRepository.save(post);
    }

    public Page<Post> getPosts(Long assetId, Pageable pageable) {
        if (assetId != null) {
            return postRepository.findByAssetId(assetId, pageable);
        }
        return postRepository.findAllWithUserAndAsset(pageable);
    }

    public Post getPost(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
    }

    @Transactional
    public void deletePost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));

        if (!post.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("You are not authorized to delete this post");
        }

        postRepository.delete(post);
    }

    @Transactional
    public Comment createComment(Long postId, Long userId, String content) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .content(content)
                .build();

        return commentRepository.save(comment);
    }

    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));

        if (!comment.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("You are not authorized to delete this comment");
        }

        commentRepository.delete(comment);
    }

    public List<Comment> getComments(Long postId) {
        return commentRepository.findByPostIdOrderByCreatedAtAsc(postId);
    }
}
