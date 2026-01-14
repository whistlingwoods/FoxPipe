package us.shandian.giga.get;

import androidx.annotation.NonNull;

public class FinishedMission extends Mission {

    public String thumbnailUrl; // URL للصورة المصغرة

    public FinishedMission() {
    }

    public FinishedMission(@NonNull DownloadMission mission) {
        source = mission.source;
        length = mission.length;
        timestamp = mission.timestamp;
        kind = mission.kind;
        storage = mission.storage;
        thumbnailUrl = mission.thumbnailUrl; // نسخ رابط الصورة المصغرة
    }

}
