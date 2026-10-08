package com.takeabreak.app;

import android.os.Build;
import java.util.Locale;

final class DeviceSetup {
    final String brand;
    final String path;
    private DeviceSetup(String brand, String path) { this.brand = brand; this.path = path; }

    static DeviceSetup current() {
        String id = (Build.MANUFACTURER + " " + Build.BRAND).toLowerCase(Locale.ROOT);
        if (id.contains("oppo")) return new DeviceSetup("OPPO", "应用详情 → 耗电管理 → 开启「允许完全后台行为」和「允许应用自启动」");
        if (id.contains("realme")) return new DeviceSetup("realme", "应用详情 → 电池/耗电管理 → 允许后台运行和自启动");
        if (id.contains("oneplus")) return new DeviceSetup("一加", "应用详情 → 电池 → 允许后台活动；检查自启动设置");
        if (id.contains("xiaomi") || id.contains("redmi") || id.contains("poco"))
            return new DeviceSetup("小米 / Redmi / POCO", "应用详情 → 省电策略 → 无限制；并在权限设置中允许后台自启动");
        if (id.contains("vivo") || id.contains("iqoo")) return new DeviceSetup("vivo / iQOO", "设置 → 电池 → 后台耗电管理 → 允许后台耗电；并开启自启动");
        if (id.contains("samsung")) return new DeviceSetup("三星", "设置 → 电池 → 后台使用限制 → 将本应用移出休眠列表，加入永不休眠的应用");
        if (id.contains("huawei") || id.contains("honor")) return new DeviceSetup("华为 / 荣耀", "设置 → 应用和服务 → 应用启动管理 → 手动管理 → 允许自启动、后台活动");
        return new DeviceSetup(Build.MANUFACTURER, "应用详情 → 电池/后台使用 → 允许后台运行或选择「不受限制」");
    }
}
