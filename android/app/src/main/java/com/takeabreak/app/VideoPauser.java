package com.takeabreak.app;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;

import java.util.List;

final class VideoPauser {
    private VideoPauser() {}

    static boolean hasAccess(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager.isNotificationListenerAccessGranted(new ComponentName(context, MediaAccessService.class));
    }

    static String pausePlayingVideos(Context context) {
        if (!hasAccess(context)) return "未开启通知使用权";
        MediaSessionManager manager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
        try {
            List<MediaController> sessions = manager.getActiveSessions(new ComponentName(context, MediaAccessService.class));
            int paused = 0;
            for (MediaController controller : sessions) {
                if (!isVideoApp(controller.getPackageName())) continue;
                PlaybackState state = controller.getPlaybackState();
                if (state == null || state.getState() != PlaybackState.STATE_PLAYING) continue;
                controller.getTransportControls().pause();
                paused++;
            }
            return paused > 0 ? "已暂停 " + paused + " 个视频" : "没有检测到正在播放的视频";
        } catch (SecurityException e) {
            return "媒体控制权限不可用";
        } catch (RuntimeException e) {
            return "视频应用未响应暂停";
        }
    }

    static boolean isVideoApp(String packageName) {
        return "tv.danmaku.bili".equals(packageName)
                || "com.google.android.youtube".equals(packageName)
                || "com.ss.android.ugc.aweme".equals(packageName)
                || "com.smile.gifmaker".equals(packageName);
    }
}
