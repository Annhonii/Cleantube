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
    return { items: items, next: token, disabled: disabled, create: create, debug: debug };
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
      if (r) tabs.push({ title: r.title || '', selected: !!r.selected, params: r.endpoint && r.endpoint.browseEndpoint && r.endpoint.browseEndpoint.params || '' });
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

  /** Playlist page: header title + videos. */
  function parsePlaylist(j) {
    var title = (j.metadata && j.metadata.playlistMetadataRenderer && j.metadata.playlistMetadataRenderer.title) || '';
    var list = parseItems(j, j.contents || j.onResponseReceivedActions || j);
    return { title: title, items: list.items.filter(function (i) { return i.t === 'v'; }), next: list.next };
  }

  function parseAccount(j) {
    var h = findFirst(j, 'activeAccountHeaderRenderer');
    if (!h) return { name: '', thumb: '', handle: '' };
    return { name: txt(h.accountName), thumb: lastThumb(h.accountPhoto), handle: txt(h.channelHandle) };
  }


  /** Official Shorts feed ("reel/reel_watch_sequence"): entries[].command.reelWatchEndpoint, in YouTube's own personalised order. */
  function parseReelSequence(j) {
    var items = [], seen = {}, lastParams = '';
    var entries = (j && j.entries) || [];
    entries.forEach(function (e) {
      var ep = e && e.command && e.command.reelWatchEndpoint;
      if (!ep || !ep.videoId || seen[ep.videoId]) return;
      seen[ep.videoId] = 1;
      if (ep.sequenceParams) lastParams = ep.sequenceParams;
      var title = txt(findFirst(e, 'reelTitleText')) || txt(findFirst(e, 'headline'));
      var ch = txt(findFirst(e, 'channelTitleText'));
      items.push(mkVideo(ep.videoId, title, ch, browseIdIn(e), -1, '', '', false, true));
    });
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
    var m = document.cookie.match(/(?:^|;\s*)SAPISID=([^;]+)/) ||
            document.cookie.match(/(?:^|;\s*)__Secure-3PAPISID=([^;]+)/);
    if (!m) return null;
    var ts = Math.floor(Date.now() / 1000);
    var buf = await crypto.subtle.digest('SHA-1', new TextEncoder().encode(ts + ' ' + m[1] + ' ' + origin));
    var hex = Array.prototype.map.call(new Uint8Array(buf), function (b) { return ('0' + b.toString(16)).slice(-2); }).join('');
    return 'SAPISIDHASH ' + ts + '_' + hex;
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
    history: async function (a) { return parseItems(await browse('FEhistory', a.token)); },
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
