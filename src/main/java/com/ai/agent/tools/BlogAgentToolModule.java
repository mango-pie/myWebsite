package com.ai.agent.tools;

import com.ai.config.ConditionalOnModule;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ai.agent.model.AgentToolContext;
import com.ai.agent.model.AgentToolResult;
import com.ai.model.vo.chat.AgentUiAction;
import com.ai.agent.registry.AgentToolModule;
import com.ai.agent.registry.AgentToolRegistryBuilder;
import com.ai.model.dto.blog.BlogCategoryQueryRequest;
import com.ai.model.dto.blog.BlogPostAddRequest;
import com.ai.model.dto.blog.BlogPostUpdateRequest;
import com.ai.model.dto.blog.BlogTagQueryRequest;
import com.ai.model.vo.blog.BlogCategoryVO;
import com.ai.model.vo.blog.BlogPostVO;
import com.ai.model.vo.blog.BlogTagVO;
import com.ai.service.BlogCategoryService;
import com.ai.service.BlogPostService;
import com.ai.service.BlogTagService;
import com.mybatisflex.core.paginate.Page;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ai.agent.registry.AgentToolRegistryBuilder.*;

/**
 * 博客域 Agent 工具：分类/标签只读查询（L0），创建与更新博客草稿（L1），
 * 发布文章（L2，经确认流后执行）。
 * 创建/更新写操作固定落为草稿状态（status=0），发布仅在用户确认后进行；
 * 成功后附带 UI 动作，引导前端刷新列表或跳到编辑页。
 * 仅在 chat 与 blog 模块同时开启时注册。
 */
@ConditionalOnModule({"chat", "blog"})
@Component
public class BlogAgentToolModule extends AgentToolModule {

    private static final int DRAFT_STATUS = 0;

    private static final int PUBLISHED_STATUS = 1;

    @Resource
    private BlogCategoryService blogCategoryService;

    @Resource
    private BlogTagService blogTagService;

    @Resource
    private BlogPostService blogPostService;

    @Override
    public String moduleName() {
        return "blog";
    }

    @Override
    protected void registerTools(AgentToolRegistryBuilder registry) {
        registry.l0("list_blog_categories", "列出博客分类", this::listBlogCategories);
        registry.l0("list_blog_tags", "列出博客标签", this::listBlogTags);

        registry.l1("create_blog_draft", "创建博客草稿（未发布）",
                objectSchema()
                        .addProperty("title", stringProp("文章标题"))
                        .addProperty("content", stringProp("正文 Markdown/HTML"))
                        .addProperty("summary", stringProp("摘要"))
                        .addProperty("categoryId", integerProp("分类 id"))
                        .required("title")
                        .build(),
                this::createBlogDraft);

        registry.l1("update_blog_draft", "更新博客草稿",
                objectSchema()
                        .addProperty("id", integerProp("文章 id"))
                        .addProperty("title", stringProp("标题"))
                        .addProperty("content", stringProp("正文"))
                        .addProperty("summary", stringProp("摘要"))
                        .addProperty("categoryId", integerProp("分类 id"))
                        .required("id")
                        .build(),
                this::updateBlogDraft);

        // L2 高风险发布：网关会挂起待确认，用户确认后原参数重放执行（设计文档 §8.3）
        registry.l2("publish_blog_post", "发布指定博客文章（需用户确认后执行）",
                objectSchema()
                        .addProperty("id", integerProp("文章 id"))
                        .required("id")
                        .build(),
                this::summarizePublishPost,
                this::publishBlogPost);
    }

    private AgentToolResult listBlogCategories(AgentToolContext ctx, String args) {
        BlogCategoryQueryRequest request = new BlogCategoryQueryRequest();
        request.setPageNum(1);
        request.setPageSize(100);
        Page<BlogCategoryVO> page = blogCategoryService.queryCategoryPage(request);
        List<Map<String, Object>> simplified = new ArrayList<>();
        for (BlogCategoryVO category : page.getRecords()) {
            simplified.add(mapOf("id", category.getId(), "name", category.getName()));
        }
        return AgentToolResult.ok(mapOf("categories", simplified));
    }

    private AgentToolResult listBlogTags(AgentToolContext ctx, String args) {
        BlogTagQueryRequest request = new BlogTagQueryRequest();
        request.setPageNum(1);
        request.setPageSize(100);
        Page<BlogTagVO> page = blogTagService.queryTagPage(request);
        List<Map<String, Object>> simplified = new ArrayList<>();
        for (BlogTagVO tag : page.getRecords()) {
            simplified.add(mapOf("id", tag.getId(), "name", tag.getName()));
        }
        return AgentToolResult.ok(mapOf("tags", simplified));
    }

    private AgentToolResult createBlogDraft(AgentToolContext ctx, String args) {
        JSONObject json = parseArgs(args);
        String title = json.getStr("title");
        if (StrUtil.isBlank(title)) {
            return AgentToolResult.fail("title 不能为空");
        }
        BlogPostAddRequest request = new BlogPostAddRequest();
        request.setTitle(title.trim());
        request.setContent(json.getStr("content"));
        request.setSummary(json.getStr("summary"));
        request.setCategoryId(json.getLong("categoryId"));
        request.setStatus(DRAFT_STATUS);
        long postId = blogPostService.addBlogPost(request, ctx.getUserId());
        AgentUiAction uiAction = AgentUiAction.builder()
                .type("navigate")
                .module("blog_editor")
                .path("/blog/edit/" + postId)
                .build();
        return AgentToolResult.ok(mapOf("postId", postId, "title", title.trim(), "status", DRAFT_STATUS), uiAction);
    }

    private AgentToolResult updateBlogDraft(AgentToolContext ctx, String args) {
        JSONObject json = parseArgs(args);
        Long id = json.getLong("id");
        if (id == null || id <= 0) {
            return AgentToolResult.fail("id 不能为空");
        }
        BlogPostUpdateRequest request = new BlogPostUpdateRequest();
        request.setId(id);
        if (json.containsKey("title")) {
            request.setTitle(json.getStr("title"));
        }
        if (json.containsKey("content")) {
            request.setContent(json.getStr("content"));
        }
        if (json.containsKey("summary")) {
            request.setSummary(json.getStr("summary"));
        }
        if (json.containsKey("categoryId")) {
            request.setCategoryId(json.getLong("categoryId"));
        }
        request.setStatus(DRAFT_STATUS);
        blogPostService.updateBlogPost(request, ctx.getUserId());
        AgentUiAction uiAction = AgentUiAction.builder()
                .type("navigate")
                .module("blog_editor")
                .path("/blog/edit/" + id)
                .build();
        return AgentToolResult.ok(mapOf("postId", id), uiAction);
    }

    private AgentToolResult publishBlogPost(AgentToolContext ctx, String args) {
        JSONObject json = parseArgs(args);
        Long id = json.getLong("id");
        if (id == null || id <= 0) {
            return AgentToolResult.fail("id 不能为空");
        }
        // updateBlogPostStatus 内部校验文章归属，非本人/不存在时抛业务异常，由网关收敛为 fail
        blogPostService.updateBlogPostStatus(id, PUBLISHED_STATUS, ctx.getUserId());
        AgentUiAction uiAction = AgentUiAction.builder()
                .type("refresh")
                .module("blog_list")
                .build();
        return AgentToolResult.ok(mapOf("postId", id, "status", PUBLISHED_STATUS), uiAction);
    }

    /** 确认摘要：待发布文章标题（查不到时回退为文章 id）。 */
    private String summarizePublishPost(AgentToolContext ctx, String args) {
        Long id = parseArgs(args).getLong("id");
        String title = null;
        try {
            BlogPostVO post = id == null ? null : blogPostService.getBlogPostVO(id);
            title = post == null ? null : post.getTitle();
        } catch (Exception ignore) {
            // 摘要查询失败不阻断确认流，回退为 id 展示
        }
        return title == null ? "发布文章（id=" + id + "）" : "将发布文章《" + title + "》";
    }

    private JSONObject parseArgs(String args) {
        if (StrUtil.isBlank(args)) {
            return new JSONObject();
        }
        return JSONUtil.parseObj(args);
    }
}
