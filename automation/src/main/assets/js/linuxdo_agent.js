/*
 * linuxdo_agent.js — Linux.do 页面自动化代理
 *
 * 移植自 PC 端 Python 脚本 jb/linuxdosss-main（MIT, icysaintdx）的 DOM 操作逻辑。
 *
 * 设计约定：
 * 1. 所有导出方法挂在 window.__ld 上，返回值必须是 JSON 可序列化的。
 * 2. 每个方法都被 envelope() 包一层，统一返回 {ok, data} 或 {ok, error, stack}，
 *    避免 Kotlin 侧把「异常」和「合法的 null」混为一谈。
 * 3. 本文件会在每次页面加载后重复注入，必须幂等。
 * 4. 参数由 Kotlin 侧用 JSONObject.quote() 序列化后传入，此处只做 JSON.parse，
 *    绝不做字符串拼接 —— 这是上游 linux_do_gui.py:953-963 注入 bug 的根因。
 */
(function () {
  'use strict';

  var AGENT_VERSION = '1.0.0';

  // 已注入过且版本一致就跳过，保证幂等
  if (window.__ld && window.__ld.version === AGENT_VERSION) {
    return;
  }

  // ===================================================================
  // 选择器集中区 —— L 站前端改版时只改这里
  // ===================================================================
  var SEL = {
    // 登录态
    currentUser: '#current-user',

    // 话题列表
    topicRow: 'tr.topic-list-item',
    topicLink: 'a.title.raw-link.raw-topic-link',
    unreadBadge: '.badge.badge-notification.new-topic',
    pinnedClass: 'pinned',
    repliesSortButton: 'th[data-sort-order="posts"] button',

    // 楼层进度（两种布局）
    timelineReplies: '.timeline-replies',
    topicProgressNums: '#topic-progress .nums',

    // 点赞
    likeButton: 'button.btn-toggle-reaction-like',
    likedClasses: ['has-like', 'my-likes'],

    // 回复
    replyOpenButton: '.topic-footer-main-buttons button.create',
    replyTextarea: '#reply-control textarea, .d-editor-input',
    replySubmitButton: '#reply-control button.create',
    replyControl: '#reply-control',

    // connect.linux.do 等级页
    cardSubtitle: '.card-subtitle',
    cardTitle: '.card-title',
    tl3Ring: '.tl3-ring',
    tl3RingLabel: '.tl3-ring-label',
    tl3RingCurrent: '.tl3-ring-current',
    tl3RingTarget: '.tl3-ring-target',
    tl3BarItem: '.tl3-bar-item',
    tl3BarLabel: '.tl3-bar-label',
    tl3BarNums: '.tl3-bar-nums',
    tl3QuotaCard: '.tl3-quota-card',
    tl3QuotaLabel: '.tl3-quota-label',
    tl3QuotaNums: '.tl3-quota-nums',
    tl3VetoItem: '.tl3-veto-item',
    tl3VetoLabel: '.tl3-veto-label',
    tl3VetoValue: '.tl3-veto-value'
  };

  // ===================================================================
  // 工具
  // ===================================================================

  /** 统一返回信封，异常不再静默变成 null */
  function envelope(fn) {
    return function () {
      try {
        return JSON.stringify({ ok: true, data: fn.apply(null, arguments) });
      } catch (e) {
        return JSON.stringify({
          ok: false,
          error: String((e && e.message) || e),
          stack: String((e && e.stack) || '')
        });
      }
    };
  }

  function $(sel, root) {
    return (root || document).querySelector(sel);
  }

  function $$(sel, root) {
    return Array.prototype.slice.call((root || document).querySelectorAll(sel));
  }

  function text(el) {
    return el ? String(el.textContent || '').trim() : '';
  }

  /** 元素是否真的可见（offsetParent 对 position:fixed 无效，故补 rect 判断） */
  function isVisible(el) {
    if (!el) return false;
    if (el.offsetParent !== null) return true;
    var r = el.getBoundingClientRect();
    return r.width > 0 && r.height > 0;
  }

  function hasAnyClass(el, classes) {
    if (!el) return false;
    for (var i = 0; i < classes.length; i++) {
      if (el.classList.contains(classes[i])) return true;
    }
    return false;
  }

  // ===================================================================
  // 登录态
  // ===================================================================

  function isLoggedIn() {
    return {
      loggedIn: !!$(SEL.currentUser),
      username: (function () {
        var img = $(SEL.currentUser + ' img');
        return img ? img.getAttribute('title') || '' : '';
      })(),
      url: location.href
    };
  }

  // ===================================================================
  // 话题列表
  // ===================================================================

  /**
   * 抓取当前板块的话题列表，区分未读（带小蓝点）与已读。
   * 对应 PC 端 Bot.get_topics()。
   */
  function getTopics() {
    var unread = [];
    var read = [];

    $$(SEL.topicRow).forEach(function (row) {
      var link = $(SEL.topicLink, row);
      if (!link) return;

      var href = link.getAttribute('href');
      var title = text(link);
      var topicId = row.getAttribute('data-topic-id');

      // 跳过置顶帖
      if (!href || !title || row.classList.contains(SEL.pinnedClass)) return;

      var item = {
        id: topicId,
        url: href,
        title: title.substring(0, 80),
        unread: !!$(SEL.unreadBadge, row)
      };

      if (item.unread) unread.push(item);
      else read.push(item);
    });

    return { unread: unread, read: read, total: unread.length + read.length };
  }

  /** 点击"回复数"表头排序，对应 PC 端 get_topics() 里的排序步骤 */
  function sortByReplies() {
    var btn = $(SEL.repliesSortButton);
    if (!btn) return { clicked: false, reason: 'sort-button-not-found' };
    btn.click();
    return { clicked: true };
  }

  /**
   * 通过点击 <a> 进入话题。
   *
   * ★ 关键：绝不能直接改 location.href。Discourse 只有在真实点击链接时
   *   才会把该话题计入「浏览话题」。这是 PC 端作者踩出来的核心结论。
   */
  function clickTopic(rawArgs) {
    var args = JSON.parse(rawArgs);
    var topicId = String(args.topicId);

    var row = $(SEL.topicRow + '[data-topic-id="' + CSS.escape(topicId) + '"]');
    if (!row) return { clicked: false, reason: 'row-not-found' };

    var link = $(SEL.topicLink, row);
    if (!link) return { clicked: false, reason: 'link-not-found' };

    link.scrollIntoView({ block: 'center' });
    link.click();
    return { clicked: true, href: link.getAttribute('href') };
  }

  /** 回到列表后确认小蓝点已消失（= 该话题已被标记为已读） */
  function checkBadgeGone(rawArgs) {
    var args = JSON.parse(rawArgs);
    var topicId = String(args.topicId);

    var row = $(SEL.topicRow + '[data-topic-id="' + CSS.escape(topicId) + '"]');
    if (!row) return { gone: true, reason: 'row-absent' };

    return { gone: !$(SEL.unreadBadge, row) };
  }

  // ===================================================================
  // 楼层进度
  // ===================================================================

  /**
   * 读取当前楼层/总楼层。
   *
   * ★ 楼层计数器是唯一可信的进度源：
   *   - 不能用滚动次数（和实际阅读量不成比例）
   *   - 不能用"是否滚到底"（Discourse 无限滚动，永远到不了底）
   *
   * 两种 DOM 布局：
   *   宽窗口 .timeline-replies       → 文本 "1 / 169"
   *   窄窗口 #topic-progress .nums   → <span>69</span><span>/</span><span>74</span>
   * 手机端大概率走第二种。
   */
  function getFloorInfo() {
    // 布局一：时间轴
    var timeline = $(SEL.timelineReplies);
    if (timeline) {
      var m = text(timeline).match(/(\d+)\s*\/\s*(\d+)/);
      if (m) {
        return {
          current: parseInt(m[1], 10),
          total: parseInt(m[2], 10),
          source: 'timeline-replies'
        };
      }
    }

    // 布局二：底部进度条（窄屏/手机）
    var progress = $(SEL.topicProgressNums);
    if (progress) {
      var spans = $$('span', progress);
      if (spans.length >= 3) {
        var cur = parseInt(text(spans[0]), 10);
        var tot = parseInt(text(spans[2]), 10);
        if (!isNaN(cur) && !isNaN(tot)) {
          return { current: cur, total: tot, source: 'topic-progress' };
        }
      }
      // 兜底：整段文本正则
      var m2 = text(progress).match(/(\d+)\s*\/\s*(\d+)/);
      if (m2) {
        return {
          current: parseInt(m2[1], 10),
          total: parseInt(m2[2], 10),
          source: 'topic-progress-regex'
        };
      }
    }

    // 拿不到就明确返回 null，由 Kotlin 侧标记为 unreliable。
    // ★ 绝不像上游那样凭空 floors += 3 伪造数据。
    return null;
  }

  function scrollByPx(rawArgs) {
    var args = JSON.parse(rawArgs);
    var px = Number(args.px) || 0;
    window.scrollBy(0, px);
    return {
      scrollY: window.scrollY,
      innerHeight: window.innerHeight,
      docHeight: document.body ? document.body.offsetHeight : 0
    };
  }

  // ===================================================================
  // 点赞
  // ===================================================================

  function likeButtons() {
    return $$(SEL.likeButton);
  }

  function getLikeState() {
    var btns = likeButtons();
    return {
      count: btns.length,
      liked: btns.map(function (b) {
        return hasAnyClass(b, SEL.likedClasses);
      })
    };
  }

  /**
   * 点赞第 index 个按钮（0 = 主帖）。
   * 与上游不同：这里同步点击，不用 setTimeout —— 上游那个 setTimeout(300)
   * 会让返回值先于点击发生，导致统计虚高。
   */
  function like(rawArgs) {
    var args = JSON.parse(rawArgs);
    var index = Number(args.index) || 0;

    var btns = likeButtons();
    if (index >= btns.length) return { liked: false, reason: 'index-out-of-range' };

    var btn = btns[index];
    if (hasAnyClass(btn, SEL.likedClasses)) {
      return { liked: false, reason: 'already-liked' };
    }
    if (!isVisible(btn)) {
      btn.scrollIntoView({ block: 'center' });
    }
    btn.click();

    return { liked: true, index: index };
  }

  // ===================================================================
  // 回复
  // ===================================================================

  function isReplyEditorOpen() {
    var ta = $(SEL.replyTextarea);
    return { open: !!ta && isVisible(ta) };
  }

  function openReply() {
    var btn = $(SEL.replyOpenButton);
    if (!btn) return { opened: false, reason: 'reply-button-not-found' };
    btn.click();
    return { opened: true };
  }

  /**
   * 填入回复内容。
   *
   * ★ 内容通过 JSON.parse 取得，绝不做字符串拼接。
   *   上游 linux_do_gui.py:953 直接把内容插进 JS 字符串字面量，
   *   内容里只要有单引号/反斜杠/换行就语法崩溃。
   *
   * Discourse 用 Ember，直接赋 value 不会触发双向绑定，
   * 必须用原生 setter + dispatch input 事件。
   */
  function fillReply(rawArgs) {
    var args = JSON.parse(rawArgs);
    var content = String(args.content == null ? '' : args.content);

    var ta = $(SEL.replyTextarea);
    if (!ta) return { filled: false, reason: 'textarea-not-found' };

    ta.focus();

    // 绕过 Ember/React 对 value 属性的劫持
    var proto = Object.getPrototypeOf(ta);
    var desc = Object.getOwnPropertyDescriptor(proto, 'value');
    if (desc && desc.set) {
      desc.set.call(ta, content);
    } else {
      ta.value = content;
    }

    ta.dispatchEvent(new Event('input', { bubbles: true }));
    ta.dispatchEvent(new Event('change', { bubbles: true }));

    return { filled: true, length: content.length, actual: ta.value };
  }

  function submitReply() {
    var btn = $(SEL.replySubmitButton);
    if (!btn) return { submitted: false, reason: 'submit-button-not-found' };
    if (btn.disabled) return { submitted: false, reason: 'submit-button-disabled' };
    btn.click();
    return { submitted: true };
  }

  // ===================================================================
  // 等级 / 升级进度（connect.linux.do）
  // ===================================================================

  /**
   * 抓取信任等级与各项升级指标。
   * 对应 PC 端 Bot.get_level_info()，覆盖 4 种 DOM 结构。
   *
   * 每项指标额外带 rawName，Kotlin 侧用显式映射表匹配，
   * 不再像上游那样靠中文子串猜（"阅读时间"会被误判成"阅读"）。
   */
  function getLevelInfo() {
    var result = {
      username: '',
      level: '',
      nextLevel: '',
      requirements: [],
      url: location.href
    };

    // 用户名：card-subtitle 里的 @xxx
    var subtitle = $(SEL.cardSubtitle);
    if (subtitle) {
      var um = text(subtitle).match(/@([^\s·]+)/);
      if (um) result.username = um[1];
    }

    // 目标等级：card-title 里的「信任级别 N」
    var cardTitle = $(SEL.cardTitle);
    if (cardTitle) {
      var lm = text(cardTitle).match(/信任级别\s*(\d+)/);
      if (lm) {
        result.nextLevel = lm[1];
        result.level = String(parseInt(lm[1], 10) - 1);
      }
    }

    function push(name, current, required, kind) {
      if (!name) return;
      result.requirements.push({
        rawName: name,
        current: String(current),
        required: String(required),
        kind: kind
      });
    }

    // 结构一：活跃程度环
    $$(SEL.tl3Ring).forEach(function (ring) {
      var label = $(SEL.tl3RingLabel, ring);
      var cur = $(SEL.tl3RingCurrent, ring);
      var tgt = $(SEL.tl3RingTarget, ring);
      if (label && cur && tgt) {
        push(text(label), text(cur), text(tgt).replace('/', '').trim(), 'ring');
      }
    });

    // 结构二：互动参与条
    $$(SEL.tl3BarItem).forEach(function (bar) {
      var label = $(SEL.tl3BarLabel, bar);
      var nums = $(SEL.tl3BarNums, bar);
      if (label && nums) {
        var m = text(nums).match(/(\d+)\s*\/\s*(\d+)/);
        if (m) push(text(label), m[1], m[2], 'bar');
      }
    });

    // 结构三：合规配额卡
    $$(SEL.tl3QuotaCard).forEach(function (q) {
      var label = $(SEL.tl3QuotaLabel, q);
      var nums = $(SEL.tl3QuotaNums, q);
      if (label && nums) {
        var m = text(nums).match(/(\d+)\s*\/\s*(\d+)/);
        if (m) push(text(label), m[1], m[2], 'quota');
      }
    });

    // 结构四：禁言/封禁否决项
    $$(SEL.tl3VetoItem).forEach(function (v) {
      var label = $(SEL.tl3VetoLabel, v);
      var value = $(SEL.tl3VetoValue, v);
      if (label && value) push(text(label), text(value), '0', 'veto');
    });

    return result;
  }

  // ===================================================================
  // 健康自检 —— L 站改版时能第一时间发现选择器失效
  // ===================================================================

  function healthCheck() {
    return {
      agentVersion: AGENT_VERSION,
      url: location.href,
      readyState: document.readyState,
      found: {
        currentUser: !!$(SEL.currentUser),
        topicRows: $$(SEL.topicRow).length,
        floorInfo: !!getFloorInfo(),
        likeButtons: likeButtons().length,
        replyButton: !!$(SEL.replyOpenButton),
        tl3Any:
          $$(SEL.tl3Ring).length +
          $$(SEL.tl3BarItem).length +
          $$(SEL.tl3QuotaCard).length
      }
    };
  }

  // ===================================================================
  // Cloudflare 挑战页检测
  // ===================================================================

  /**
   * 判断当前是不是 Cloudflare 的人机验证页。
   *
   * ★ 为什么必须有这个
   *
   * Cloudflare 有一类**无感验证**：页面自己转几秒就过了，不需要用户点任何东西。
   * 但如果这期间脚本去 navigate/reload，挑战就被打断，
   * 回到原点重新验证——用户会看到"验证永远过不去"。
   *
   * 所以引擎在检测到挑战页时必须**完全不动**，静静等它自己过。
   *
   * interactive 用来区分两种：
   * - false：无感验证，等就行
   * - true ：Turnstile 复选框，需要用户点一下
   */
  function detectChallenge() {
    var markers = [
      '#challenge-running',
      '#challenge-form',
      '#cf-challenge-running',
      '.cf-browser-verification',
      '#cf-wrapper',
    ];
    var passive = markers.some(function (sel) { return !!$(sel); });

    var turnstile = $('.cf-turnstile') ||
      $('iframe[src*="challenges.cloudflare.com"]') ||
      $('iframe[title*="Cloudflare"]');

    var title = String(document.title || '').toLowerCase();
    var titleHit =
      title.indexOf('just a moment') >= 0 ||
      title.indexOf('请稍候') >= 0 ||
      title.indexOf('稍等') >= 0 ||
      title.indexOf('attention required') >= 0 ||
      title.indexOf('checking your browser') >= 0;

    var challenge = passive || !!turnstile || titleHit;

    return {
      challenge: challenge,
      interactive: !!turnstile,
      title: document.title || '',
      url: location.href,
    };
  }

  // ===================================================================
  // 导出
  // ===================================================================
  window.__ld = {
    version: AGENT_VERSION,

    ping: envelope(function () {
      return { pong: true, version: AGENT_VERSION };
    }),
    isLoggedIn: envelope(isLoggedIn),
    detectChallenge: envelope(detectChallenge),

    getTopics: envelope(getTopics),
    sortByReplies: envelope(sortByReplies),
    clickTopic: envelope(clickTopic),
    checkBadgeGone: envelope(checkBadgeGone),

    getFloorInfo: envelope(getFloorInfo),
    scrollBy: envelope(scrollByPx),

    getLikeState: envelope(getLikeState),
    like: envelope(like),

    isReplyEditorOpen: envelope(isReplyEditorOpen),
    openReply: envelope(openReply),
    fillReply: envelope(fillReply),
    submitReply: envelope(submitReply),

    getLevelInfo: envelope(getLevelInfo),
    healthCheck: envelope(healthCheck)
  };
})();
