#!/usr/bin/env python3
"""Multi-service gRPC mock for MeterSphere scenario testing."""
from __future__ import annotations

import argparse
import subprocess
import sys
import tempfile
from concurrent import futures
from pathlib import Path

import grpc
from grpc_reflection.v1alpha import reflection


PROTO_DIR = Path(__file__).resolve().parents[1] / "handoff" / "grpc-final-handoff" / "test-fixtures" / "proto"


def compile_proto(workdir: Path) -> None:
    for name in ("scenario_common.proto", "scenario_main.proto"):
        (workdir / name).write_text((PROTO_DIR / name).read_text(encoding="utf-8"), encoding="utf-8")
    subprocess.run(
        [
            sys.executable,
            "-m",
            "grpc_tools.protoc",
            f"-I{workdir}",
            f"--python_out={workdir}",
            f"--grpc_python_out={workdir}",
            str(workdir / "scenario_main.proto"),
            str(workdir / "scenario_common.proto"),
        ],
        check=True,
        capture_output=True,
        text=True,
    )


def result(pb2, message: str):
    return pb2.CommonResult(code=pb2.RESULT_CODE_OK, message=message, traceId="trace-msdemo-001")


def money(pb2, amount: float):
    return pb2.Money(currency="CNY", amount=amount)


def address(pb2, request_address=None):
    if request_address and request_address.city:
        return request_address
    return pb2.Address(province="Zhejiang", city="Hangzhou", detail="MeterSphere Test Street")


def user(pb2, user_id: str = "U10001", user_name: str = "demo-user"):
    return pb2.UserProfile(userId=user_id, userName=user_name, level=pb2.USER_LEVEL_VIP, address=address(pb2))


def order_item(pb2, product_id: str = "P10001", quantity: int = 1):
    return pb2.OrderItem(productId=product_id, productName="grpc-product", quantity=quantity, price=money(pb2, 99.9))


def order(pb2, order_id: str = "O10001", user_id: str = "U10001", status=None, items=None):
    order_status = status if status is not None else pb2.ORDER_STATUS_CREATED
    return pb2.OrderSummary(
        orderId=order_id,
        userId=user_id,
        status=order_status,
        items=items or [order_item(pb2, "P10001", 1), order_item(pb2, "P10002", 2)],
        total=money(pb2, 299.7),
    )


def serve(host: str, port: int) -> None:
    workdir = Path(tempfile.mkdtemp(prefix="msdemo-grpc-mock-"))
    compile_proto(workdir)
    sys.path.insert(0, str(workdir))
    import scenario_common_pb2 as common_pb2  # noqa: WPS433
    import scenario_main_pb2 as main_pb2  # noqa: WPS433
    import scenario_main_pb2_grpc as main_grpc  # noqa: WPS433

    class UserService(main_grpc.UserServiceServicer):
        def CreateUser(self, request, context):
            return main_pb2.CreateUserReply(
                result=result(common_pb2, "user created"),
                user=user(common_pb2, "U10001", request.userName or "demo-user"),
            )

        def GetUser(self, request, context):
            return main_pb2.GetUserReply(result=result(common_pb2, "user found"), user=user(common_pb2, request.userId or "U10001"))

    class ProductService(main_grpc.ProductServiceServicer):
        def ListProducts(self, request, context):
            products = [
                common_pb2.Product(productId="P10001", name="grpc-keyboard", stock=88, price=money(common_pb2, 199.0)),
                common_pb2.Product(productId="P10002", name="grpc-mouse", stock=66, price=money(common_pb2, 99.0)),
                common_pb2.Product(productId="P10003", name="grpc-monitor", stock=12, price=money(common_pb2, 1299.0)),
            ]
            return main_pb2.ListProductsReply(result=result(common_pb2, "products listed"), products=products)

        def GetInventory(self, request, context):
            return main_pb2.GetInventoryReply(
                result=result(common_pb2, "inventory found"),
                productId=request.productId or "P10001",
                availableStock=88,
            )

    class OrderService(main_grpc.OrderServiceServicer):
        def CreateOrder(self, request, context):
            items = list(request.items) or [order_item(common_pb2)]
            return main_pb2.CreateOrderReply(
                result=result(common_pb2, "order created"),
                order=order(common_pb2, "O10001", request.userId or "U10001", common_pb2.ORDER_STATUS_CREATED, items),
            )

        def GetOrder(self, request, context):
            return main_pb2.GetOrderReply(
                result=result(common_pb2, "order found"),
                order=order(common_pb2, request.orderId or "O10001"),
            )

    class PaymentService(main_grpc.PaymentServiceServicer):
        def PayOrder(self, request, context):
            return main_pb2.PayOrderReply(
                result=result(common_pb2, "order paid"),
                paymentId="PAY10001",
                status=common_pb2.ORDER_STATUS_PAID,
            )

        def RefundOrder(self, request, context):
            return main_pb2.RefundOrderReply(result=result(common_pb2, "order refunded"), refundId="REF10001")

    class ShippingService(main_grpc.ShippingServiceServicer):
        def ShipOrder(self, request, context):
            return main_pb2.ShipOrderReply(
                result=result(common_pb2, "order shipped"),
                trackingNo="SF10000001",
                status=common_pb2.ORDER_STATUS_SHIPPED,
            )

    class NotificationService(main_grpc.NotificationServiceServicer):
        def SendMessage(self, request, context):
            return main_pb2.SendMessageReply(result=result(common_pb2, "message sent"), messageId="MSG10001")

    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    main_grpc.add_UserServiceServicer_to_server(UserService(), server)
    main_grpc.add_ProductServiceServicer_to_server(ProductService(), server)
    main_grpc.add_OrderServiceServicer_to_server(OrderService(), server)
    main_grpc.add_PaymentServiceServicer_to_server(PaymentService(), server)
    main_grpc.add_ShippingServiceServicer_to_server(ShippingService(), server)
    main_grpc.add_NotificationServiceServicer_to_server(NotificationService(), server)
    service_names = tuple(service.full_name for service in main_pb2.DESCRIPTOR.services_by_name.values())
    reflection.enable_server_reflection(service_names + (reflection.SERVICE_NAME,), server)
    bind = f"{host}:{port}"
    server.add_insecure_port(bind)
    server.start()
    print(f"[msdemo-grpc-mock] listening on {bind}", flush=True)
    server.wait_for_termination()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=50052)
    args = parser.parse_args()
    serve(args.host, args.port)


if __name__ == "__main__":
    main()
