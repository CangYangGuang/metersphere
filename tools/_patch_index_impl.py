from pathlib import Path

def run(ROOT: Path) -> None:
    p = ROOT / "frontend/src/views/api-test/components/requestComposition/index.vue"
    text = p.read_text(encoding="utf-8")
    if "grpcMessageTab" in text and "grpcDebugContentTabs" in text:
        print("index.vue already patched")
        return
    raise SystemExit("index.vue not patched; run manual patch")
