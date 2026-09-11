package com.ai.service.impl;

import com.ai.constant.UserConstant;
import com.ai.model.dto.blog.BlogPostQueryRequest;
import com.ai.model.entity.BlogPost;
import com.ai.model.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlogPostServiceImplTest {

    @Test
    void anonymousCannotViewDraft() {
        BlogPost draft = BlogPost.builder().id(1L).userId(9L).status(0).build();
        assertFalse(BlogPostServiceImpl.canViewPost(draft, null));
    }

    @Test
    void anonymousCanViewPublished() {
        BlogPost published = BlogPost.builder().id(1L).userId(9L).status(1).build();
        assertTrue(BlogPostServiceImpl.canViewPost(published, null));
    }

    @Test
    void authorCanViewOwnDraft() {
        User author = user(9L, UserConstant.DEFAULT_ROLE);
        BlogPost draft = BlogPost.builder().id(1L).userId(9L).status(0).build();
        assertTrue(BlogPostServiceImpl.canViewPost(draft, author));
    }

    @Test
    void otherUserCannotViewDraft() {
        User other = user(8L, UserConstant.DEFAULT_ROLE);
        BlogPost draft = BlogPost.builder().id(1L).userId(9L).status(0).build();
        assertFalse(BlogPostServiceImpl.canViewPost(draft, other));
    }

    @Test
    void adminCanViewOthersDraft() {
        User admin = user(1L, UserConstant.ADMIN_ROLE);
        BlogPost draft = BlogPost.builder().id(1L).userId(9L).status(0).content("secret").build();
        assertTrue(BlogPostServiceImpl.canViewPost(draft, admin));
    }

    @Test
    void anonymousListForcesPublished() {
        BlogPostQueryRequest request = new BlogPostQueryRequest();
        request.setStatus(0);
        BlogPostServiceImpl.applyPublicListVisibility(request, null);
        assertEquals(1, request.getStatus());
    }

    @Test
    void authorListKeepsRequestedStatus() {
        BlogPostQueryRequest request = new BlogPostQueryRequest();
        request.setStatus(0);
        BlogPostServiceImpl.applyPublicListVisibility(request, user(9L, UserConstant.DEFAULT_ROLE));
        assertEquals(0, request.getStatus());
    }

    private static User user(Long id, String role) {
        User user = new User();
        user.setId(id);
        user.setUserRole(role);
        return user;
    }
}
