package com.ydm.linuxdo.automation.model

/**
 * 内置板块列表。
 *
 * 以上游 GUI 版 `linux_do_gui.py` 的 `CATS`（16 个，带默认开关）为准。
 *
 * ⚠️ 注意：上游 `docker/linux_do_docker.py` 里那份只有 12 个且没有开关字段——
 * 这正是「同一份爬虫抄 4 遍」导致的配置漂移。这里只保留一份真源。
 *
 * 默认关闭的 4 个（积分乐园 / 扬帆起航 / 社区孵化 / 运营反馈）沿用上游选择：
 * 这些板块要么内容与技术无关，要么话题量少、爬楼收益低。
 */
object DefaultCategories {

    val all: List<Category> = listOf(
        Category("develop", "开发调优", "/c/develop/4", enabledByDefault = true),
        Category("domestic", "国产替代", "/c/domestic/98", enabledByDefault = true),
        Category("resource", "资源荟萃", "/c/resource/14", enabledByDefault = true),
        Category("cloud-asset", "网盘资源", "/c/resource/cloud-asset/94", enabledByDefault = true),
        Category("wiki", "文档共建", "/c/wiki/42", enabledByDefault = true),
        Category("credit", "积分乐园", "/c/credit/106", enabledByDefault = false),
        Category("job", "非我莫属", "/c/job/27", enabledByDefault = true),
        Category("reading", "读书成诗", "/c/reading/32", enabledByDefault = true),
        Category("startup", "扬帆起航", "/c/startup/46", enabledByDefault = false),
        Category("news", "前沿快讯", "/c/news/34", enabledByDefault = true),
        Category("feeds", "网络记忆", "/c/feeds/92", enabledByDefault = true),
        Category("welfare", "福利羊毛", "/c/welfare/36", enabledByDefault = true),
        Category("gossip", "搞七捻三", "/c/gossip/11", enabledByDefault = true),
        Category("incubator", "社区孵化", "/c/incubator/102", enabledByDefault = false),
        Category("square", "虫洞广场", "/c/square/110", enabledByDefault = true),
        Category("feedback", "运营反馈", "/c/feedback/2", enabledByDefault = false),
    )

    val defaultEnabled: List<Category> get() = all.filter { it.enabledByDefault }

    fun bySlug(slug: String): Category? = all.firstOrNull { it.slug == slug }
}
