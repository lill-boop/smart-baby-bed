import os, sys, time, subprocess
from pathlib import Path

BASE = "/var/log.hdd/babycam"
RING = Path(BASE) / "ring"
OUTD = Path(BASE) / "clips"
TMPD = Path(BASE) / "tmp"

def newest_segments(seconds: int) -> list[Path]:
    # ring 文件名可不连续/有0B；这里按 mtime 选最近 seconds 内的有效 ts
    now = time.time()
    segs = []
    for p in RING.glob("*.ts"):
        try:
            st = p.stat()
        except OSError:
            continue
        if st.st_size <= 0:
            continue
        if now - st.st_mtime <= seconds + 5:  # 给点缓冲
            segs.append((st.st_mtime, p))
    segs.sort(key=lambda x: x[0])
    return [p for _, p in segs]

def export_from_list(files: list[Path], out_path: Path):
    TMPD.mkdir(parents=True, exist_ok=True)
    OUTD.mkdir(parents=True, exist_ok=True)
    list_path = TMPD / "list.txt"
    with list_path.open("w", encoding="utf-8") as w:
        for f in files:
            w.write(f"file '{str(f)}'\n")

    cmd_copy = ["ffmpeg","-y","-f","concat","-safe","0","-i",str(list_path),"-c","copy",str(out_path)]
    r = subprocess.run(cmd_copy)
    if r.returncode != 0:
        cmd_re = ["ffmpeg","-y","-f","concat","-safe","0","-i",str(list_path),
                  "-c:v","libx264","-preset","veryfast","-crf","23",str(out_path)]
        subprocess.check_call(cmd_re)

def main():
    # 用法：
    # 1) export_clip.py now 60          -> 导出最近60秒
    # 2) export_clip.py ts <epoch> 60   -> 以某个时刻为中心，导出前后30(共60)（简化版：先取最近60秒也足够）
    if len(sys.argv) < 3:
        print("usage: export_clip.py now <seconds>  OR  export_clip.py ts <epoch> <seconds>")
        sys.exit(2)

    mode = sys.argv[1]
    if mode == "now":
        seconds = int(sys.argv[2])
        files = newest_segments(seconds)
        if len(files) < 3:
            print("not enough segments found.")
            print("found:", len(files))
            sys.exit(1)
        out_name = time.strftime("%Y%m%d_%H%M%S") + f"_{seconds}s.mp4"
        out_path = OUTD / out_name
        export_from_list(files, out_path)
        print("OK:", out_path)
        return

    if mode == "ts":
        # 为了鲁棒，这里不再靠文件名对齐：直接取“事件时刻附近的一段”
        # 简单做法：导出最近 seconds 秒（你后面接 AI 触发时通常都是“现在发生”）
        epoch = int(sys.argv[2])
        seconds = int(sys.argv[3]) if len(sys.argv) >= 4 else 60
        files = newest_segments(seconds)
        if len(files) < 3:
            print("not enough segments found.")
            print("found:", len(files))
            sys.exit(1)
        out_name = time.strftime("%Y%m%d_%H%M%S", time.localtime(epoch)) + f"_{seconds}s.mp4"
        out_path = OUTD / out_name
        export_from_list(files, out_path)
        print("OK:", out_path)
        return

    print("unknown mode:", mode)
    sys.exit(2)

if __name__ == "__main__":
    main()
