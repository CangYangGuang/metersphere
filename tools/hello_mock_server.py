#!/usr/bin/env python3
"""Minimal hello.HelloService mock on 0.0.0.0:50051 for gRPC regression."""
import json
import sys
from concurrent import futures

import grpc

HELLO_PROTO = r'''
syntax = "proto3";
package hello;
service HelloService {
  rpc SayHello (HelloRequest) returns (HelloReply);
}
message HelloRequest { string greeting = 1; }
message HelloReply { string reply = 1; }
'''


def main() -> None:
    from grpc_tools import protoc

    out = __import__('tempfile').mkdtemp()
    proto_path = __import__('os').path.join(out, 'hello.proto')
    with open(proto_path, 'w', encoding='utf-8') as f:
        f.write(HELLO_PROTO)

    protoc.main((
        '',
        f'-I{out}',
        f'--python_out={out}',
        f'--grpc_python_out={out}',
        proto_path,
    ))

    sys.path.insert(0, out)
    import hello_pb2  # type: ignore
    import hello_pb2_grpc  # type: ignore

    class Greeter(hello_pb2_grpc.HelloServiceServicer):
        def SayHello(self, request, context):  # noqa: N802
            greeting = request.greeting or ''
            return hello_pb2.HelloReply(reply=f'hello {greeting}')

    server = grpc.server(futures.ThreadPoolExecutor(max_workers=4))
    hello_pb2_grpc.add_HelloServiceServicer_to_server(Greeter(), server)
    server.add_insecure_port('0.0.0.0:50051')
    server.start()
    print('hello mock listening on 0.0.0.0:50051', flush=True)
    server.wait_for_termination()


if __name__ == '__main__':
    main()
