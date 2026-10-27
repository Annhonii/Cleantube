/* Voidstream InnerTube engine.
 *
 * Runs inside a hidden WebView that is on https://www.youtube.com (so the user's login cookies,
 * TLS fingerprint and headers are the real browser's). Kotlin calls CT.run(id, command, argsJson);
 * the answer comes back through AndroidBridge.onResult(id, ok, json).
 *
 * The parsers (section 1) are pure functions and are tested against recorded YouTube responses.
 * YouTube changes its JSON now and then: when something stops showing up, this is the file to fix.
 */
(function (root) {
  'use strict';

  /* ================================================================== 1. parsers */

  function txt(o) {
    if (o == null) return '';
    if (typeof o === 'string') return o;
    if (o.simpleText) return o.simpleText;
    if (typeof o.content === 'string') return o.content;
    if (Array.isArray(o.runs)) return o.runs.map(function (r) { return r.text || ''; }).join('');
    return '';
  }

  /** Depth-first walk. visit(obj) may return true to NOT descend into obj. */
  function walk(o, visit) {
    if (Array.isArray(o)) {
      for (var i = 0; i < o.length; i++) walk(o[i], visit);
    } else if (o && typeof o === 'object') {
      if (visit(o) === true) return;
      for (var k in o) walk(o[k], visit);
    }
  }

  function findFirst(o, key) {
    var found;
    walk(o, function (x) {
      if (found !== undefined) return true;
      if (key in x) { found = x[key]; return true; }
      return false;
    });
    return found;
  }

  function fixUrl(u) { return (u && u.indexOf('//') === 0) ? 'https:' + u : (u || ''); }

  function lastThumb(t) {
    var a = t && (t.thumbnails || t.sources);
    return a && a.length ? fixUrl(a[a.length - 1].url) : '';
  }
  function firstThumb(t) {
    var a = t && (t.thumbnails || t.sources);
    return a && a.length ? fixUrl(a[0].url) : '';
  }

  function browseIdIn(o) {
    var id = '';
    walk(o, function (x) {
      if (id) return true;
      if (x.browseEndpoint && typeof x.browseEndpoint.browseId === 'string' &&
          x.browseEndpoint.browseId.indexOf('UC') === 0) { id = x.browseEndpoint.browseId; return true; }
      return false;
    });
    return id;
  }

  var DUR_RE = /^(?:\d+:)?\d{1,2}:\d{2}$/;

  function durSeconds(s) {
    if (!s || !DUR_RE.test(s)) return -1;
    var p = s.split(':').map(Number), n = 0;
    for (var i = 0; i < p.length; i++) n = n * 60 + p[i];
    return n;
  }

  // Everything below an ad container is dropped, so the feeds stay ad free.
  var AD_KEYS = {
    adSlotRenderer: 1, promotedSparklesWebRenderer: 1, promotedVideoRenderer: 1, inFeedAdLayoutRenderer: 1,
    displayAdRenderer: 1, bannerPromoRenderer: 1, statementBannerRenderer: 1, compactPromotedVideoRenderer: 1,
    adBreakServiceRenderer: 1, searchPyvRenderer: 1, promotedSparklesTextSearchRenderer: 1
  };

  /** First image url found under [o] (a channel avatar block). */
  function avatarIn(o) {
    var u = '';
    walk(o, function (x) {
      if (u) return true;
      if (x && x.sources && x.sources.length && x.sources[0] && x.sources[0].url) { u = lastThumb(x); return true; }
      if (x && x.thumbnails && x.thumbnails.length && x.thumbnails[0] && x.thumbnails[0].url) { u = lastThumb(x); return true; }
      return false;
    });
    return u;
  }

  function mkVideo(id, title, ch, chId, dur, views, age, live, short, chThumb) {
    return { t: 'v', id: id, title: title || '', ch: ch || '', chId: chId || '', dur: dur == null ? -1 : dur,
             views: views || '', age: age || '', live: !!live, short: !!short, chThumb: chThumb || '' };
  }

  /** videoRenderer, compactVideoRenderer, gridVideoRenderer, reelItemRenderer */
  function parseClassicVideo(r) {
    if (!r.videoId) return null;
    var by = r.longBylineText || r.shortBylineText || r.ownerText || null;
    var ch = by && by.runs && by.runs[0] ? by.runs[0].text : '';
    var chId = browseIdIn(by) || browseIdIn(r.channelThumbnailSupportedRenderers) || browseIdIn(r.ownerText);
    var lenText = txt(r.lengthText);
    var live = false;
    walk(r.thumbnailOverlays || [], function (x) {
      if (x.thumbnailOverlayTimeStatusRenderer) {
        var tr = x.thumbnailOverlayTimeStatusRenderer;
        if (!lenText) lenText = txt(tr.text);
        if (tr.style === 'LIVE') live = true;
      }
      return false;
    });
    (r.badges || []).forEach(function (b) {
      var m = b.metadataBadgeRenderer;
      if (m && (m.style === 'BADGE_STYLE_TYPE_LIVE_NOW' || m.label === 'LIVE')) live = true;
    });
    var views = txt(r.viewCountText) || txt(r.shortViewCountText);
    var short = !!r.reelWatchEndpoint || /\/shorts\//.test(JSON.stringify(r.navigationEndpoint || '').slice(0, 400));
    return mkVideo(r.videoId, txt(r.title) || txt(r.headline), ch, chId, durSeconds(lenText),
                   views, txt(r.publishedTimeText), live, short, avatarIn(r.channelThumbnailSupportedRenderers));
  }

  function metadataRows(lock) {
    var m = lock.metadata && lock.metadata.lockupMetadataViewModel;
    var rows = m && m.metadata && m.metadata.contentMetadataViewModel && m.metadata.contentMetadataViewModel.metadataRows;
    return rows || [];
  }

  function rowText(row) {
    return (row.metadataParts || []).map(function (p) { return txt(p.text); }).filter(Boolean);
  }

  /** lockupViewModel: the new format for videos and playlists in feeds, search, related, playlists. */
  function parseLockup(l) {
    var type = l.contentType || '';
    var id = l.contentId;
    if (!id) return null;
    var meta = l.metadata && l.metadata.lockupMetadataViewModel;
    var title = meta ? txt(meta.title) : '';
    var rows = metadataRows(l);
    var chName = '';
    var VA = /\bviews?\b|watching|\bago\b|streamed|premiere|scheduled|waiting|^\d+(\.\d+)?[KMB]?$/i;
    var allParts = [];
    rows.forEach(function (r) { rowText(r).forEach(function (s) { allParts.push(s); }); });
    for (var pi = 0; pi < allParts.length; pi++) { if (!VA.test(allParts[pi])) { chName = allParts[pi]; break; } }
    var chId = browseIdIn(rows) || browseIdIn(meta && meta.image);

    // thumbnail badges: duration / "N videos" / LIVE
    var badges = [];
    walk(l.contentImage || {}, function (x) {
      if (x.thumbnailBadgeViewModel) badges.push(txt(x.thumbnailBadgeViewModel.text) || '');
      return false;
    });

    if (type === 'LOCKUP_CONTENT_TYPE_VIDEO' || type === 'LOCKUP_CONTENT_TYPE_UNKNOWN' || !type) {
      var dur = -1, live = false;
      badges.forEach(function (b) {
        if (durSeconds(b) >= 0) dur = durSeconds(b);
        if (/^live$/i.test(b)) live = true;
      });
      var r1 = allParts;
      var views = '', age = '';
      r1.forEach(function (s) {
        if (/view|watching/i.test(s)) views = s;
        else if (/ago|streamed|premiere|scheduled|waiting/i.test(s)) age = s;
      });
      if (/watching/i.test(views)) live = true;
      return mkVideo(id, title, chName, chId, dur, views, age, live, false, avatarIn(meta && meta.image));
    }
    if (type === 'LOCKUP_CONTENT_TYPE_PLAYLIST' || type === 'LOCKUP_CONTENT_TYPE_PODCAST' ||
        type === 'LOCKUP_CONTENT_TYPE_ALBUM') {
      var count = '';
      badges.forEach(function (b) { if (/video|episode|track/i.test(b)) count = b; });
      var thumb = '';
      walk(l.contentImage || {}, function (x) {
        if (!thumb && x.image && x.image.sources) { thumb = lastThumb(x.image); return true; }
        return false;
      });
      return { t: 'p', id: id, title: title, ch: chName, thumb: thumb, count: count };
    }
    return null;
  }

  function parseShortsLockup(s) {
    var id = (s.onTap && s.onTap.innertubeCommand && s.onTap.innertubeCommand.reelWatchEndpoint &&
              s.onTap.innertubeCommand.reelWatchEndpoint.videoId) ||
             (s.inlinePlayerData && s.inlinePlayerData.onVisible && s.inlinePlayerData.onVisible.innertubeCommand &&
              s.inlinePlayerData.onVisible.innertubeCommand.watchEndpoint &&
              s.inlinePlayerData.onVisible.innertubeCommand.watchEndpoint.videoId);
    if (!id) return null;
    var om = s.overlayMetadata || {};
    return mkVideo(id, txt(om.primaryText), '', '', -1, txt(om.secondaryText), '', false, true);
  }

  function parseChannelRenderer(c) {
    var id = c.channelId;
    if (!id) return null;
    var a = txt(c.subscriberCountText), b = txt(c.videoCountText);
    var subs = /subscriber/i.test(a) ? a : (/subscriber/i.test(b) ? b : a || b);
    var handle = /^@/.test(a) ? a : '';
    return { t: 'c', id: id, title: txt(c.title), thumb: firstThumb(c.thumbnail), subs: subs, handle: handle,
             desc: txt(c.descriptionSnippet) };
  }

  function parsePlaylistRenderer(p) {
    if (!p.playlistId) return null;
    var by = p.longBylineText || p.shortBylineText;
    var thumb = '';
    walk(p.thumbnails || p.thumbnail || {}, function (x) {
      if (!thumb && x.thumbnails) { thumb = lastThumb(x); return true; }
      return false;
    });
    return { t: 'p', id: p.playlistId, title: txt(p.title), ch: by && by.runs && by.runs[0] ? by.runs[0].text : '',
             thumb: thumb, count: p.videoCount ? p.videoCount + ' videos' : txt(p.videoCountText) };
  }

  /** The continuation token that fetches the NEXT page: the last continuationItemRenderer found. */
  function lastToken(root_, skipKey) {
    var tok = null;
    walk(root_, function (x) {
      if (skipKey && skipKey in x) return true;
      if (AD_KEYS[Object.keys(x)[0]]) return true;
      var c = x.continuationItemRenderer;
      if (c) {
        var t = c.continuationEndpoint && c.continuationEndpoint.continuationCommand &&
                c.continuationEndpoint.continuationCommand.token;
        if (t) tok = t;
        return true;
      }
      return false;
    });
    return tok;
  }

  /**
   * The "Not interested" / "Don't recommend channel" feedback tokens in a feed entry's menu (classic menu or the newer
   * list-item sheet). Sent to YouTube's feedback endpoint they teach its recommender, exactly like the official app.
   */
  function attachFeedback(it, node) {
    if (!it || it.t !== 'v') return;
    walk(node, function (x) {
      var label = '', token = '', icon = '';
      if (x.menuServiceItemRenderer) {
        var m = x.menuServiceItemRenderer;
        label = txt(m.text); icon = (m.icon && m.icon.iconType) || '';
        token = findFirst(m.serviceEndpoint || m, 'feedbackToken') || '';
      } else if (x.listItemViewModel) {
        var l = x.listItemViewModel;
        label = txt(l.title);
        token = findFirst(l, 'feedbackToken') || '';
      } else return false;
      if (typeof token === 'string' && token) {
        if (/remove from (your )?(watch )?history/i.test(label)) { if (!it.rm) it.rm = token; }
        else if (/not interested/i.test(label) || icon === 'NOT_INTERESTED') { if (!it.ni) it.ni = token; }
        else if (/(don.?t|do not) recommend|channel/i.test(label) && /recommend/i.test(label) || icon === 'REMOVE_CIRCLE') { if (!it.nc) it.nc = token; }
      }
      return true;
    });
  }

  /** Every video / channel / playlist in a response, in document order, plus the next page token. */
  function parseItems(json, scope) {
    var items = [], seen = {};
    walk(scope || json, function (x) {
      for (var k in AD_KEYS) if (k in x) return true;
      var it = null;
      if (x.videoRenderer) it = parseClassicVideo(x.videoRenderer);
      else if (x.compactVideoRenderer) it = parseClassicVideo(x.compactVideoRenderer);
      else if (x.gridVideoRenderer) it = parseClassicVideo(x.gridVideoRenderer);
      else if (x.playlistVideoRenderer) it = parseClassicVideo(x.playlistVideoRenderer);
      else if (x.reelItemRenderer) { it = parseClassicVideo(x.reelItemRenderer); if (it) it.short = true; }
      else if (x.lockupViewModel) it = parseLockup(x.lockupViewModel);
      else if (x.shortsLockupViewModel) it = parseShortsLockup(x.shortsLockupViewModel);
      else if (x.channelRenderer) it = parseChannelRenderer(x.channelRenderer);
      else if (x.gridChannelRenderer) it = parseChannelRenderer(x.gridChannelRenderer);
      else if (x.playlistRenderer) it = parsePlaylistRenderer(x.playlistRenderer);
      else if (x.gridPlaylistRenderer) it = parsePlaylistRenderer(x.gridPlaylistRenderer);
      else return false;
      if (it) {
        attachFeedback(it, x);
        var key = it.t + ':' + it.id;
        if (!seen[key]) { seen[key] = 1; items.push(it); }
      }
      return true;
    });
    return { items: items, next: lastToken(scope || json) };
  }

  /** Watch page ("next" endpoint). */
  function parseWatch(j) {
    var tw = j.contents && j.contents.twoColumnWatchNextResults;
    var primary = findFirst(tw || j, 'videoPrimaryInfoRenderer') || {};
    var secondary = findFirst(tw || j, 'videoSecondaryInfoRenderer') || {};
    var owner = (secondary.owner && secondary.owner.videoOwnerRenderer) || {};

    var likes = '';
    walk(primary.videoActions || {}, function (x) {
      if (likes) return true;
      var b = x.likeButtonViewModel;
      if (b) {
        walk(b, function (y) {
          if (!likes && typeof y.title === 'string' && /^[\d.,]+\s?[KMB]?$/i.test(y.title)) likes = y.title;
          return !!likes;
        });
        return true;
      }
      return false;
    });

    var description = secondary.attributedDescription ? txt(secondary.attributedDescription) : txt(secondary.description);

    var commentsToken = null, commentCount = '';
    walk(tw && tw.results, function (x) {
      var s = x.itemSectionRenderer;
      if (s && s.sectionIdentifier === 'comment-item-section') { commentsToken = lastToken(s); return true; }
      return false;
    });
    (j.engagementPanels || []).forEach(function (p) {
      var r = p.engagementPanelSectionListRenderer;
      if (r && r.panelIdentifier === 'engagement-panel-comments-section') {
        var ctx = findFirst(r, 'contextualInfo');
        if (ctx) commentCount = txt(ctx);
        if (!commentsToken) commentsToken = lastToken(r);
      }
    });

    // YouTube's record of what THIS account already did to the video (LIKE / DISLIKE / INDIFFERENT)
    var likeStatus = '';
    var muts = (j.frameworkUpdates && j.frameworkUpdates.entityBatchUpdate && j.frameworkUpdates.entityBatchUpdate.mutations) || [];
    muts.forEach(function (m) {
      var p = m.payload && m.payload.likeStatusEntity;
      if (p && typeof p.likeStatus === 'string') likeStatus = p.likeStatus;
    });
    if (!likeStatus) {
      walk(primary.videoActions || {}, function (x) {
        if (x.toggleButtonViewModel && typeof x.toggleButtonViewModel.isToggled === 'boolean') {
          likeStatus = x.toggleButtonViewModel.isToggled ? 'LIKE' : '';
          return true;
        }
        return false;
      });
    }
    // params that YouTube's own like buttons send (used as a second try when the plain call is refused)
    var rateParams = {};
    walk(j, function (x) {
      var e = x.likeEndpoint;
      if (e && e.status) {
        var k = e.status === 'LIKE' ? 'like' : e.status === 'DISLIKE' ? 'dislike' : e.status === 'INDIFFERENT' ? 'none' : '';
        var p = e.likeParams || e.dislikeParams || e.removeLikeParams;
        if (k && p && !rateParams[k]) rateParams[k] = p;
      }
      return false;
    });

    var secRoot = tw && tw.secondaryResults;
    var rel = secRoot ? parseItems(j, secRoot) : { items: [], next: null };
    var related = rel.items.filter(function (i) { return i.t === 'v'; });

    return {
      title: txt(primary.title),
      views: txt(primary.viewCount && primary.viewCount.videoViewCountRenderer && primary.viewCount.videoViewCountRenderer.viewCount),
      age: txt(primary.dateText) || txt(primary.relativeDateText),
      likes: likes,
      description: description,
      owner: {
        id: browseIdIn(owner) || '',
        name: txt(owner.title),
        thumb: lastThumb(owner.thumbnail),
        subs: txt(owner.subscriberCountText)
      },
      related: related,
      relatedNext: rel.next,
      commentsToken: commentsToken,
      commentCount: commentCount,
      likeStatus: likeStatus,
      rateParams: rateParams
    };
  }

  /** Comments page (a "next" call with a continuation token). */
  function parseComments(j) {
    var payloads = {};
    var surfaces = {};
    var others = {};      // every other entity of the response, by key (and its payload type)
    var kinds = {};
    var muts = (j.frameworkUpdates && j.frameworkUpdates.entityBatchUpdate && j.frameworkUpdates.entityBatchUpdate.mutations) || [];
    muts.forEach(function (m) {
      var p = m.payload && m.payload.commentEntityPayload;
      if (p && p.key) payloads[p.key] = p;
      var sp = m.payload && m.payload.commentSurfaceEntityPayload;
      if (sp && sp.key) surfaces[sp.key] = sp;
      if (m.payload) {
        Object.keys(m.payload).forEach(function (kind) {
          var o = m.payload[kind];
          kinds[kind] = (kinds[kind] || 0) + 1;
          if (o && typeof o === 'object' && o.key) others[o.key] = { kind: kind, obj: o };
        });
      }
    });

    /** The token YouTube wants for a reply to one comment (found wherever the reply button's data is kept). */
    function replyParamsOf(ctxObj, vm, p) {
      var r = '';
      function scan(o) {
        if (r || !o) return;
        walk(o, function (x) {
          if (r) return true;
          if (typeof x.createReplyParams === 'string') { r = x.createReplyParams; return true; }
          for (var k in x) {
            if (/reply.*params|params.*reply/i.test(k) && typeof x[k] === 'string' && x[k].length > 20) { r = x[k]; return true; }
          }
          return false;
        });
      }
      scan(ctxObj);
      if (!r && vm) {
        ['toolbarSurfaceKey', 'commentSurfaceKey', 'sharedKey', 'sharedSurfaceKey', 'toolbarStateKey', 'inlineRepliesKey'].forEach(function (k) {
          var e = vm[k] && others[vm[k]];
          if (!r && e && e.kind !== 'commentEntityPayload') scan(e.obj);
        });
      }
      if (!r) scan(p);
      return r;
    }
    var items = [];
    var token = null;
    var debug = '';
    var likeDebug = '';

    /** Compact picture of an object's keys (values cut short), to see where YouTube keeps the reply button's data. */
    function shape(o, d) {
      if (o === null || o === undefined) return o;
      if (typeof o === 'string') return o.length > 28 ? o.slice(0, 28) + '~' + o.length : o;
      if (typeof o !== 'object') return o;
      if (d > 6) return '{..}';
      if (Array.isArray(o)) return o.length ? [shape(o[0], d + 1)] : [];
      var r = {};
      Object.keys(o).slice(0, 24).forEach(function (k) { r[k] = shape(o[k], d + 1); });
      return r;
    }

    /** The like / dislike (and take-back) actions of one comment: base64 strings inside performCommentActionEndpoint. */
    function actionsOf(ctxObj, vm, p) {
      var out = { like: '', dislike: '', unlike: '', undislike: '' };
      function scan(o, path, depth) {
        if (!o || typeof o !== 'object' || depth > 14) return;
        if (o.performCommentActionEndpoint && typeof o.performCommentActionEndpoint.action === 'string') {
          var a = o.performCommentActionEndpoint.action;
          var pl = path.toLowerCase();
          var undo = /(^|[^n])toggled|unlike|undislike|remove|undo/.test(path.replace(/untoggled/gi, 'x'));
          var dis = pl.indexOf('dislike') >= 0;
          if (dis) { if (undo) { if (!out.undislike) out.undislike = a; } else if (!out.dislike) out.dislike = a; }
          else if (pl.indexOf('like') >= 0) { if (undo) { if (!out.unlike) out.unlike = a; } else if (!out.like) out.like = a; }
        }
        Object.keys(o).forEach(function (k) { scan(o[k], path + '.' + k, depth + 1); });
      }
      scan(ctxObj, '', 0);
      if (vm) {
        ['toolbarSurfaceKey', 'commentSurfaceKey', 'sharedKey', 'sharedSurfaceKey', 'toolbarStateKey'].forEach(function (k) {
          var e = vm[k] && others[vm[k]];
          if (e && e.kind !== 'commentEntityPayload') scan(e.obj, e.kind, 0);
        });
      }
      scan(p, 'comment', 0);
      // Second pass: when the path names didn't tell "do" from "undo", the first action of a button is the do and the
      // second one the undo (YouTube lists a toggle button's default state before its toggled state).
      var likeList = [], disList = [];
      function collect(o, path, depth) {
        if (!o || typeof o !== 'object' || depth > 14) return;
        if (o.performCommentActionEndpoint && typeof o.performCommentActionEndpoint.action === 'string') {
          var a = o.performCommentActionEndpoint.action, pl = path.toLowerCase();
          var list = pl.indexOf('dislike') >= 0 ? disList : (pl.indexOf('like') >= 0 ? likeList : null);
          if (list && list.indexOf(a) < 0) list.push(a);
        }
        Object.keys(o).forEach(function (k) { collect(o[k], path + '.' + k, depth + 1); });
      }
      collect(ctxObj, '', 0);
      if (vm) {
        ['toolbarSurfaceKey', 'commentSurfaceKey', 'sharedKey', 'sharedSurfaceKey', 'toolbarStateKey'].forEach(function (k) {
          var e = vm[k] && others[vm[k]];
          if (e && e.kind !== 'commentEntityPayload') collect(e.obj, e.kind, 0);
        });
      }
      collect(p, 'comment', 0);
      if (!out.like && likeList.length) out.like = likeList[0];
      if (!out.unlike && likeList.length > 1) out.unlike = likeList[1];
      if (!out.dislike && disList.length) out.dislike = disList[0];
      if (!out.undislike && disList.length > 1) out.undislike = disList[1];
      // still nothing for "like": keep a picture of where YouTube keeps the buttons (the app copies it on the failed tap)
      if (!out.like && !likeDebug) {
        try {
          var rel = {};
          ['toolbarSurfaceKey', 'commentSurfaceKey', 'sharedKey', 'sharedSurfaceKey', 'toolbarStateKey'].forEach(function (k) {
            var e = vm && vm[k] && others[vm[k]];
            if (e && e.kind !== 'commentEntityPayload') rel[k + ':' + e.kind] = shape(e.obj, 1);
          });
          likeDebug = 'LIKE ' + JSON.stringify({ kinds: kinds, related: rel, vm: shape(vm, 2) }).slice(0, 3600);
        } catch (e2) { /* ignore */ }
      }
      return out;
    }

    function toItem(p, replyToken, replyParams, acts) {
      return {
        replyParams: replyParams || '',
        acts: acts || {},
        t: 'm',
        author: (p.author && p.author.displayName) || '',
        avatar: fixUrl(p.author && p.author.avatarThumbnailUrl),
        text: (p.properties && p.properties.content && p.properties.content.content) || '',
        likes: (p.toolbar && (p.toolbar.likeCountNotliked || p.toolbar.likeCountLiked)) || '',
        age: (p.properties && p.properties.publishedTime) || '',
        replies: (p.toolbar && p.toolbar.replyCount) || 0,
        replyToken: replyToken || ''
      };
    }

    /** Continuation that loads the replies of a comment thread (null when it has none). */
    function replyTokenOf(replies) {
      if (!replies) return null;
      var t = lastToken(replies);
      if (t) return t;
      walk(replies, function (x) {
        if (!t && x.continuationCommand && x.continuationCommand.token) { t = x.continuationCommand.token; return true; }
        return !!t;
      });
      return t;
    }

    walk(j.onResponseReceivedEndpoints || j, function (x) {
      if (x.commentThreadRenderer) {
        var thread = x.commentThreadRenderer;
        var vm = thread.commentViewModel && thread.commentViewModel.commentViewModel;
        var p = vm && payloads[vm.commentKey];
        if (p) {
          var rpar = replyParamsOf(thread, vm, p);
          if (!rpar && !debug) {
            try {
              var rel = {};
              ['toolbarSurfaceKey', 'commentSurfaceKey', 'sharedKey', 'sharedSurfaceKey', 'toolbarStateKey', 'inlineRepliesKey'].forEach(function (k) {
                var e = vm && vm[k] && others[vm[k]];
                if (e && e.kind !== 'commentEntityPayload') rel[k + ':' + e.kind] = shape(e.obj, 1);
              });
              var tr = {};
              Object.keys(thread).forEach(function (k) { if (k !== 'replies' && k !== 'commentViewModel') tr[k] = shape(thread[k], 3); });
              var dd = { kinds: kinds, related: rel, thread: tr };
              debug = JSON.stringify(dd).slice(0, 3800);
            } catch (e) { /* ignore */ }
          }
          items.push(toItem(p, replyTokenOf(thread.replies), rpar, actionsOf(thread, vm, p)));
        }
        return true;
      }
      // Replies come without the thread wrapper: a plain commentViewModel per reply.
      if (x.commentViewModel) {
        var rv = x.commentViewModel.commentViewModel || x.commentViewModel;
        var rp = rv && payloads[rv.commentKey];
        if (rp) items.push(toItem(rp, null, replyParamsOf(x, rv, rp), actionsOf(x, rv, rp)));
        return true;
      }
      if (x.continuationItemRenderer) {
        var c = x.continuationItemRenderer;
        var t = c.continuationEndpoint && c.continuationEndpoint.continuationCommand && c.continuationEndpoint.continuationCommand.token;
        if (t) token = t;
        return true;
      }
      return false;
    });
    var disabled = !items.length && JSON.stringify(j).indexOf('Comments are turned off') >= 0;
    // token needed to post a new top-level comment (only present in the first page, and only when signed in)
    var create = '';
    walk(j, function (x) {
      if (create) return true;
      if (typeof x.createCommentParams === 'string') { create = x.createCommentParams; return true; }
      return false;
    });
    return { items: items, next: token, disabled: disabled, create: create, debug: debug || likeDebug };
  }

  /** Channel page (browse with browseId, optionally followed by continuation). */
  function parseChannel(j) {
    var meta = (j.metadata && j.metadata.channelMetadataRenderer) || {};
    var hdr = j.header && (j.header.pageHeaderRenderer || j.header.c4TabbedHeaderRenderer) || {};
    var subs = '', handle = '', videos = '', banner = '';
    var subscribed = false;

    walk(hdr, function (x) {
      if (x.metadataRows) {
        x.metadataRows.forEach(function (row) {
          rowText(row).forEach(function (s) {
            if (/subscriber/i.test(s)) subs = s;
            else if (/^@/.test(s)) handle = s;
            else if (/\bvideos?\b/i.test(s)) videos = s;
          });
        });
        return true;
      }
      return false;
    });
    if (hdr.subscriberCountText && !subs) subs = txt(hdr.subscriberCountText);

    // banner
    walk(hdr, function (x) {
      if (!banner && x.imageBannerViewModel && x.imageBannerViewModel.image) { banner = lastThumb(x.imageBannerViewModel.image); return true; }
      if (!banner && x.banner && x.banner.thumbnails) { banner = lastThumb(x.banner); return true; }
      return false;
    });

    // "Subscribed" button title means we are already subscribed
    walk(hdr.content ? hdr.content.pageHeaderViewModel && hdr.content.pageHeaderViewModel.actions : hdr, function (x) {
      if (x.buttonViewModel && /^subscribed$/i.test(x.buttonViewModel.title || '')) subscribed = true;
      if (x.subscribeButtonRenderer && x.subscribeButtonRenderer.subscribed) subscribed = true;
      return false;
    });

    var title = meta.title || '';
    if (!title && hdr.content) title = txt(findFirst(hdr.content, 'text'));
    var tabs = [];
    var tabsRoot = j.contents && j.contents.twoColumnBrowseResultsRenderer && j.contents.twoColumnBrowseResultsRenderer.tabs;
    (tabsRoot || []).forEach(function (t) {
      var r = t.tabRenderer;
      if (r) tabs.push({ title: r.title || '', url: (r.endpoint && r.endpoint.commandMetadata && r.endpoint.commandMetadata.webCommandMetadata && r.endpoint.commandMetadata.webCommandMetadata.url) || '', selected: !!r.selected, params: r.endpoint && r.endpoint.browseEndpoint && r.endpoint.browseEndpoint.params || '' });
    });

    var list = parseItems(j, j.contents);
    return {
      channel: {
        id: meta.externalId || '',
        title: title,
        desc: meta.description || '',
        thumb: fixUrl(meta.avatar && meta.avatar.thumbnails && meta.avatar.thumbnails[0] && meta.avatar.thumbnails[0].url),
        banner: banner,
        subs: subs,
        handle: handle,
        videos: videos,
        subscribed: subscribed
      },
      tabs: tabs,
      items: list.items,
      next: list.next
    };
  }


  /** Channel "Posts" (community) tab: text, pictures, a shared video, like / comment counts. */
  function parsePosts(j) {
    var items = [], token = null, seen = {};
    walk(j, function (x) {
      var post = x.backstagePostRenderer || x.postRenderer;
      if (post) {
        var id = post.postId || '';
        if (!id || seen[id]) return true;
        seen[id] = 1;
        var att = post.backstageAttachment || post.attachment || null;
        var image = '', vid = '', vtitle = '', poll = '';
        if (att) {
          var img = findFirst(att, 'backstageImageRenderer');
          if (img) image = lastThumb(img.image);
          if (!image) {
            var th = findFirst(att, 'thumbnail');
            if (th && th.thumbnails) image = lastThumb(th);
          }
          var vr = att.videoRenderer || findFirst(att, 'videoRenderer');
          if (vr && vr.videoId) { vid = vr.videoId; vtitle = txt(vr.title); if (!image) image = lastThumb(vr.thumbnail); }
          var pr = att.pollRenderer || findFirst(att, 'pollRenderer');
          if (pr) {
            var ch = (pr.choices || []).map(function (c) { return txt(c.text); }).filter(function (t) { return t; });
            poll = ch.join(' | ');
          }
        }
        var rb = findFirst(post, 'replyButton');
        var comments = rb ? txt(findFirst(rb, 'text') || rb) : '';
        items.push({
          t: 'p', id: id,
          author: txt(post.authorText),
          avatar: lastThumb(post.authorThumbnail),
          text: txt(post.contentText),
          age: txt(post.publishedTimeText),
          likes: txt(post.voteCount) || (post.voteStatus ? '' : ''),
          comments: comments,
          image: image, video: vid, videoTitle: vtitle, poll: poll
        });
        return true;
      }
      if (x.continuationItemRenderer) {
        var c = x.continuationItemRenderer;
        var t = c.continuationEndpoint && c.continuationEndpoint.continuationCommand && c.continuationEndpoint.continuationCommand.token;
        if (t) token = t;
        return true;
      }
      return false;
    });
    return { items: items, next: token };
  }

  /**
   * Owner, video count, views, last update and cover of a playlist page. YouTube serves two header layouts
   * (the older playlistHeaderRenderer + sidebar, the newer pageHeaderViewModel) so every field is optional;
   * the app shows what it gets and works without the rest.
   */
  function parsePlaylistHeader(j) {
    var h = { owner: '', thumb: '', count: '', views: '', updated: '' };
    function classify(t) {
      t = (t || '').trim();
      if (!t) return;
      // "Updated today" has no number, the other two always do
      if (/updated/i.test(t)) { if (!h.updated) h.updated = t; return; }
      if (!/\d/.test(t)) return;
      if (/\bvideos?\b/i.test(t)) { if (!h.count) h.count = t; }
      else if (/\bviews?\b/i.test(t)) { if (!h.views) h.views = t; }
    }
    function firstImage(root_) {
      var u = '';
      walk(root_ || {}, function (x) {
        if (u) return true;
        if (x.thumbnails || x.sources) { u = lastThumb(x); return !!u; }
        return false;
      });
      return u;
    }
    try {
      // older layout
      var ph = findFirst(j, 'playlistHeaderRenderer');
      if (ph) {
        h.owner = txt(ph.ownerText);
        classify(txt(ph.numVideosText));
        classify(txt(ph.viewCountText));
        h.thumb = firstImage(ph.playlistHeaderBanner);
      }
      var side = findFirst(j, 'playlistSidebarPrimaryInfoRenderer');
      if (side) {
        (side.stats || []).forEach(function (s) { classify(txt(s)); });
        if (!h.thumb) h.thumb = firstImage(side.thumbnailRenderer);
      }
      var sec = findFirst(j, 'playlistSidebarSecondaryInfoRenderer');
      if (sec && !h.owner) {
        var vo = findFirst(sec, 'videoOwnerRenderer');
        if (vo) h.owner = txt(vo.title);
      }
      // newer layout
      var pv = findFirst(j, 'pageHeaderViewModel');
      if (pv) {
        var cm = pv.metadata && pv.metadata.contentMetadataViewModel;
        ((cm && cm.metadataRows) || []).forEach(function (row) {
          (row.metadataParts || []).forEach(function (part) {
            var av = part.avatarStack && part.avatarStack.avatarStackViewModel;
            if (av) { if (!h.owner) h.owner = txt(av.text); }
            else classify(txt(part.text));
          });
        });
        if (!h.thumb) h.thumb = firstImage(pv.heroImage);
      }
      h.owner = (h.owner || '').replace(/^by\s+/i, '').trim();
    } catch (e) { /* the header is a nicety: the videos below must still load */ }
    return h;
  }

  /** Playlist page: header title + facts + videos. */
  function parsePlaylist(j) {
    var title = (j.metadata && j.metadata.playlistMetadataRenderer && j.metadata.playlistMetadataRenderer.title) || '';
    var list = parseItems(j, j.contents || j.onResponseReceivedActions || j);
    var h = parsePlaylistHeader(j);
    return {
      title: title, owner: h.owner, thumb: h.thumb, count: h.count, views: h.views, updated: h.updated,
      items: list.items.filter(function (i) { return i.t === 'v'; }), next: list.next
    };
  }

  function parseAccount(j) {
    var h = findFirst(j, 'activeAccountHeaderRenderer');
    if (!h) return { name: '', thumb: '', handle: '' };
    return { name: txt(h.accountName), thumb: lastThumb(h.accountPhoto), handle: txt(h.channelHandle) };
  }


  /** Official Shorts feed ("reel/reel_watch_sequence"): entries[].command.reelWatchEndpoint, in YouTube's own personalised order. */
  /** Ads sit in the Shorts sequence as ordinary-looking entries: they carry ad parameters / ad containers. */
  var AD_KEY_RE = /^(adSlotRenderer|adClientParams|adLayout\w*|adPlacement\w*)$|[Pp]romotedSparkles/;
  function isAdEntry(e) {
    var ad = false;
    walk(e, function (x) {
      if (ad) return true;
      for (var k in x) {
        if (AD_KEYS[k] || AD_KEY_RE.test(k)) { ad = true; return true; }
      }
      return false;
    });
    return ad;
  }

  function parseReelSequence(j, keepAds) {
    var items = [], seen = {}, lastParams = '', dropped = 0;
    var entries = (j && j.entries) || [];
    entries.forEach(function (e) {
      var ep = e && e.command && e.command.reelWatchEndpoint;
      if (ep && ep.sequenceParams) lastParams = ep.sequenceParams;   // keep the sequence going even over an ad
      if (!ep || !ep.videoId || seen[ep.videoId]) return;
      if (!keepAds && isAdEntry(e)) { dropped++; return; }            // ads never reach the feed
      seen[ep.videoId] = 1;
      if (ep.sequenceParams) lastParams = ep.sequenceParams;
      var title = txt(findFirst(e, 'reelTitleText')) || txt(findFirst(e, 'headline'));
      var ch = txt(findFirst(e, 'channelTitleText'));
      items.push(mkVideo(ep.videoId, title, ch, browseIdIn(e), -1, '', '', false, true));
    });
    // the ad filter must never empty a feed: if it removed everything, trust YouTube's list as it is
    if (!keepAds && !items.length && dropped) return parseReelSequence(j, true);
    var tok = null;
    var ce = j && j.continuationEndpoint && j.continuationEndpoint.continuationCommand;
    if (ce && ce.token) tok = 'c:' + ce.token;
    else if (lastParams) tok = 's:' + lastParams;
    return { items: items, next: tok };
  }

  /** YouTube's notification inbox (\"notification/get_notification_menu\"): uploads, live, comments, replies, mentions... */
  function parseNotifications(j) {
    var items = [], seen = {}, token = null;
    walk(j, function (x) {
      if (x.continuationItemRenderer) {
        var c = x.continuationItemRenderer;
        var t = findFirst(c, 'ctoken') || findFirst(c, 'token');
        if (typeof t === 'string' && t) token = t;
        return true;
      }
      var n = x.notificationRenderer;
      if (!n) return false;
      var msg = txt(n.shortMessage) || txt(n.title) || txt(n.message);
      var ep = n.navigationEndpoint || {};
      var meta = ep.commandMetadata && ep.commandMetadata.webCommandMetadata;
      var url = (meta && meta.url) || '';
      var vid = (ep.watchEndpoint && ep.watchEndpoint.videoId) || (ep.reelWatchEndpoint && ep.reelWatchEndpoint.videoId) || '';
      var m;
      if (!vid && url && (m = url.match(/[?&]v=([\w-]{11})/) || url.match(/\/shorts\/([\w-]{11})/))) vid = m[1];
      var thumb = lastThumb(n.videoThumbnail);
      if (!vid && thumb && (m = thumb.match(/\/vi\/([\w-]{11})\//))) vid = m[1];
      var chId = (ep.browseEndpoint && typeof ep.browseEndpoint.browseId === 'string' && ep.browseEndpoint.browseId.indexOf('UC') === 0)
        ? ep.browseEndpoint.browseId : '';
      if (!chId && url && (m = url.match(/\/channel\/(UC[\w-]+)/))) chId = m[1];
      var handle = (!vid && !chId && url && (m = url.match(/^\/(@[^\/?#]+)/))) ? m[1] : '';
      var low = msg.toLowerCase();
      var kind = 'other';
      if (/mentioned|tagged/.test(low)) kind = 'mention';
      else if (/repl(y|ied)/.test(low)) kind = 'reply';
      else if (/comment/.test(low)) kind = 'comment';
      else if (/premiere/.test(low)) kind = 'premiere';
      else if (/\blive\b|streaming/.test(low)) kind = 'live';
      else if (/upload|posted|new video|published/.test(low) || (vid && thumb)) kind = 'upload';
      var id = n.notificationId || (vid + '|' + msg + '|' + txt(n.sentTimeText));
      if (!msg && !vid && !chId) return true;
      if (seen[id]) return true;
      seen[id] = 1;
      items.push({
        t: 'n', id: id, msg: msg, time: txt(n.sentTimeText), avatar: lastThumb(n.thumbnail), thumb: thumb,
        read: !!n.read, kind: kind, vid: vid, chId: chId, handle: handle
      });
      return true;
    });
    return { items: items, next: token };
  }

  var P = {
    parseNotifications: parseNotifications, txt: txt, parseItems: parseItems, parseWatch: parseWatch, parseComments: parseComments,
    parseChannel: parseChannel, parsePlaylist: parsePlaylist, parseAccount: parseAccount, parseReelSequence: parseReelSequence, durSeconds: durSeconds
  };

  if (typeof module !== 'undefined' && module.exports) { module.exports = P; }
  if (typeof window === 'undefined') return; // node: parsers only

  /* ================================================================== 2. transport */

  var ENV = null;

  function loadEnvCache() {
    try {
      var c = JSON.parse(localStorage.getItem('ct_env') || 'null');
      if (c && c.key && c.version && Date.now() - c.at < 6 * 3600 * 1000) return c;
    } catch (e) { /* ignore */ }
    return null;
  }

  async function boot() {
    if (ENV) return ENV;
    var cached = loadEnvCache();
    if (cached) { ENV = cached; return ENV; }
    var html = await (await fetch('/', { credentials: 'include' })).text();
    function pick(re) { var m = html.match(re); return m ? m[1] : ''; }
    var key = pick(/"INNERTUBE_API_KEY":"([^"]+)"/);
    var version = pick(/"INNERTUBE_CONTEXT_CLIENT_VERSION":"([^"]+)"/) || pick(/"clientVersion":"([^"]+)"/);
    var visitor = pick(/"visitorData":"([^"]+)"/);
    if (!key || !version) throw new Error('Could not read YouTube config');
    ENV = { key: key, version: version, visitor: visitor, at: Date.now() };
    try { localStorage.setItem('ct_env', JSON.stringify(ENV)); } catch (e) { /* ignore */ }
    return ENV;
  }

  async function sapHash(origin) {
    function ck(n) { var m = document.cookie.match(new RegExp('(?:^|;\\s*)' + n + '=([^;]+)')); return m ? m[1] : null; }
    var ts = Math.floor(Date.now() / 1000);
    async function h(v) {
      var buf = await crypto.subtle.digest('SHA-1', new TextEncoder().encode(ts + ' ' + v + ' ' + origin));
      return ts + '_' + Array.prototype.map.call(new Uint8Array(buf), function (x) { return ('0' + x.toString(16)).slice(-2); }).join('');
    }
    var main = ck('SAPISID') || ck('__Secure-3PAPISID');
    if (!main) return null;
    var parts = ['SAPISIDHASH ' + await h(main)];
    var p1 = ck('__Secure-1PAPISID'), p3 = ck('__Secure-3PAPISID');
    if (p1) parts.push('SAPISID1PHASH ' + await h(p1));
    if (p3) parts.push('SAPISID3PHASH ' + await h(p3));
    return parts.join(' ');
  }

  var LOCALE = { hl: 'en', gl: 'IN' };

  async function call(endpoint, body, retried) {
    var env = await boot();
    var context = {
      client: {
        clientName: 'WEB', clientVersion: env.version, hl: LOCALE.hl, gl: LOCALE.gl,
        visitorData: env.visitor, platform: 'DESKTOP', userAgent: navigator.userAgent
      },
      user: { lockedSafetyMode: false },
      request: { useSsl: true }
    };
    var headers = {
      'Content-Type': 'application/json',
      'X-Goog-AuthUser': '0',
      'X-Origin': location.origin,
      'X-Youtube-Client-Name': '1',
      'X-Youtube-Client-Version': env.version
    };
    if (env.visitor) headers['X-Goog-Visitor-Id'] = env.visitor;
    var auth = await sapHash(location.origin);
    if (auth) headers['Authorization'] = auth;
    var r = await fetch('/youtubei/v1/' + endpoint + '?prettyPrint=false&key=' + env.key, {
      method: 'POST', headers: headers, credentials: 'include',
      body: JSON.stringify(Object.assign({ context: context }, body))
    });
    if ((r.status === 400 || r.status === 403) && !retried) {   // stale cached config: refresh it and try once more
      try { localStorage.removeItem('ct_env'); } catch (e) { /* ignore */ }
      ENV = null;
      return call(endpoint, body, true);
    }
    if (!r.ok) throw new Error('HTTP ' + r.status);
    return r.json();
  }

  var FILTERS = { video: 'EgIQAQ%3D%3D', channel: 'EgIQAg%3D%3D', playlist: 'EgIQAw%3D%3D' };
  var CHANNEL_VIDEOS = 'EgZ2aWRlb3PyBgQKAjoA';

  function browse(id, token) { return call('browse', token ? { continuation: token } : { browseId: id }); }

  var COMMANDS = {
    status: async function () {
      return { loggedIn: /(?:^|;\s*)SAPISID=/.test(document.cookie) || /(?:^|;\s*)__Secure-3PAPISID=/.test(document.cookie) };
    },
    reset: async function () { ENV = null; try { localStorage.removeItem('ct_env'); } catch (e) { /* ignore */ } return {}; },
    locale: async function (a) { LOCALE = { hl: a.hl || 'en', gl: a.gl || 'IN' }; ENV = null; return {}; },
    home: async function (a) { return parseItems(await browse('FEwhat_to_watch', a.token)); },
    feed: async function (a) { return parseItems(await browse('FEsubscriptions', a.token)); },
    /** Watch history. Every entry carries the day heading YouTube put above it ("Today", "Yesterday", "Sunday", "Oct 20"). */
    history: async function (a) {
      var j = await browse('FEhistory', a.token);
      var sections = [];
      walk(j, function (x) {
        if (x.itemSectionRenderer) { sections.push(x.itemSectionRenderer); return true; }
        return false;
      });
      var items = [], seen = {};
      sections.forEach(function (s) {
        var h = s.header && (s.header.itemSectionHeaderRenderer || s.header.richSectionHeaderRenderer);
        var day = h ? txt(h.title) : '';
        parseItems(j, s).items.forEach(function (it) {
          var key = it.t + ':' + it.id;
          if (seen[key]) return;
          seen[key] = 1;
          if (day) it.day = day;
          items.push(it);
        });
      });
      if (!items.length) return parseItems(j);
      return { items: items, next: lastToken(j) };
    },
    /** Clears the whole watch history of the signed-in account (uses the token of YouTube's own "Clear all watch history" button). */
    clearHistory: async function () {
      var j = await browse('FEhistory');
      var token = '';
      walk(j, function (x) {
        if (token) return true;
        var d = x.confirmDialogRenderer;
        if (d) {
          var t = txt(d.title) + ' ' + txt(d.dialogMessages && d.dialogMessages[0]);
          if (/history/i.test(t) && /clear/i.test(t)) {
            var f = findFirst(d.confirmButton || d, 'feedbackToken');
            if (f) { token = f; return true; }
          }
        }
        return false;
      });
      if (!token) return { ok: false, error: 'no clear token' };
      await call('feedback', { feedbackTokens: [token], isFeedbackTokenUnencrypted: false, shouldMerge: false });
      return { ok: true };
    },
    playlists: async function (a) { return parseItems(await browse('FEplaylist_aggregation', a.token)); },
    channels: async function (a) { return parseItems(await browse('FEchannels', a.token)); },
    search: async function (a) {
      var body = a.token ? { continuation: a.token } : { query: a.q, params: decodeURIComponent(FILTERS[a.filter] || FILTERS.video) };
      return parseItems(await call('search', body));
    },
    channel: async function (a) {
      var j = a.token ? await browse(null, a.token)
                      : await call('browse', { browseId: a.id, params: CHANNEL_VIDEOS });
      var r = parseChannel(j);
      return a.token ? { items: r.items, next: r.next } : r;
    },
    /** Channel posts. a.id = channel, a.token = continuation of the next page. */
    posts: async function (a) {
      if (a.token) return parsePosts(await browse(null, a.token));
      var params = 'EgVwb3N0c_IBBAoCSgA=';
      try {
        var main = parseChannel(await call('browse', { browseId: a.id }));
        var tab = (main.tabs || []).filter(function (t) { return /^(posts|community)$/i.test(t.title) && t.params; })[0];
        if (tab) params = tab.params;
      } catch (e) { /* use the default */ }
      return parsePosts(await call('browse', { browseId: a.id, params: params }));
    },
    /** A channel's Shorts / Live / Playlists tab. a.tab = shorts | live | playlists, a.token = next page. */
    channelTab: async function (a) {
      if (a.token) { var r0 = parseItems(await browse(null, a.token)); return { items: r0.items, next: r0.next }; }
      var def = { shorts: 'EgZzaG9ydHPyBgUKA5oBAA==', live: 'EgdzdHJlYW1z8gYECgJ6AA==', playlists: 'EglwbGF5bGlzdHPyBgQKAkIA' };
      var url = { shorts: /\/shorts$/, live: /\/streams$/, playlists: /\/playlists$/ }[a.tab];
      var params = def[a.tab];
      try {
        var main = parseChannel(await call('browse', { browseId: a.id }));
        var tab = (main.tabs || []).filter(function (t) { return t.params && url && url.test(t.url || ''); })[0];
        if (tab) params = tab.params;
      } catch (e) { /* use the default */ }
      var r = parseItems(await call('browse', { browseId: a.id, params: params }));
      return { items: r.items, next: r.next };
    },
    handle: async function (a) {
      var j = await call('navigation/resolve_url', { url: 'https://www.youtube.com/' + a.handle });
      return { id: (j.endpoint && j.endpoint.browseEndpoint && j.endpoint.browseEndpoint.browseId) || '' };
    },
    playlist: async function (a) {
      var j = await browse('VL' + a.id, a.token);
      var r = parsePlaylist(j);
      return a.token ? { items: r.items, next: r.next } : r;
    },
    /** YouTube's own personalised Shorts feed for the logged-in account. a.token: "c:<continuation>" or "s:<sequenceParams>". */
    shorts: async function (a) {
      var tries = [];
      if (a.token && a.token.indexOf('c:') === 0) tries.push({ continuation: a.token.slice(2) });
      else if (a.token && a.token.indexOf('s:') === 0) tries.push({ sequenceParams: a.token.slice(2) });
      else { tries.push({ sequenceParams: 'CA8=' }); tries.push({ sequenceParams: 'CA8%3D' }); }
      var lastErr = null;
      for (var i = 0; i < tries.length; i++) {
        try {
          var r = parseReelSequence(await call('reel/reel_watch_sequence', tries[i]));
          if (r.items.length) return r;
        } catch (e) { lastErr = e; }
      }
      throw lastErr || new Error('empty shorts feed');
    },
    watch: async function (a) { return parseWatch(await call('next', { videoId: a.id, contentCheckOk: true, racyCheckOk: true })); },
    related: async function (a) {
      var j = await call('next', { continuation: a.token });
      var r = parseItems(j);
      return { items: r.items.filter(function (i) { return i.t === 'v'; }), next: r.next };
    },
    comments: async function (a) { return parseComments(await call('next', { continuation: a.token })); },
    account: async function () { return parseAccount(await call('account/account_menu', {})); },
    /** Sends a feedback token ("Not interested" / "Don't recommend channel") to YouTube. */
    feedback: async function (a) {
      await call('feedback', { feedbackTokens: [a.token], isFeedbackTokenUnencrypted: false, shouldMerge: false });
      return { ok: true };
    },
    /** Creates a real playlist in the signed-in YouTube account and adds the supplied video(s). */
    createPlaylist: async function (a) {
      var title = String(a.title || '').trim();
      if (!title) throw new Error('Enter a playlist name.');
      var body = { title: title, privacyStatus: a.privacyStatus || 'PRIVATE' };
      if (Array.isArray(a.videoIds) && a.videoIds.length) body.videoIds = a.videoIds;
      var r = await call('playlist/create_playlist', body);
      var id = findFirst(r, 'playlistId') || findFirst(r, 'playlistIdString');
      if (typeof id !== 'string' || !id) throw new Error('YouTube did not return a playlist ID. Check the account session and try again.');
      return { id: id, title: title };
    },
    /** Removes one of the signed-in account's own playlists (never Liked videos / Watch later). */
    deletePlaylist: async function (a) {
      var id = String(a.id || '');
      if (!id || id === 'LL' || id === 'WL') throw new Error('This list cannot be removed.');
      var r = await call('playlist/delete', { playlistId: id });
      if (r && r.errors && r.errors.length) throw new Error('YouTube refused to remove the playlist.');
      return { ok: true };
    },
    subscribe: async function (a) {
      await call(a.on ? 'subscription/subscribe' : 'subscription/unsubscribe',
                 a.on ? { channelIds: [a.id], params: 'EgIIAhgA' } : { channelIds: [a.id] });
      return { ok: true };
    },
    /** Like / dislike / clear the rating of a video (signed-in only). a.status: 'like' | 'dislike' | 'none'. */
    rate: async function (a) {
      var ep = a.status === 'like' ? 'like/like' : a.status === 'dislike' ? 'like/dislike' : 'like/removelike';
      var body = { target: { videoId: a.id } };
      if (a.params) body.params = a.params;
      var r = await call(ep, body);
      if (r && r.errors && r.errors.length) throw new Error('rating refused');
      return { ok: true };
    },
    /** Posts a new top-level comment (signed-in only). a.params = createCommentParams, a.text = the comment. */
    postComment: async function (a) {
      var r = await call('comment/create_comment', { commentText: a.text, createCommentParams: a.params });
      var st = r && r.actionResult && r.actionResult.status;
      if (st && st !== 'STATUS_SUCCEEDED') throw new Error('comment refused (' + st + ')');
      if (r && r.errors && r.errors.length) throw new Error('comment refused');
      return { ok: true };
    },
    /** Likes / dislikes a comment (or takes it back). a.action = the base64 action YouTube gave for that button. */
    commentAction: async function (a) {
      var r = await call('comment/perform_comment_action', { actions: [a.action] });
      if (r && r.errors && r.errors.length) throw new Error('refused');
      var res = r && r.actionResults;
      if (res && res.length && res[0].status && res[0].status !== 'STATUS_SUCCEEDED') throw new Error('refused (' + res[0].status + ')');
      return { ok: true };
    },
    /** Posts a reply to a comment (signed-in only). a.params = createReplyParams of that comment, a.text = the reply. */
    postReply: async function (a) {
      var r = await call('comment/create_comment_reply', { commentText: a.text, createReplyParams: a.params });
      var st = r && r.actionResult && r.actionResult.status;
      if (st && st !== 'STATUS_SUCCEEDED') throw new Error('reply refused (' + st + ')');
      if (r && r.errors && r.errors.length) throw new Error('reply refused');
      return { ok: true };
    },
    /** Connection check: is YouTube really treating this app as a signed-in account? (names only, never cookie values) */
    diag: async function () {
      var names = document.cookie.split(';').map(function (c) { return c.split('=')[0].trim(); })
        .filter(function (n) { return /^(SID|SAPISID|__Secure-[13]PAPISID|__Secure-[13]PSID|LOGIN_INFO)$/.test(n); });
      var out = { cookiesSeenByPage: names.join(',') || 'none', authHeaderSent: !!(await sapHash(location.origin)) };
      var j = await call('browse', { browseId: 'FEwhat_to_watch' });
      var loggedIn = null;
      walk(j && j.responseContext, function (o) {
        if (o && o.key === 'logged_in') { loggedIn = o.value; return true; }
      });
      out.youtubeSaysLoggedIn = loggedIn === '1' ? 'YES' : loggedIn === '0' ? 'NO (feed is generic)' : 'unknown';
      out.homeItems = parseItems(j).items.length;
      try { var a = await call('account/account_menu', {}); out.account = parseAccount(a).name || 'no name'; } catch (e) { out.account = 'error ' + e.message; }
      return out;
    },
    /** Tells YouTube a video was watched (the same ping the official player sends), so history and recommendations learn from it. */
    markWatched: async function (a) {
      var out = { ok: false, loggedIn: /(?:^|;\s*)(__Secure-3)?PAPISID=/.test(document.cookie) };
      var j = await call('player', { videoId: a.id, contentCheckOk: true, racyCheckOk: true });
      out.playability = (j && j.playabilityStatus && j.playabilityStatus.status) || 'none';
      var pt = j && j.playbackTracking;
      var pb = pt && pt.videostatsPlaybackUrl && pt.videostatsPlaybackUrl.baseUrl;
      var wt = pt && pt.videostatsWatchtimeUrl && pt.videostatsWatchtimeUrl.baseUrl;
      out.hasPlaybackUrl = !!pb; out.hasWatchtimeUrl = !!wt;
      if (!pb) return out;
      var abc = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_', cpn = '';
      for (var i = 0; i < 16; i++) cpn += abc.charAt(Math.floor(Math.random() * 64));
      function withQ(u, q) { return u + (u.indexOf('?') < 0 ? '?' : '&') + q; }
      async function ping(u) {
        try {
          var r = await fetch(u, { credentials: 'include' });
          return r.status;
        } catch (e) {
          try { await fetch(u, { mode: 'no-cors', credentials: 'include' }); return 'sent-no-cors'; }
          catch (e2) { return 'failed:' + (e2 && e2.message); }
        }
      }
      out.playbackStatus = await ping(withQ(pb, 'ver=2&cpn=' + cpn + '&cmt=0&el=detailpage'));
      if (wt) {
        var t = Math.max(1, Math.floor(a.pos || 10));
        out.watchtimeStatus = await ping(withQ(wt, 'ver=2&cpn=' + cpn + '&cmt=' + t + '&st=0&et=' + t + '&state=playing&el=detailpage&final=1'));
      }
      out.ok = (typeof out.playbackStatus === 'number' && out.playbackStatus < 300) || out.playbackStatus === 'sent-no-cors';
      return out;
    },
    /** YouTube's notification bell (signed-in only). a.token = continuation of the next page. */
    notifications: async function (a) {
      var body = a.token ? { ctoken: a.token } : { notificationsMenuRequestType: 'NOTIFICATIONS_MENU_REQUEST_TYPE_INBOX' };
      return parseNotifications(await call('notification/get_notification_menu', body));
    }
  };

  root.CT = {
    run: function (id, cmd, argsJson) {
      var args = {};
      try { args = JSON.parse(argsJson || '{}'); } catch (e) { /* ignore */ }
      var fn = COMMANDS[cmd];
      if (!fn) { AndroidBridge.onResult(id, false, 'unknown command ' + cmd); return; }
      fn(args).then(
        function (res) { AndroidBridge.onResult(id, true, JSON.stringify(res)); },
        function (err) { AndroidBridge.onResult(id, false, String(err && err.message || err)); }
      );
    },
    version: 1
  };
})(typeof window !== 'undefined' ? window : globalThis);
