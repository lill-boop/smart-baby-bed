#!/bin/bash
# BabyCam Auto Cleanup Script
# 自动清理过期视频和日志

CLIPS_DIR="/var/log.hdd/babycam/clips"
RING_DIR="/var/log.hdd/babycam/ring"
EVENTS_FILE="/var/log.hdd/babycam/events/events.jsonl"

# 保留天数
CLIPS_KEEP_DAYS=7
RING_KEEP_HOURS=24

echo "[$(date)] Starting BabyCam cleanup..."

# 1. 清理超过7天的视频片段
if [ -d "$CLIPS_DIR" ]; then
    deleted=$(find "$CLIPS_DIR" -name "*.mp4" -mtime +$CLIPS_KEEP_DAYS -delete -print | wc -l)
    echo "Deleted $deleted old clips (>$CLIPS_KEEP_DAYS days)"
fi

# 2. 清理超过24小时的环形录像
if [ -d "$RING_DIR" ]; then
    deleted=$(find "$RING_DIR" -name "*.ts" -mmin +$((RING_KEEP_HOURS * 60)) -delete -print | wc -l)
    echo "Deleted $deleted old ring segments (>$RING_KEEP_HOURS hours)"
fi

# 3. 只保留最近1000条事件记录
if [ -f "$EVENTS_FILE" ]; then
    lines=$(wc -l < "$EVENTS_FILE")
    if [ "$lines" -gt 1000 ]; then
        tail -n 1000 "$EVENTS_FILE" > "${EVENTS_FILE}.tmp"
        mv "${EVENTS_FILE}.tmp" "$EVENTS_FILE"
        echo "Trimmed events.jsonl from $lines to 1000 lines"
    fi
fi

# 4. 显示磁盘使用情况
echo "Disk usage:"
du -sh "$CLIPS_DIR" 2>/dev/null || echo "  clips: N/A"
du -sh "$RING_DIR" 2>/dev/null || echo "  ring: N/A"

echo "[$(date)] Cleanup completed!"
