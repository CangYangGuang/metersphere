#!/usr/bin/env python3
"""Apply gRPC UI Phase A+B: copy grpc_ui_components and patch index.vue."""
from __future__ import annotations
import importlib.util
import pathlib
ROOT = pathlib.Path(__file__).resolve().parents[1]

def write(path: str, content: str) -> None:
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8", newline=chr(10))
    print("wrote", path)

def write_new_components() -> None:
    components_dir = "frontend/src/views/api-test/components/requestComposition/grpc"
    sources = ROOT / "tools" / "grpc_ui_components"
    for src in sorted(sources.glob("*.vue")):
        write(f"{components_dir}/{src.name}", src.read_text(encoding="utf-8"))

def patch_index_vue() -> None:
    patch_file = ROOT / "tools" / "index_vue_grpc.patch.py"
    spec = importlib.util.spec_from_file_location("index_patch", patch_file)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    mod.apply(ROOT)

def main() -> None:
    write_new_components()
    patch_index_vue()
    print("done")

if __name__ == "__main__":
    main()
