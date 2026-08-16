package com.ydm.linuxdo.automation.engine

import kotlinx.coroutines.delay

/**
 * 可替换的等待器。
 *
 * 引擎里到处是「随机等 2-4 秒」这种防风控延迟，如果直接调 [delay]，
 * 单元测试跑完一次完整会话要几十分钟。抽出接口后测试可注入零延迟实现，
 * 同时还能断言「引擎确实等了该等的时间」。
 */
interface Delayer {
    suspend fun sleep(millis: Long)

    /** 当前时间戳，一并抽出来便于测试控制时间流逝 */
    fun nowMillis(): Long

    companion object {
        val Real: Delayer = object : Delayer {
            override suspend fun sleep(millis: Long) {
                if (millis > 0) delay(millis)
            }

            override fun nowMillis(): Long = System.currentTimeMillis()
        }
    }
}
