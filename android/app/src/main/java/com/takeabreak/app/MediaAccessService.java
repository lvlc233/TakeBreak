package com.takeabreak.app;

import android.service.notification.NotificationListenerService;

public class MediaAccessService extends NotificationListenerService {
    // Android binds this service only after the user grants notification access.
    // VideoPauser reads media sessions; notification content is never stored.
}
