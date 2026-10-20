#!/usr/bin/env python3
"""Minimal hello.HelloService mock on TCP :50051 for MeterSphere gRPC verification."""
from __future__ import annotations

import argparse
import subprocess
import sys
import tempfile
from concurrent import futures
from pathlib import Path

import grpc

PROTO = """syntax = "proto3";

package hello;

service HelloService {
  rpc SayHello (HelloRequest) returns (HelloReply);
}

message HelloRequest {
  string greeting = 1;
}

message HelloReply {
  string reply = 1;
}
"""


def compile_proto(workdir: Path) -> None:
    proto_file = workdir / "hello.proto"
    proto_file.write_text(PROTO, encoding="utf-8")
    cmd = [
        sys.executable,
        "-m",
        "grpc_tools.protoc",
        f"-I{workdir}",
        f"--python_out={workdir}",
        f"--grpc_python_out={workdir}",
        str(proto_file),
    ]
    subprocess.run(cmd, check=True, capture_output=True, text=True)


class HelloServiceServicer:
    def __init__(self, pb2):
        self._pb2 = pb2

    def SayHello(self, request, context):
        greeting = request.greeting or "world"
        return self._pb2.HelloReply(reply=f"hello {greeting}")


def serve(host: str, port: int) -> None:
    workdir = Path(tempfile.mkdtemp(prefix="hello-grpc-mock-"))
    compile_proto(workdir)
    sys.path.insert(0, str(workdir))
    import hello_pb2  # noqa: WPS433
    import hello_pb2_grpc  # noqa: WPS433

    server = grpc.server(futures.ThreadPoolExecutor(max_workers=4))
    hello_pb2_grpc.add_HelloServiceServicer_to_server(HelloServiceServicer(hello_pb2), server)
    bind = f"{host}:{port}"
    server.add_insecure_port(bind)
    server.start()
    print(f"[hello-grpc-mock] listening on {bind}", flush=True)
    server.wait_for_termination()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=50051)
    args = parser.parse_args()
    serve(args.host, args.port)


if __name__ == "__main__":
    main()
