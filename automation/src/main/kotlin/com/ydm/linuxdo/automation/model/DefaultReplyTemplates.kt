package com.ydm.linuxdo.automation.model

/**
 * 内置回复模板种子数据。
 *
 * 移植自上游 `linux_do_gui.py` 的 `CFG["tpl"]`。
 *
 * ## 一处数量出入
 *
 * 上游 README 和界面都写「内置 68 条精选回复模板」，但源码里实际只有 **65 条**
 * （感谢 8 / 学习 8 / 支持 6 / 收藏 7 / 赞美 8 / 前排 5 / 佬 7 / 其他 16）。
 * 这里如实按 65 条移植，不硬凑。
 *
 * ## 约束
 *
 * 每条不少于 6 个字——这是上游为规避 Discourse「无意义回复」检测定的规则，
 * 由 [ReplyTemplateRules.MIN_LENGTH] 强制校验，用户新增模板时同样生效。
 */
object DefaultReplyTemplates {

    enum class Group(val displayName: String) {
        THANKS("感谢类"),
        LEARNING("学习类"),
        SUPPORT("支持类"),
        COLLECT("收藏类"),
        PRAISE("赞美类"),
        FRONT_ROW("前排类"),
        SENIOR("佬类"),
        OTHER("其他"),
    }

    data class Seed(val content: String, val group: Group)

    val all: List<Seed> = buildList {
        fun g(group: Group, vararg items: String) {
            items.forEach { add(Seed(it, group)) }
        }

        g(
            Group.THANKS,
            "感谢分享！学习了",
            "感谢楼主的分享",
            "感谢分享，很有帮助",
            "感谢大佬的分享",
            "感谢楼主无私分享",
            "感谢分享，收藏学习",
            "感谢楼主，学到了",
            "感谢分享，受益匪浅",
        )

        g(
            Group.LEARNING,
            "学习了，谢谢楼主！",
            "学到了新知识，感谢",
            "涨知识了，谢谢分享",
            "学习学习，感谢大佬",
            "又学到了，感谢楼主",
            "学习一下，感谢分享",
            "认真学习中，感谢",
            "好好学习天天向上",
        )

        g(
            Group.SUPPORT,
            "支持一下，感谢分享",
            "支持楼主，继续加油",
            "必须支持，感谢分享",
            "大力支持，感谢楼主",
            "支持支持，学习了",
            "强烈支持，感谢分享",
        )

        g(
            Group.COLLECT,
            "好文章，收藏了",
            "收藏了，感谢分享",
            "先收藏，慢慢学习",
            "收藏学习，感谢楼主",
            "马克一下，感谢分享",
            "mark一下，以后学习",
            "先马后看，感谢分享",
        )

        g(
            Group.PRAISE,
            "不错不错，学习了",
            "写得很好，感谢分享",
            "内容很棒，感谢楼主",
            "干货满满，感谢分享",
            "质量很高，感谢楼主",
            "很有价值，感谢分享",
            "非常实用，感谢楼主",
            "很有帮助，感谢分享",
        )

        g(
            Group.FRONT_ROW,
            "前排围观，感谢分享",
            "前排学习，感谢楼主",
            "前排支持，感谢分享",
            "前排关注，学习了",
            "前排占座，感谢分享",
        )

        g(
            Group.SENIOR,
            "谢谢佬，学习了",
            "感谢佬的分享",
            "佬太强了，学习了",
            "跟着佬学习一下",
            "佬就是佬，感谢分享",
            "大佬牛逼，学习了",
            "膜拜大佬，感谢分享",
        )

        g(
            Group.OTHER,
            "路过学习，感谢分享",
            "围观学习，感谢楼主",
            "来学习一下，感谢",
            "看看学习，感谢分享",
            "顶一下，感谢分享",
            "顶顶顶，感谢楼主",
            "帮顶一下，感谢分享",
            "好帖必顶，感谢楼主",
            "精华帖子，感谢分享",
            "优质内容，感谢楼主",
            "实用干货，感谢分享",
            "很有意思，感谢楼主",
            "长见识了，感谢分享",
            "开眼界了，感谢楼主",
            "受教了，感谢分享",
            "茅塞顿开，感谢楼主",
        )
    }
}

/** 模板校验规则，新增/导入模板时统一走这里 */
object ReplyTemplateRules {

    /** Discourse 对过短回复有「无意义」判定，上游定的下限是 6 个字 */
    const val MIN_LENGTH = 6

    const val MAX_LENGTH = 500

    sealed interface Result {
        data object Valid : Result
        data class Invalid(val reason: String) : Result
    }

    fun validate(content: String): Result {
        val trimmed = content.trim()
        return when {
            trimmed.isEmpty() -> Result.Invalid("内容不能为空")
            trimmed.length < MIN_LENGTH -> Result.Invalid("至少 $MIN_LENGTH 个字，当前 ${trimmed.length} 个")
            trimmed.length > MAX_LENGTH -> Result.Invalid("最多 $MAX_LENGTH 个字，当前 ${trimmed.length} 个")
            else -> Result.Valid
        }
    }

    /** 归一化用于去重：去首尾空白、压缩内部连续空白 */
    fun normalizeForDedup(content: String): String =
        content.trim().replace(Regex("\\s+"), " ")
}
